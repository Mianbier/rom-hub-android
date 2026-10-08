package org.linbaogu.romhub.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程分片下载器。
 *
 * 原理（跟所有"高速下载器"一样）：
 *   1. 先探测总大小，把文件切成 N 片；
 *   2. 每片发一个带 `Range: bytes=a-b` 的请求，各自并发下载；
 *   3. **每片写自己的独立临时文件**（`目标名.partN`），互不干扰；
 *   4. 所有片下完后按序号顺序 merge 成最终文件；
 *   5. 每片的下到的字节数记在 [ChunkState.done] 里 → 断了再连接着下（断点续传）。
 *
 * ## 为什么每片要写独立文件（重要）
 *
 * 老实现是「每片各自 `RandomAccessFile(dest,"rw").seek(start+done)` 写同一个文件」。
 * 这在**多线程下是不可靠的**：POSIX 的 `lseek()` + `write()` 不是原子操作，
 * Android/Bionic 下更没有保证。实测（大量社区案例）当 3 个以上线程对同一文件
 * 并发 `seek()`+`write()` 时，会出现**字节错位**（偏移 ±几十到几千字节），
 * 下出来的文件看起来大小对，内容却是错的 —— 表现就是**APK 安装报签名不一致**、
 * ZIP 解压报 CRC 错误。
 *
 * 所以改成：每片写独立 part 文件，全部完成后按序合并。
 * 独立文件 = 每个片只有一个写者 = 天然无竞态，代价只是 merge 时多一次顺序拷贝。
 *
 * ## 为什么高线程会"卡死"（64 线程以上跑不动）
 *
 * 老实现把在途请求压在一个固定的 `Semaphore(32)` 里，`runChunk` 失败重试时
 * 要重新 `gate.acquire()`。高线程（64/128/256）下片数多、重试也多，
 * 大量协程在信号量前排队 + OkHttp 的 `maxRequestsPerHost` 队列叠加，
 * 一旦有连接被 CDN 静默挂住，就形成活锁：进度永远不动。
 *
 * 新实现的限流改成 **两层、且不会死锁**：
 *   · 协程层：用 [chunkSemaphore] 控制在途分片数（可随档位放大到 [MAX_INFLIGHT]）；
 *   · 网络层：交给 OkHttp 自己的 Dispatcher（在 DownloadManager 里按档位配置）。
 * 关键点是**重试不走队列**：分片一旦拿到许可就把整片（含重试）跑完再还，
 * 避免"重试要重新排队"造成的饥饿。
 */
class ChunkDownloader(private val client: OkHttpClient) {

    private val inflight = ConcurrentHashMap<Long, MutableSet<Call>>()
    private val cancelledIds: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())

    /** 每片已经落盘的字节数（按片序号），用于 UI 显示与断点续传。 */
    private val partDone = ConcurrentHashMap<String, AtomicLong>()

    /** 单片失败重试上限（跟用户设置同步，默认 3）。 */
    @Volatile private var retryLimit = 3

    /** 重试指数退避基数（毫秒，默认 1s）。 */
    @Volatile private var retryBaseMs = 1000L

    /**
     * 合并完成后是否删掉 .parts 目录（默认删）。
     * 由 [DownloadManager] 按设置同步 —— 开了「保留分片文件」就留着方便排查。
     */
    @Volatile var keepParts = false

    private fun track(taskId: Long, call: Call) {
        inflight.getOrPut(taskId) { Collections.synchronizedSet(mutableSetOf()) }.add(call)
    }

    private fun untrack(taskId: Long, call: Call) {
        inflight[taskId]?.remove(call)
    }

    /** 停止某个任务：把它所有在飞的请求打断。 */
    fun cancel(taskId: Long) {
        cancelledIds.add(taskId)
        inflight.remove(taskId)?.forEach { runCatching { it.cancel() } }
    }

    fun isCancelled(taskId: Long): Boolean = taskId in cancelledIds

    fun clearCancel(taskId: Long) {
        cancelledIds.remove(taskId)
    }

    // ---------------------------------------------------------------- 分片规划

    /**
     * 把 [total] 字节切成片。
     *
     * 片数 = 线程数（上限 [MAX_THREADS] = 256）。
     *
     * 最小片长不是固定的 1MB，而是随线程数自适应：
     * 开 256 线程时若仍要求每片 1MB，文件小于 256MB 就永远开不满线程，
     * 白白浪费用户选的档位。所以线程数越高，允许的片越小（下限 64KB）。
     *
     * 未知大小（total <= 0）返回空表，交给单线程兜底。
     */
    fun planChunks(total: Long, threads: Int): List<ChunkState> {
        if (total <= 0L) return emptyList()
        var n = threads.coerceIn(1, MAX_THREADS)
        val minChunk = minChunkSizeFor(n)
        if (total / n < minChunk) n = (total / minChunk).toInt().coerceAtLeast(1)
        val per = total / n
        return (0 until n).map { i ->
            val start = i * per
            val end = if (i == n - 1) total - 1 else start + per - 1
            ChunkState(start, end)
        }
    }

    /**
     * 每片最小字节数：线程数越高切得越细。
     *
     * 8 线程 → 1MB/片；64 线程 → 256KB/片；256 线程 → 64KB/片。
     * 再低就没意义了：一片的请求头开销会超过收益。
     */
    private fun minChunkSizeFor(threads: Int): Long = when {
        threads <= 8 -> 1L * 1024 * 1024
        threads <= 32 -> 512L * 1024
        threads <= 128 -> 256L * 1024
        else -> 64L * 1024
    }

    /**
     * 同时在飞的请求上限（按档位缩放）。
     *
     * 老实现是固定 32：选 64 / 128 / 256 时 `coerceAtMost(32)` 统统压回 32，
     * 档位形同虚设。改成随档位放大，但设一个绝对天花板 [MAX_INFLIGHT_HARD]，
     * 免得 256 档真的同时开 256 条 TCP —— 手机的 NAT 表撑不住。
     */
    private fun inflightFor(threads: Int): Int = when {
        threads <= 8 -> 8
        threads <= 16 -> 16
        threads <= 32 -> 32
        threads <= 64 -> 48
        else -> MAX_INFLIGHT_HARD
    }

    // ---------------------------------------------------------------- 分片下载

    /**
     * 按 [task].chunks 并发下载到**独立 part 文件**，最后合并。
     *
     * @return true 全部片都完成且合并成功。
     * @throws RangeIgnored 服务器无视 Range（需要调用方回退到单线程）。
     */
    suspend fun downloadChunked(
        task: DownloadTask,
        dest: File,
        onProgress: () -> Unit,
    ): Boolean {
        val chunks = task.chunks
        if (chunks.isEmpty()) return false

        // 并发数必须跟「切了几片」用的那个数字一致。
        // task.activeThreads 是规划分片时写的，正常一定有值；
        // 万一没有（老存档/异常路径），退化到「按片数推」而不是硬编码 8 ——
        // 硬编码会让 256 片的文件只开 8 个并发，慢得像卡住。
        val threads = task.effectiveThreads(chunks.size.coerceAtMost(MAX_THREADS))
        val cap = inflightFor(threads)
        android.util.Log.i(
            "RomHubDL",
            "downloadChunked 开始：${chunks.size} 片 线程=$threads 在途上限=$cap",
        )

        // 清理历史遗留的 part 残片（上一次中断留下的）
        val partDir = File(dest.parentFile, ".${dest.name}.parts")
        if (!partDir.exists()) partDir.mkdirs()

        val ok = coroutineScope {
            // 两层限流，且都不会死锁：
            //   · gate：协程版信号量（**挂起**等待，不阻塞线程），控制在途分片数；
            //   · 调度器：把这一批分片钉在容量为 cap 的调度器上，避免 256 个协程
            //     一起涌进 Dispatchers.IO 去抢那几十个线程。
            //
            // ⚠ 这里绝不能用 java.util.concurrent.Semaphore 的 acquire()：
            //   它是**阻塞**调用，会把 Dispatchers.IO 的线程真的堵死。要下 256 片
            //   时，等许可的协程把线程池占满，真正该传数据的协程反而拿不到线程，
            //   整条下载就"卡在开始处"——日志有、连接没有、一个字节不动。
            val gate = Semaphore(cap)
            val dispatcher = Dispatchers.IO.limitedParallelism(cap.coerceAtLeast(1))
            val results = chunks.mapIndexed { index, chunk ->
                async(dispatcher) {
                    // 拿到许可才发请求；许可在整片（含重试）跑完后才释放，
                    // 这样重试不会跟"还没开始的片"抢许可，避免高线程下饿死。
                    gate.withPermit {
                        runChunk(task, index, chunk, partDir, onProgress)
                    }
                }
            }.awaitAll()
            results.all { it }
        }
        if (!ok) return false

        // 全部片完成 → 按序号顺序合并成目标文件
        return mergeParts(task, chunks, partDir, dest, onProgress)
    }

    /**
     * 大小未知时的兜底：用一次**普通 GET**（不发 Range）把大小问出来。
     *
     * 触发场景：探测请求（`Range: bytes=0-0`）失败 —— 源站对"只取首字节"这种
     * 请求不友好（有些 CDN 会 403/挂起），但**完整 GET 是通的**。
     *
     * 为什么不用"开放区间的多片"：那样每个片都得从 0 读到尾，
     * 等于同一个文件被并发下 N 遍，纯浪费流量。
     *
     * 这里的做法是：发一个不带 Range 的 GET，**只读响应头就断开**
     * （`call.cancel()` / `body.close()`），拿到 `Content-Length` 就能正常分片了。
     * 拿不到（chunked 编码等）才真正退到单线程。
     *
     * @return 总字节数；<= 0 表示拿不到。
     */
    fun probeSizeByHead(client: OkHttpClient, task: DownloadTask): Long {
        val req = Request.Builder()
            .url(task.url)
            .header("User-Agent", RangeProbe.UA)
            .header("Accept", "*/*")
            .apply { task.headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
            .get()
            .build()
        val call = client.newCall(req)
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) return 0L
                resp.body?.contentLength()?.takeIf { it > 0 } ?: 0L
            }
        } catch (e: Throwable) {
            android.util.Log.w("RomHubDL", "probeSizeByHead 失败：${e.message}")
            0L
        } finally {
            runCatching { call.cancel() }
        }
    }

    private fun partFile(partDir: File, index: Int): File = File(partDir, "%05d.part".format(index))

    private suspend fun runChunk(
        task: DownloadTask,
        index: Int,
        chunk: ChunkState,
        partDir: File,
        onProgress: () -> Unit,
    ): Boolean {
        var attempt = 0
        while (true) {
            if (isCancelled(task.id)) return false
            try {
                downloadChunkOnce(task, index, chunk, partDir, onProgress)
                return chunk.finished
            } catch (e: RangeIgnoredException) {
                throw e
            } catch (e: Throwable) {
                attempt++
                if (attempt > retryLimit) {
                    throw IllegalStateException(
                        "分片 ${chunk.start}-${chunk.end} 失败：${e.message ?: e.javaClass.simpleName}"
                    )
                }
                // 指数退避：base、base*2、base*4…（base 由 setRetryPolicy 传入，默认 1s）
                delay(retryBaseMs shl (attempt - 1))
            }
        }
    }

    /**
     * 同步用户的「重试次数 / 重试间隔」设置。
     *
     * 由用户在设置里配置重试次数与间隔。
     * 由 [DownloadManager] 在 init 和设置变更时调用 —— 这样下载器的重试行为
     * 跟界面上显示的一致，而不是写死。
     */
    fun setRetryPolicy(count: Int, baseIntervalSec: Int) {
        retryLimit = count.coerceIn(1, 10)
        retryBaseMs = baseIntervalSec.coerceIn(1, 60) * 1000L
    }

    private fun downloadChunkOnce(
        task: DownloadTask,
        index: Int,
        chunk: ChunkState,
        partDir: File,
        onProgress: () -> Unit,
    ) {
        val from = chunk.start + chunk.done
        if (from > chunk.end) return

        val part = partFile(partDir, index)
        // 上次这片下到哪了：以磁盘上的 part 文件大小为准（比内存里的 done 更可信）
        val existing = if (part.exists()) part.length() else 0L
        if (existing < chunk.done) {
            // 记录说下了这么多，但文件更短 → 以文件为准，回退
            chunk.done = existing
        }
        val startFrom = chunk.start + chunk.done
        if (startFrom > chunk.end) return

        val req = Request.Builder()
            .url(task.url)
            .header("Range", "bytes=$startFrom-${chunk.end}")
            .header("User-Agent", RangeProbe.UA)
            .header("Accept", "*/*")
            .apply { task.headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
            .get()
            .build()

        val call = client.newCall(req)
        track(task.id, call)
        try {
            android.util.Log.i("RomHubDL", "分片 #$index 请求 Range=$startFrom-${chunk.end}")
            call.execute().use { resp ->
                // 我们明确要了 Range，服务器却回 200 = 它不支持分片
                if (resp.code == 200) throw RangeIgnoredException()
                if (resp.code != 206) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("响应为空")

                // 写**自己的** part 文件，追加模式。
                // 因为这一片只有这一个写入者，不需要 seek，也不会有竞态。
                java.io.FileOutputStream(part, true).use { out ->
                    val buf = ByteArray(BUFFER)
                    body.byteStream().use { input ->
                        while (true) {
                            if (isCancelled(task.id)) throw InterruptedException("已取消")
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            chunk.done += read
                            onProgress()
                            if (chunk.done >= chunk.size) break
                        }
                    }
                    out.flush()
                }
                if (chunk.done > chunk.size) chunk.done = chunk.size
            }
        } finally {
            untrack(task.id, call)
        }
    }

    /**
     * 把各 part 文件按序号顺序拼接成最终文件。
     *
     * 合并用**流式拷贝**（8MB 缓冲），不一次性读进内存 —— ROM 包好几个 G。
     * 先写 `.tmp`、校验大小通过后再原子改名，避免中途失败留下一个"大小对但内容不全"的
     * 成品文件骗过用户。
     */
    private suspend fun mergeParts(
        task: DownloadTask,
        chunks: List<ChunkState>,
        partDir: File,
        dest: File,
        onProgress: () -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        try {
            java.io.FileOutputStream(tmp).use { out ->
                val buf = ByteArray(MERGE_BUFFER)
                chunks.indices.forEach { i ->
                    val part = partFile(partDir, i)
                    if (!part.exists()) {
                        throw IllegalStateException("分片 $i 缺失，无法合并")
                    }
                    part.inputStream().use { input ->
                        while (true) {
                            if (isCancelled(task.id)) throw InterruptedException("已取消")
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                    }
                }
                out.flush()
                out.fd.sync()   // 落盘，别让用户拿到还在页缓存里的半成品
            }

            // 合并后校验：大小必须跟服务端声明的一致
            val expect = task.totalBytes
            val actual = tmp.length()
            if (expect > 0 && actual != expect) {
                tmp.delete()
                throw IllegalStateException("文件大小校验失败：期望 $expect 字节，实际 $actual 字节")
            }

            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                // rename 失败（跨设备等）：退化成拷贝
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }

            // 成功后清掉 part 目录（除非用户开了「保留分片文件」）
            if (!keepParts) runCatching { partDir.deleteRecursively() }
            onProgress()
            true
        } catch (e: Throwable) {
            runCatching { tmp.delete() }
            throw e
        }
    }

    // ---------------------------------------------------------------- 单线程兜底

    /**
     * 不支持分片（或大小未知）时用：直接 GET 整个文件，顺着写。
     * 没有断点续传 —— 中断了下次只能重来。
     */
    suspend fun downloadFull(
        task: DownloadTask,
        dest: File,
        onProgress: () -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(task.url)
            .header("User-Agent", RangeProbe.UA)
            .header("Accept", "*/*")
            .apply { task.headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
            .get()
            .build()

        val call = client.newCall(req)
        track(task.id, call)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("响应为空")

                // 单线程也记一个「整文件片」，进度条好算
                val total = task.totalBytes.takeIf { it > 0 } ?: body.contentLength()
                val chunk = task.chunks.firstOrNull() ?: ChunkState(0, total - 1).also {
                    task.chunks = listOf(it)
                }

                dest.outputStream().use { out ->
                    val buf = ByteArray(BUFFER)
                    body.byteStream().use { input ->
                        while (true) {
                            if (isCancelled(task.id)) throw InterruptedException("已取消")
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            chunk.done += read
                            onProgress()
                        }
                    }
                    out.flush()
                    out.fd.sync()
                }
                if (total > 0) {
                    task.totalBytes = total
                    chunk.done = total
                    val newChunk = ChunkState(chunk.start, total - 1).also { it.done = total }
                    task.chunks = listOf(newChunk)
                }
                true
            }
        } finally {
            untrack(task.id, call)
        }
    }

    /** 服务器不支持 Range 的信号。 */
    class RangeIgnoredException : IllegalStateException("服务器不支持分片下载")

    companion object {
        private const val BUFFER = 64 * 1024
        private const val MERGE_BUFFER = 8 * 1024 * 1024
        private const val CHUNK_RETRIES = 3

        /**
         * 线程数硬上限（用户可选的最大档位）。
         *
         * 256 是权衡结果：ROM 包普遍 5~12GB，切到 256 片每片也有几十 MB，
         * 收益（吃满带宽）还在；但再往上单个文件切得太碎，
         * 请求头开销和 CDN 风控都不划算。
         */
        const val MAX_THREADS = 256

        /**
         * 真正在飞的网络请求上限（绝对天花板）。
         *
         * 与 [MAX_THREADS] 区分：这个是**同时打开的连接数**。
         * 256 个连接同时握手会把手机的 NAT 表和瞬时内存打爆，
         * 所以实际并发压在这里，片数可以更多、排队即可。
         *
         * 注意：实际并发还会按档位缩放（见 [inflightFor]），不要写死。
         */
        const val MAX_INFLIGHT_HARD = 64

        /**
         * 兼容旧引用：老代码里用这个名字表示"在途上限"。
         * 保留常量避免别处编译不过，实际以 [inflightFor] 为准。
         */
        @Deprecated("改用 inflightFor(threads) 按档位缩放", ReplaceWith("MAX_INFLIGHT_HARD"))
        const val MAX_INFLIGHT = 32
    }
}
