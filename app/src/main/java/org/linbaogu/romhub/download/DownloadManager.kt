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

    /** taskId → 每秒字节数（给界面显示速度用） */
    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val speeds: StateFlow<Map<Long, Long>> = _speeds.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    private var appCtx: Context? = null
    private var downloader: ChunkDownloader? = null
    private var client: OkHttpClient? = null

    @Volatile private var lastTouch = 0L

    /** 同时下载几个任务 */
    val maxConcurrent: Int get() = appCtx?.let { Prefs.downloadConcurrent(it) } ?: 2

    /** 每个任务开几个分片线程 */
    val threads: Int get() = appCtx?.let { Prefs.downloadThreads(it) } ?: 8

    // ---------------------------------------------------------------- 初始化

    fun init(context: Context) {
        if (appCtx != null) return
        val ctx = context.applicationContext
        appCtx = ctx

        val c = Api.client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)      // 分片一次读 64KB，不需要长超时
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // ↓↓↓ 高并发必需的三处放开，缺一都跑不满线程数 ↓↓↓
            // 1) Dispatcher 默认 maxRequests=64 / maxRequestsPerHost=5，
            //    不改的话同一个下载源永远只有 5 个请求在飞，选 256 也没用。
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = ChunkDownloader.MAX_INFLIGHT + 8
                    maxRequestsPerHost = ChunkDownloader.MAX_INFLIGHT
                }
            )
            // 2) 连接池默认只留 5 个空闲连接，高并发下会反复重建 TCP+TLS。
            .connectionPool(okhttp3.ConnectionPool(ChunkDownloader.MAX_INFLIGHT + 8, 5, TimeUnit.MINUTES))
            .build()
        client = c
        downloader = ChunkDownloader(c)

        // 上次没下完的（进程被杀）统一落成「已暂停」，让用户手动继续
        val loaded = DownloadStore.load(ctx)
        var needSave = false
        loaded.forEach {
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

    // ---------------------------------------------------------------- 增删改

    fun add(
        url: String,
        fileName: String? = null,
        headers: Map<String, String> = emptyMap(),
        subDir: String = "",
        threads: Int = 0,
    ): DownloadTask {
        val task = DownloadTask(
            id = System.currentTimeMillis(),
            url = url.trim(),
            fileName = RangeProbe.sanitize(fileName.orEmpty(), ""),
            subDir = subDir,
            headers = headers,
            threads = threads.coerceIn(0, ChunkDownloader.MAX_THREADS),
        )
        _tasks.value = _tasks.value + task
        persistAndSync()
        pump()
        return task
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
        if (wasRunning) resume(id) else persistAndSync()
        // 任务对象是原地改的，StateFlow 不发新值界面就不重绘，
        // 卡片上的「N 线程」会停在旧数字。手动 touch 一次强制刷新。
        touch(force = true)
    }

    /** 暂停：打断所有在飞的请求，已下载的字节都留着。 */
    fun pause(id: Long) {
        val t = find(id) ?: return
        downloader?.cancel(id)
        t.status = DownloadTask.Status.PAUSED
        jobs[id]?.cancel()
        jobs.remove(id)
        persistAndSync()
        pump()
    }

    fun resume(id: Long) {
        val t = find(id) ?: return
        if (t.isDone) return
        downloader?.clearCancel(id)
        t.status = DownloadTask.Status.PENDING
        t.error = ""
        persistAndSync()
        pump()
    }

    fun retry(id: Long) {
        val t = find(id) ?: return
        downloader?.clearCancel(id)
        t.chunks = emptyList()          // 失败重来：重新探测、重新分片
        t.totalBytes = -1L
        t.error = ""
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
        val t = find(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
        if (deleteFile && t != null) {
            deleteTaskFiles(t)
        }
        persistAndSync()
        pump()
    }

    /** 删掉一条任务落盘的所有痕迹：成品文件 + 同名的 .part / .tmp 残片。 */
    fun deleteTaskFiles(t: DownloadTask) {
        runCatching {
            val dir = DownloadStore.downloadDir(requireCtx(), t.subDir)
            val name = t.fileName
            if (name.isNotBlank()) {
                File(dir, name).delete()
                File(dir, "$name.part").delete()
                File(dir, "$name.tmp").delete()
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
                File(dir, "$name.tmp").exists()
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
        val active = _tasks.value.count { it.status == DownloadTask.Status.RUNNING }
        if (active >= maxConcurrent) return
        val next = _tasks.value.firstOrNull { it.status == DownloadTask.Status.PENDING } ?: return
        launchTask(ctx, next)
    }

    private fun launchTask(ctx: Context, task: DownloadTask) {
        if (jobs.containsKey(task.id)) return
        val d = downloader ?: return
        val c = client ?: return

        val job = scope.launch {
            task.status = DownloadTask.Status.RUNNING
            task.error = ""
            touch(force = true)
            try {
                val dest = fileOf(task)

                // 首次（或重试）：探测大小 / 是否支持分片 / 文件名
                if (task.totalBytes <= 0L || task.chunks.isEmpty()) {
                    val info = RangeProbe.probe(c, task.url, task.headers)
                    task.totalBytes = info.total
                    task.resumable = info.resumable
                    if (task.fileName.isBlank()) {
                        task.fileName = RangeProbe.sanitize(info.fileName, "download_${task.id}")
                    }
                    task.chunks = if (info.resumable && info.total > 0L) {
                        d.planChunks(info.total, task.effectiveThreads(threads))
                    } else {
                        emptyList()
                    }
                    touch(force = true)
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
            } catch (e: Throwable) {
                task.status = if (d.isCancelled(task.id)) {
                    DownloadTask.Status.PAUSED
                } else {
                    DownloadTask.Status.FAILED
                }
                task.error = e.message?.take(120) ?: e.javaClass.simpleName
            } finally {
                jobs.remove(task.id)
                d.clearCancel(task.id)
                touch(force = true)
                pump()
            }
        }
        jobs[task.id] = job
    }

    // ---------------------------------------------------------------- 状态刷新

    /** 节流刷新界面（下载中每秒会触发很多次进度回调）。 */
    private fun touch(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastTouch < 120L) return
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
        }
    }
}
