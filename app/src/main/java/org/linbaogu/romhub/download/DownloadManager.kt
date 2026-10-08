package org.linbaogu.romhub.download

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.Api
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 下载总控。
 *
 * 三层并发：
 *   · 任务层：同时最多 [maxConcurrent] 个任务在下载（默认 2）
 *   · 分片层：每个任务内部按 [threads] 个线程分片并发（默认 8，用户可调）
 *   · 请求层：OkHttp 自己的连接池
 *
 * 状态放在 [tasks] 这个 StateFlow 里，UI 直接 collect；落盘交给 [DownloadStore]，
 * 前台保活交给 [DownloadService]。
 */
object DownloadManager {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /**
     * 每个任务「已下字节数」的历史最大值。
     *
     * 为什么要它：分片重试时 [ChunkState.done] 可能要回退（比如 part 文件比记录短），
     * 那总进度就会**倒退** —— 用户看到进度条往回缩，会以为出问题了。
     * 界面上的进度只认这个单调递增的值；真实的分片状态照旧用 `chunk.done`。
     * 任务删除或重试时清掉。
     */
    private val progressFloor = ConcurrentHashMap<Long, Long>()

    /** taskId → 每秒字节数（给界面显示速度用） */
    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val speeds: StateFlow<Map<Long, Long>> = _speeds.asStateFlow()

    /**
     * 用户手动放行、允许走移动网络的任务 id。
     *
     * 「仅 Wi-Fi 下载」是全局开关，但用户对某一条任务点「用移动网络下载」时，
     * 显然只针对这一条 —— 不该顺手把全局开关也关掉（那会影响以后所有任务）。
     * 名单只在内存里，进程重启即失效（重启后重新按全局开关判断，符合预期）。
     */
    private val allowedOnMetered = ConcurrentHashMap.newKeySet<Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    /**
     * 每个任务的「代际号」，每启动一次 +1。
     *
     * 解决的是这个竞态：用户点暂停 → [pause] 取消协程，但协程的 `finally`
     * 还没跑完；用户马上又点继续/改线程 → 新协程起来了。此时旧协程可能
     * 还没从 `isCancelled` 的检查点退出，两次运行同时往同一批 part 文件写，
     * 进度统计会错乱（表现为「暂停后继续，进度乱跳 / 卡在某个数」）。
     *
     * 有了代际号，协程体在每次写数据前对比一下自己是不是「当前的这一代」，
     * 不是就直接退出，绝不和新协程抢。
     */
    private val epochs = ConcurrentHashMap<Long, Long>()

    private var appCtx: Context? = null
    private var downloader: ChunkDownloader? = null
    private var client: OkHttpClient? = null

    @Volatile private var lastTouch = 0L
    private val touchLock = Any()

    /** 界面刷新间隔：约 60fps 的一半，够顺滑又不会把主线程压垮。 */
    private const val TOUCH_INTERVAL_MS = 32L

    /** 日志标签（诊断用：adb logcat -s RomHubDL）。 */
    private const val TAG = "RomHubDL"

    /**
     * 探测失败时的兜底线程数。
     *
     * 为什么不退到 1：单线程下一个 11GB 的包基本等于下不完，等于"功能没了"。
     * 16 是「对绝大多数源站都安全」的保守值 —— 不会像 256 那样被 CDN 风控打死，
     * 又能真正跑起来多线程。探测失败只是没拿到总大小，不代表不能分片。
     */
    private const val FALLBACK_THREADS = 16

    /**
     * 卡死看门狗：一条任务从 RUNNING 起算，这么久还没收到任何数据就判失败。
     *
     * 为什么要它：探测阶段的 callTimeout 只在请求**真正开始执行**后计时。
     * 高线程档下 Dispatcher 线程被分片请求占满时，探测会一直排队、永不计时，
     * 于是任务无限期挂在 RUNNING。这里兜一道底，90 秒无数据即中止。
     */
    private const val WATCHDOG_MS = 90_000L

    /** 同时下载几个任务 */
    val maxConcurrent: Int get() = appCtx?.let { Prefs.downloadConcurrent(it) } ?: 2

    /** 每个任务开几个分片线程 */
    val threads: Int get() = appCtx?.let { Prefs.downloadThreads(it) } ?: 8

    // ---------------------------------------------------------------- 初始化

    fun init(context: Context) {
        if (appCtx != null) return
        val ctx = context.applicationContext
        appCtx = ctx

        // 按用户当前选的档位配置连接层。选 64 线程时若 maxRequestsPerHost 还停在 32，
        // 同一 host 的请求会有一半在 OkHttp 队列里排队 —— 这正是老版本
        // 「64 线程及以上下不动」的直接原因之一。这里放宽到天花板，
        // 真正的并发节流交给 ChunkDownloader 的信号量（按档位缩放）。
        val hardCap = ChunkDownloader.MAX_INFLIGHT_HARD
        val timeout = Prefs.downloadTimeout(ctx).toLong()

        val c = Api.client.newBuilder()
            // 建连超时独立收短：用户设置里那个是「读超时」（可以给到几十秒等慢速源站），
            // 但**建连**超过 8 秒基本就是连不上，没必要耗着 —— 配合下面的
            // retryOnConnectionFailure，一次失败会立刻重试，不会让用户干等。
            .connectTimeout(
                timeout.coerceIn(5, 15).toLong(),
                TimeUnit.SECONDS,
            )
            .readTimeout(90, TimeUnit.SECONDS)      // 高并发下个别连接可能被 CDN 拖慢
            .writeTimeout(30, TimeUnit.SECONDS)
            // ★ 整体调用超时兜底。
            //   只设 connect/read 是不够的：服务器接受了连接、却一直「细水长流」地
            //   挤数据（或干脆挂着不发响应头），readTimeout 会被每个数据包不断重置，
            //   于是这一次请求可能拖几分钟 —— 表现出来就是「点了开始下载，半天没反应」。
            //   callTimeout 是**整个调用**的硬上限，到点必断。
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // ↓↓↓ 高并发必需的三处放开，缺一都跑不满线程数 ↓↓↓
            // 1) Dispatcher 默认 maxRequests=64 / maxRequestsPerHost=5，
            //    不改的话同一个下载源永远只有 5 个请求在飞，选 256 也没用。
            //    ⚠ 光改这两个数还不够：Dispatcher 的 executorService 默认也只有 64 个
            //    线程。分片请求把线程占满后，**探测请求会连「开始执行」都轮不到**
            //    （callTimeout 是 AsyncTimeout 计时的，请求还在排队时根本不计时），
            //    表现出来就是选高线程档时「点了开始下载半天没反应」。
            //    所以这里必须连线程池一起放大。
            .dispatcher(
                okhttp3.Dispatcher(
                    java.util.concurrent.Executors.newFixedThreadPool(hardCap + 16) { r ->
                        Thread(r, "romhub-http").apply { isDaemon = true }
                    }
                ).apply {
                    maxRequests = hardCap + 16
                    maxRequestsPerHost = hardCap
                }
            )
            // 2) 连接池默认只留 5 个空闲连接，高并发下会反复重建 TCP+TLS。
            .connectionPool(okhttp3.ConnectionPool(hardCap + 16, 5, TimeUnit.MINUTES))
            .build()
        client = c
        downloader = ChunkDownloader(c)
        syncDownloadPolicies()

        // 上次没下完的（进程被杀）统一落成「已暂停」，让用户手动继续
        val loaded = DownloadStore.load(ctx)
        var needSave = false
        loaded.forEach {
            // 重启后闸门条件要重新判断，别把上次的拦截原因带过来
            if (it.holdReason.isNotBlank()) {
                it.holdReason = ""
                needSave = true
            }
            if (it.status == DownloadTask.Status.RUNNING || it.status == DownloadTask.Status.PENDING) {
                it.status = DownloadTask.Status.PAUSED
                needSave = true
            }
        }
        _tasks.value = loaded
        if (needSave) DownloadStore.save(ctx, loaded)

        scope.launch { ticker() }
    }

    private fun requireCtx(): Context = appCtx ?: error("DownloadManager 还没 init")

    /**
     * 把用户在设置里改的重试/保留分片策略同步给下载器。
     *
     * 设置页改完调一下这个方法即可生效，不用重启 App。
     */
    fun syncDownloadPolicies() {
        val ctx = appCtx ?: return
        downloader?.setRetryPolicy(
            count = Prefs.downloadRetryCount(ctx),
            baseIntervalSec = Prefs.downloadRetryInterval(ctx),
        )
        downloader?.keepParts = Prefs.downloadKeepParts(ctx)
    }

    // ---------------------------------------------------------------- 增删改

    fun add(
        url: String,
        fileName: String? = null,
        headers: Map<String, String> = emptyMap(),
        subDir: String = "",
        threads: Int = 0,
        isHls: Boolean = false,
        queued: Boolean = false,
    ): DownloadTask {
        val ctx = appCtx
        // 把用户设的自定义 UA / Referer 合进请求头。
        // 已显式传了的以调用方为准 —— 比如网盘取链时带的 Referer 不能被覆盖。
        val mergedHeaders = LinkedHashMap<String, String>()
        if (ctx != null) {
            val ua = Prefs.downloadUserAgent(ctx)
            if (ua.isNotBlank()) mergedHeaders["User-Agent"] = ua
            val ref = Prefs.downloadReferer(ctx)
            if (ref.isNotBlank()) mergedHeaders["Referer"] = ref
        }
        mergedHeaders.putAll(headers)

        val task = DownloadTask(
            id = System.currentTimeMillis(),
            url = url.trim(),
            fileName = RangeProbe.sanitize(fileName.orEmpty(), ""),
            subDir = subDir,
            headers = mergedHeaders,
            threads = threads.coerceIn(0, ChunkDownloader.MAX_THREADS),
            isHls = isHls,
            queued = queued,
            // 排队任务接在已有的队尾；普通任务给 0（不参与排队）
            queueOrder = if (queued) nextQueueOrder() else 0,
        )
        _tasks.value = _tasks.value + task
        persistAndSync()
        // 先同步判定一次闸门（仅 Wi-Fi / 低电量），把原因写到任务上 ——
        // 调用方（界面）拿到返回值时就能知道「这条其实没跑起来」并立刻提示用户。
        // pump() 里也会判，但这里是**同步**的，保证 holdReason 在 return 前就绪。
        precheckGate(task)
        pump()
        return task
    }

    /**
     * 同步判定闸门。跟 [pump] 里的逻辑保持一致，只是不启动协程。
     * 目的：让 [add] 的调用方在返回值上就能看到 holdReason。
     */
    private fun precheckGate(task: DownloadTask) {
        val ctx = appCtx ?: return
        if (task.status != DownloadTask.Status.PENDING || task.queued) return
        if (task.id in allowedOnMetered) return
        if (Prefs.downloadWifiOnly(ctx) && !isOnWifi(ctx)) {
            task.holdReason = "等 Wi-Fi —— 当前是移动网络（可点下方按钮放行）"
            return
        }
        if (Prefs.downloadBatteryLimit(ctx) && isBatteryLow(ctx)) {
            task.holdReason = "等充电 —— 电量低于 15%（可点下方按钮放行）"
        }
    }

    /** 依次给排队任务编号：已有的最大号 +1。 */
    private fun nextQueueOrder(): Int =
        (_tasks.value.filter { it.queued }.maxOfOrNull { it.queueOrder } ?: 0) + 1

    // ---------------------------------------------------------------- 队列

    /** 已在排队、还没开始的任务（按顺序）。 */
    fun queue(): List<DownloadTask> =
        _tasks.value.filter { it.queued && !it.isDone && !it.isFailed }
            .sortedBy { it.queueOrder }

    /**
     * 把队里某个任务往前/往后挪一位。
     *
     * 为什么要显式排序而不是「拖动」：拖动在 LazyColumn 里要接重排动画，
     * 而这一页的列表每 32ms 就刷一次（进度），拖拽手势会被刷新打断。
     * 上移/下移两个按钮简单可靠，够用。
     */
    fun moveInQueue(id: Long, up: Boolean) {
        val q = queue().toMutableList()
        val i = q.indexOfFirst { it.id == id }
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in q.indices) return
        val tmp = q[i]; q[i] = q[j]; q[j] = tmp
        // 重新编号（从 1 开始），保证顺序稳定
        q.forEachIndexed { idx, t -> t.queueOrder = idx + 1 }
        persistAndSync()
        touch(force = true)
        pump()
    }

    /** 把某个排队任务直接提到队首。 */
    fun moveQueueToTop(id: Long) {
        val q = queue().toMutableList()
        val t = q.firstOrNull { it.id == id } ?: return
        q.remove(t)
        q.add(0, t)
        q.forEachIndexed { idx, x -> x.queueOrder = idx + 1 }
        persistAndSync()
        touch(force = true)
        pump()
    }

    /** 解除排队，立刻开始下载。 */
    fun startQueuedNow(id: Long) {
        val t = find(id) ?: return
        t.queued = false
        t.queueOrder = 0
        t.status = DownloadTask.Status.PENDING
        persistAndSync()
        pump()
    }

    /** 开始跑整条队列（把队里的任务都放出来，按并发上限依次下）。 */
    fun startQueue() {
        queue().forEach { it.queued = false; it.queueOrder = 0 }
        persistAndSync()
        touch(force = true)
        pump()
    }

    /** 清空队列（只解除排队标记，不删任务）。 */
    fun clearQueue() {
        _tasks.value.filter { it.queued }.forEach { it.queued = false; it.queueOrder = 0 }
        persistAndSync()
        touch(force = true)
    }

    /**
     * 改某个任务的线程数。传 0 = 跟随全局设置。
     *
     * 正在下的话要重新分片才生效（[DownloadTask.chunks] 是按旧线程数切的），
     * 所以这里顺手清掉分片让它重新探测 —— 已下载的字节会保留，
     * 但由于分片边界变了，断点位置会重算（对同一个文件总字节数不变）。
     */
    fun setThreads(id: Long, n: Int) {
        val t = find(id) ?: return
        val v = n.coerceIn(0, ChunkDownloader.MAX_THREADS)
        if (t.threads == v) return
        val wasRunning = t.status == DownloadTask.Status.RUNNING
        if (wasRunning) pause(id)
        t.threads = v
        t.chunks = emptyList()
        t.totalBytes = -1L
        t.activeThreads = 0     // 重新探测时会按新线程数再写一次
        t.resetProgressFloor()
        if (wasRunning) resume(id) else persistAndSync()
        // 任务对象是原地改的，StateFlow 不发新值界面就不重绘，
        // 卡片上的「N 线程」会停在旧数字。手动 touch 一次强制刷新。
        touch(force = true)
    }

    /**
     * 暂停：打断所有在飞的请求，已下载的字节都留着。
     *
     * ⚠ 关键点：这里**递增代际号 + 取消协程**，但**不清下载器的取消标记**。
     *   旧协程可能还卡在某个 chunk 的读写里没退出，若立刻清标记，
     *   它会"复活"继续写，和新协程抢同一批 part 文件。
     *
     *   取消标记由新协程自己处理：[launchTask] 在确认旧 job 已结束后
     *   调用 `clearCancel`，那时代际号已经换成新的，旧协程绝不会再动。
     */
    fun pause(id: Long) {
        val t = find(id) ?: return
        downloader?.cancel(id)
        t.status = DownloadTask.Status.PAUSED
        epochs[id] = (epochs[id] ?: 0L) + 1L   // 旧代作废
        jobs[id]?.cancel()
        // ⚠ 不在这里 jobs.remove —— 让旧协程的 finally 自己移除，
        //   这样「名额占用数」(jobs.size) 在旧协程真正退出前不会被重复计算，
        //   pump 也不会在旧协程还活着时就启动新一个同 id 的协程。
        persistAndSync()
        pump()
    }

    fun resume(id: Long) {
        val t = find(id) ?: return
        if (t.isDone) return
        // 旧协程若还在飞，等它退出再放新代 —— 但不能阻塞 UI 线程，
        // 所以放到 IO 线程里做，完成后回到主线程继续。
        t.status = DownloadTask.Status.PENDING
        t.error = ""
        t.holdReason = ""
        persistAndSync()
        scope.launch {
            val old = jobs[id]
            if (old != null && !old.isCompleted) {
                // 最多等 1.5 秒；超时也放行（宁可有一点竞态，也不能让"继续"没反应）
                withContext(Dispatchers.IO) {
                    runCatching { kotlinx.coroutines.withTimeout(1500) { old.join() } }
                }
            }
            downloader?.clearCancel(id)
            if (find(id)?.status == DownloadTask.Status.PENDING) pump()
        }
    }

    /**
     * 「就用移动网络下这一条」—— 用户对被闸门挡下的任务点这个按钮时调。
     *
     * 语义上跟 [resume] 一样（都是「放它出去跑」），但这里只清当前任务。
     * 闸门本身（仅 Wi-Fi / 低电量）会在 [pump] 里**再次**拦截，所以这里
     * 必须把这条任务加入「临时放行」名单，让 pump 跳过它。
     */
    fun forceStart(id: Long) {
        val t = find(id) ?: return
        if (t.isDone) return
        // 加入放行名单：pump 的「仅 Wi-Fi / 低电量」闸门会跳过这条
        allowedOnMetered.add(id)
        // 剩下的交给 resume —— 它也负责「等旧代退出再放新代」和刷新界面
        resume(id)
        // resume 里清了 error/holdReason 但没 touch，这里补一次让卡片立刻变样
        touch(force = true)
    }

    fun retry(id: Long) {
        val t = find(id) ?: return
        downloader?.cancel(id)
        jobs[id]?.cancel()
        epochs[id] = (epochs[id] ?: 0L) + 1L   // 旧代作废
        downloader?.clearCancel(id)
        t.chunks = emptyList()          // 失败重来：重新探测、重新分片
        t.totalBytes = -1L
        t.activeThreads = 0             // 重新探测时再定
        t.resetProgressFloor()          // 进度重新计数（否则新探测出来的进度会被旧最大值压住）
        t.error = ""
        t.holdReason = ""
        t.status = DownloadTask.Status.PENDING
        persistAndSync()
        pump()
    }

    /**
     * 取消/删除一条任务。
     *
     * [deleteFile] = true 时连磁盘上的文件一起清掉（含没下完的分片残文件）；
     * = false 时只移除任务记录，文件留在原地（用户可能想自己去文件管理器拿）。
     *
     * 默认 `true`：未完成的任务本来就没有保留价值，残文件占空间。
     */
    fun remove(id: Long, deleteFile: Boolean = true) {
        downloader?.cancel(id)
        jobs[id]?.cancel()
        jobs.remove(id)
        allowedOnMetered.remove(id)
        progressFloor.remove(id)
        val t = find(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
        if (deleteFile && t != null) {
            deleteTaskFiles(t)
        }
        persistAndSync()
        pump()
    }

    /** 删掉一条任务落盘的所有痕迹：成品文件 + 同名的 .part / .tmp 残片 + 分片目录。 */
    fun deleteTaskFiles(t: DownloadTask) {
        runCatching {
            val dir = DownloadStore.downloadDir(requireCtx(), t.subDir)
            val name = t.fileName
            if (name.isNotBlank()) {
                File(dir, name).delete()
                File(dir, "$name.part").delete()
                File(dir, "$name.tmp").delete()
                // 新版分片下载的 part 目录（.文件名.parts/）
                File(dir, ".$name.parts").takeIf { it.isDirectory }?.deleteRecursively()
            }
            // HLS 之类的分片目录（同名文件夹）
            if (name.isNotBlank()) {
                File(dir, name).takeIf { it.isDirectory }?.deleteRecursively()
            }
        }
    }

    /** 该任务在磁盘上是否已经有东西（给「取消时要不要删文件」的提示用）。 */
    fun hasLocalData(t: DownloadTask): Boolean = runCatching {
        val dir = DownloadStore.downloadDir(requireCtx(), t.subDir)
        val name = t.fileName
        name.isNotBlank() && (
            File(dir, name).exists() ||
                File(dir, "$name.part").exists() ||
                File(dir, "$name.tmp").exists() ||
                File(dir, ".$name.parts").exists()
            )
    }.getOrDefault(false)

    fun pauseAll() {
        _tasks.value.filter { it.isActive }.forEach { pause(it.id) }
    }

    fun resumeAll() {
        _tasks.value.filter { it.isPaused }.forEach { resume(it.id) }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filterNot { it.isDone }
        persistAndSync()
    }

    fun find(id: Long): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    /** 已完成文件所在的完整路径（给「打开/分享」用）。 */
    fun fileOf(task: DownloadTask): File =
        File(DownloadStore.downloadDir(requireCtx(), task.subDir), task.fileName)

    // ---------------------------------------------------------------- 调度

    private fun pump() {
        val ctx = appCtx ?: return
        // 并发名额只算「真的在传数据」的任务。
        //
        // ⚠ 这里不能只看 status == RUNNING：RUNNING 是协程一启动就设的，
        //   而协程在真正开始收数据前还要先做探测（probe）。如果探测卡住
        //   （源站不回响应头 / Dispatcher 线程被占满），这条任务会一直挂着
        //   RUNNING 却一个字节都不动 —— 名额被它占死，后面所有任务永远 PENDING，
        //   表现出来就是「加了新任务，一直排队中，半天没反应」。
        //   所以只把「正在跑」的算作占用名额。
        val active = jobs.size
        if (active >= maxConcurrent) return
        // 挑下一个要跑的任务：
        //   ① 有排队任务时严格按队列顺序（queueOrder 最小者先）
        //   ② 没有就按加入先后
        // 还带着 queued 标记的任务是「等用户点开始」的，pump 不碰。
        val next = _tasks.value
            .filter { it.status == DownloadTask.Status.PENDING && !it.queued }
            .minByOrNull { it.queueOrder.takeIf { q -> q > 0 } ?: Int.MAX_VALUE }
            ?: return

        // ---- 启动前的两道「暂缓」闸门 ----
        //
        // ⚠ 关键：这里**不能直接 return 走人**。闸门挡下任务时必须把原因写到
        //   任务身上（holdReason），否则用户看到的就是「状态是待下载、可它一直不动、
        //   也不报错」—— 这正是「点了开始下载，半天没反应」最让人抓狂的形态。
        //
        //   而且闸门是**可恢复的**：等网络/电量条件满足时，下一次 pump
        //   （由网络回调或 ticker 触发）会把 holdReason 清掉再放它出去。
        //
        // ①「仅 Wi-Fi 下载」：蜂窝网时先不启动。
        //   任务保持 PENDING，等网络切回 Wi-Fi 时由 NetworkCallback 拉起。
        //   例外：用户对这条任务手动点过「用移动网络下载」（allowedOnMetered）。
        val exempt = next.id in allowedOnMetered
        if (Prefs.downloadWifiOnly(ctx) && !isOnWifi(ctx) && !exempt) {
            markHold(next, "等 Wi-Fi —— 当前是移动网络（可点下方按钮放行）")
            return
        }
        // ②「低电量暂停」：电量低于 15% 且没在充电时不自动开始
        if (Prefs.downloadBatteryLimit(ctx) && isBatteryLow(ctx) && !exempt) {
            markHold(next, "等充电 —— 电量低于 15%（可点下方按钮放行）")
            return
        }
        // 闸门全过 → 清掉拦截标记，正常开跑
        if (next.holdReason.isNotBlank()) {
            next.holdReason = ""
            touch(force = true)
        }
        launchTask(ctx, next)
    }

    /**
     * 把「这条任务为什么停着没动」写到任务上，并刷一次界面。
     *
     * 只在原因变化时才刷（避免每 32ms 的 ticker 反复触发重组）。
     */
    private fun markHold(task: DownloadTask, reason: String) {
        if (task.holdReason == reason) return
        task.holdReason = reason
        touch(force = true)
    }

    /** 电量是否偏低（<15%）且未在充电。 */
    private fun isBatteryLow(ctx: Context): Boolean = runCatching {
        val bm = ctx.getSystemService(android.os.BatteryManager::class.java) ?: return false
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level <= 0) return false
        val charging = bm.isCharging
        level < 15 && !charging
    }.getOrDefault(false)

    /** 当前是不是连在 Wi-Fi（或有线）上。 */
    private fun isOnWifi(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(false)

    /**
     * 网络状态变化时调一下：Wi-Fi 回来了就把「仅 Wi-Fi」拦下的任务放出去。
     * 由 [DownloadService] 注册的网络回调触发。
     */
    fun onNetworkChanged() {
        pump()
    }

    private fun launchTask(ctx: Context, task: DownloadTask) {
        val d = downloader ?: return
        val c = client ?: return
        // 已经在跑的任务不重复启动。判断依据是 job 是否**仍在运行**，
        // 而不是「jobs 里有没有这个 key」—— 暂停后旧 job 可能还没完全退出，
        // 但代际已经作废，这时新代应该正常启动。
        val prev = jobs[task.id]
        if (prev != null && !prev.isCompleted) return

        // 记下这一代；协程体会用它判断自己是否已被「暂停/重试」作废。
        val myEpoch = (epochs[task.id] ?: 0L)
        val stillCurrent = { (epochs[task.id] ?: 0L) == myEpoch }

        val job = scope.launch {
            task.status = DownloadTask.Status.RUNNING
            task.error = ""
            // 耗时统计：记第一次开始的时间；重试/继续时不重置累计值
            if (task.startedAt == 0L) task.startedAt = System.currentTimeMillis()
            val runStart = SystemClock.elapsedRealtime()
            touch(force = true)
            // ---- 卡死看门狗 ----
            // 探测阶段理论上 20 秒会超时，但 OkHttp 的 callTimeout 只在请求真正
            // 开始执行后才计时：如果执行线程被占满，请求会一直排队、永远不计时。
            // 这里再加一道兜底 —— 90 秒内一个字节都没动过就直接判失败，
            // 把名额让给后面的任务，而不是让用户对着「排队中」干等。
            val watchdog = scope.launch {
                delay(WATCHDOG_MS)
                // 这一代已被暂停/重试作废 → 看门狗不再插手（新代有自己的看门狗）
                if (!stillCurrent()) return@launch
                if (task.doneBytes == 0L && task.status == DownloadTask.Status.RUNNING) {
                    downloader?.cancel(task.id)
                    jobs[task.id]?.cancel()
                    task.status = DownloadTask.Status.FAILED
                    task.error = task.error.ifBlank { "连接后 90 秒没有任何数据，已中止" }
                    touch(force = true)
                    pump()
                }
            }
            try {
                val dest = fileOf(task)
                android.util.Log.i(
                    TAG,
                    "task ${task.id} 启动：hls=${task.isHls} total=${task.totalBytes} " +
                            "chunks=${task.chunks.size} threads=${task.effectiveThreads(threads)} url=${task.url.take(90)}",
                )

                // ---- HLS（m3u8）走专用路径 ----
                // 为什么要单独一条：普通分片靠 `Range: bytes=a-b` 自己切，但 HLS 的「片」
                // 是播放列表里已切好的 ts/fmp4 段，不能重新切。并发粒度由
                // 「M3U8 分片线程数」控制。
                if (task.isHls) {
                    if (task.fileName.isBlank()) {
                        task.fileName = RangeProbe.sanitize("", "hls_${task.id}.ts")
                    }
                    // HLS 的总大小播放列表里给不出确切值，抓完才知道 —— 先按已下字节显示。
                    val okHls = HlsDownloader.download(
                        url = task.url,
                        headers = task.headers,
                        destFile = dest,
                        threads = Prefs.downloadM3u8Threads(ctx),
                        onBytes = { delta ->
                            // 把字节增量记到一个「单片」上，进度条即按它推进
                            val chunk = task.chunks.firstOrNull() ?: ChunkState(0, -1).also {
                                task.chunks = listOf(it)
                            }
                            chunk.done += delta
                            touch()
                        },
                    )
                    if (okHls) {
                        val real = dest.length()
                        task.totalBytes = real
                        task.chunks = listOf(ChunkState(0, real - 1).also { it.done = real })
                    }
                    task.status = if (okHls) DownloadTask.Status.DONE else DownloadTask.Status.FAILED
                    if (!okHls) task.error = "HLS 下载失败"
                    if (task.isDone) {
                        runVerification(ctx, task, dest)
                        CompletionNotifier.onTaskDone(ctx, task, dest)
                    }
                    return@launch
                }

                // 首次（或重试）：探测大小 / 是否支持分片 / 文件名
                if (task.totalBytes <= 0L || task.chunks.isEmpty()) {
                    android.util.Log.i(TAG, "task ${task.id} 开始探测…")
                    // 探测失败**不判死、也不退单线程**：
                    //   单线程下一个 11GB 的包等于下不完，那跟"下不了"没区别。
                    //   探测失败只是"只取首字节"这条路不通（有些源站对 Range 探测
                    //   不友好），不代表不能多线程 —— 所以标成 resumable，
                    //   下面会用一次普通 GET 补测大小，然后按 16 线程正常分片。
                    //   真连普通 GET 都不支持分片时，downloadChunked 会抛
                    //   RangeIgnoredException，那时才退单线程。
                    val info = try {
                        RangeProbe.probe(c, task.url, task.headers)
                    } catch (e: Throwable) {
                        android.util.Log.w(
                            TAG,
                            "task ${task.id} 探测失败，改按 16 线程方案：${e.message}",
                        )
                        RangeProbe.Info(total = -1L, resumable = true, fileName = "", mime = null)
                    }
                    android.util.Log.i(
                        TAG,
                        "task ${task.id} 探测完成：total=${info.total} resumable=${info.resumable} " +
                                "name=${info.fileName}",
                    )
                    task.totalBytes = info.total
                    task.resumable = info.resumable
                    if (task.fileName.isBlank()) {
                        task.fileName = RangeProbe.sanitize(info.fileName, "download_${task.id}")
                    }
                    task.chunks = if (info.resumable && info.total > 0L) {
                        // 分片数优先级：任务单独设的 > 该域名实测记住的 > 全局默认。
                        // 「按域名记住」—— 同一 CDN
                        // 上次开多少线程好用，这次自动套用，不用用户反复调。
                        val perDomain = if (Prefs.downloadRememberDomainParts(ctx)) {
                            DomainPartLimit.partLimitFor(ctx, task.url)
                        } else {
                            0
                        }
                        val effective = when {
                            task.threads in 1..ChunkDownloader.MAX_THREADS -> task.threads
                            perDomain > 0 -> perDomain
                            else -> task.effectiveThreads(threads)
                        }
                        android.util.Log.i(TAG, "task ${task.id} 规划分片… effective=$effective")
                        // ⚠ 把这次用的线程数记到任务上 —— 执行阶段（downloadChunked）
                        //   读的就是它。不记的话执行阶段会拿一个二级回退的默认值
                        //   （曾经是硬编码 8），导致「256 片只开 8 并发」，
                        //   下载能完成但慢得像卡住。
                        task.activeThreads = effective
                        val planned = d.planChunks(info.total, effective)
                        android.util.Log.i(TAG, "task ${task.id} 分片规划完成：${planned.size} 片")
                        planned
                    } else if (info.resumable) {
                        // 探测失败但按「支持分片」处理：再用一次普通 GET 把大小问出来，
                        // 拿到就按兜底线程数正常分片。
                        //
                        // 为什么不退回单线程：单线程下一个 11GB 的包等于下不完。
                        // 探测失败只是"拿首字节"这条路不通，不代表不能多线程。
                        val effective = task.activeThreads.takeIf {
                            it in 1..ChunkDownloader.MAX_THREADS
                        } ?: FALLBACK_THREADS
                        task.activeThreads = effective
                        val size = d.probeSizeByHead(c, task)
                        android.util.Log.i(
                            TAG,
                            "task ${task.id} 补测大小 = $size（$effective 线程）",
                        )
                        if (size > 0L) {
                            task.totalBytes = size
                            d.planChunks(size, effective)
                        } else {
                            // 连普通 GET 都拿不到大小 → 只能单线程顺着下
                            task.resumable = false
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    touch(force = true)
                    android.util.Log.i(TAG, "task ${task.id} 分片已写回，准备开始传数据")
                }

                val ok: Boolean = if (task.resumable && task.chunks.isNotEmpty()) {
                    try {
                        d.downloadChunked(task, dest, { touch() })
                    } catch (e: ChunkDownloader.RangeIgnoredException) {
                        // 服务器嘴上支持、实际忽略 Range → 退回单线程
                        task.resumable = false
                        task.chunks = emptyList()
                        d.downloadFull(task, dest, { touch() })
                    }
                } else {
                    d.downloadFull(task, dest, { touch() })
                }

                task.status = if (ok && task.doneBytes >= task.totalBytes && task.totalBytes > 0L) {
                    DownloadTask.Status.DONE
                } else if (ok) {
                    DownloadTask.Status.DONE
                } else {
                    DownloadTask.Status.PAUSED
                }

                // 下完了做一次完整性校验（大小 / zip 结构 / APK 签名）。
                // 这是回应「下载后签名不同」的关键一步 —— 与其让用户装到一半
                // 才看到失败，不如现在就把结论摊给他看。
                if (task.isDone) {
                    runVerification(ctx, task, dest)
                    // 记下这个域名实测好用的分片数，下次同域名自动套用
                    if (task.verifyOk != false) {
                        DomainPartLimit.remember(
                            ctx, task.url, task.effectiveThreads(threads),
                        )
                    }
                    // 完成时的用户反馈：震动 / 提示音 / 自动打开
                    
                    CompletionNotifier.onTaskDone(ctx, task, dest)
                }
            } catch (e: Throwable) {
                // 已被「暂停/重试」作废的旧代：不要动它的状态 ——
                // 此刻 status 很可能已经被用户的新操作改成了 PAUSED/PENDING，
                // 这里再覆盖成 FAILED 会让用户看到「我明明点了暂停，它却报失败」。
                if (!stillCurrent()) return@launch
                task.status = if (d.isCancelled(task.id)) {
                    DownloadTask.Status.PAUSED
                } else {
                    DownloadTask.Status.FAILED
                }
                // 看门狗已经写过更有信息量的原因时不要覆盖它
                if (task.error.isBlank()) {
                    task.error = e.message?.take(120) ?: e.javaClass.simpleName
                }
                android.util.Log.w(TAG, "task ${task.id} 出错：${task.status} / ${task.error}", e)
            } finally {
                // 任务正常结束（完成/暂停/失败），看门狗没必要再盯着了
                watchdog.cancel()
                if (stillCurrent()) {
                    // 累计这一段跑了多久（暂停/失败/完成都记）
                    task.elapsedMs += SystemClock.elapsedRealtime() - runStart
                    // ⚠ 只有当前代才有权移除 job 记录 ——
                    //   旧代若也来 remove，会把新代刚放进去的 job 抹掉，
                    //   导致并发名额算错（jobs.size 偏小 → 超额启动任务）。
                    jobs.remove(task.id)
                    d.clearCancel(task.id)
                    touch(force = true)
                    pump()
                }
            }
        }
        jobs[task.id] = job
    }

    // ---------------------------------------------------------------- 状态刷新

    /**
     * 下载完成后的完整性校验。
     *
     * 按文件真实类型分层做（详见 [FileVerifier]）：
     *   · 所有文件：大小 vs 服务端声明
     *   · zip 系（含 APK / ROM 包）：逐条 CRC32 —— 能真正查出分片写坏的字节错位
     *   · APK：签名证书 SHA-256 + 与本机已装版本对比
     *
     * 校验跑在 IO 线程（大文件读一遍要几秒），结果写回 [DownloadTask.verifyNote]。
     * 用户在设置里可以关掉深度校验（[Prefs.downloadVerifyOnFinish]）。
     */
    private suspend fun runVerification(ctx: Context, task: DownloadTask, dest: File) {
        if (!Prefs.downloadVerifyOnFinish(ctx)) return
        if (!dest.exists()) return
        val r = withContext(Dispatchers.IO) {
            runCatching {
                FileVerifier.verify(
                    ctx = ctx,
                    file = dest,
                    expectedSize = task.totalBytes,
                    expectedSha256 = task.sha256,
                    deepZip = Prefs.downloadDeepVerify(ctx),
                )
            }.getOrNull()
        } ?: return

        // 校验通过的判断：大小/结构/签名各项检查都过
        task.verifyOk = r.allPassed
        task.verifyNote = r.summary()
        touch(force = true)
    }

    /**
     * 节流刷新界面。
     *
     * ## 为什么改成"只节流、不丢帧"（这是「进度接不上」的真凶）
     *
     * 老实现是：120ms 内的刷新**直接丢弃**（`return`）。多任务同时下的时候
     * 每个任务都在疯狂调 `touch()`，但 `lastTouch` 是**全局共享**的一个变量 ——
     * A 任务的回调刷新完，B 任务紧接着的回调就被丢掉；等 B 轮到机会，A 又把它顶掉。
     * 结果就是用户看到的「进度卡在一个数不动，过一会儿猛跳一下」。
     *
     * 新实现：只限制**发通知的频率**，不丢弃状态。
     * 每次 `touch()` 都把 `rev++` 写进列表（这是实际状态），
     * 只是交给 Compose 的 `MutableStateFlow` 去合并 —— 它自己会做 conflation，
     * 连续的相同值不会触发多次重组。这样进度永远是"接得上"的。
     */
    private fun touch(force: Boolean = false) {
        // 刷新前先推进每个任务的单调进度（进度条只增不减）
        _tasks.value.forEach { it.bumpProgressFloor() }

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastTouch < TOUCH_INTERVAL_MS) return
        lastTouch = now
        _tasks.value = _tasks.value.onEach { it.rev++ }.toList()
    }

    /** 落盘 + 前台服务同步（低频，别放在进度回调里）。 */
    private fun persistAndSync() {
        val ctx = appCtx ?: return
        val list = _tasks.value
        DownloadStore.save(ctx, list)
        if (list.any { it.isActive }) DownloadService.start(ctx) else DownloadService.stop(ctx)
    }

    private suspend fun ticker() {
        var lastBytes = emptyMap<Long, Long>()
        while (true) {
            delay(1000)
            val list = _tasks.value
            val nowBytes = list.associate { it.id to it.doneBytes }
            val sp = HashMap<Long, Long>(list.size)
            for ((id, bytes) in nowBytes) {
                val prev = lastBytes[id] ?: bytes
                sp[id] = (bytes - prev).coerceAtLeast(0L)
            }
            lastBytes = nowBytes
            _speeds.value = sp

            if (list.any { it.isActive }) {
                touch(force = true)
                appCtx?.let { DownloadStore.save(it, list) }
                DownloadService.refresh(appCtx!!, list)
            }

            // 有任务被闸门挡着（「仅 Wi-Fi」/ 低电量）时，每秒重新评估一次 ——
            // 网络切回 Wi-Fi、或插上充电器之后，不用等用户手动点也能自己开始。
            // 光靠 NetworkCallback 不够：它只在网络**变化**时触发，
            // 「一直有 Wi-Fi 只是刚解除拦截」这类情况不会被通知到。
            if (list.any { it.status == DownloadTask.Status.PENDING && !it.queued }) {
                pump()
            }
        }
    }
}
