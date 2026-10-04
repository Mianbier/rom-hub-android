package org.linbaogu.romhub.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * 多线程分片下载器。
 *
 * 原理（跟所有"高速下载器"一样）：
 *   1. 先探测总大小，把文件切成 N 片；
 *   2. 每片发一个带 `Range: bytes=a-b` 的请求，各自并发下载；
 *   3. 每片下完就 write 到文件对应的偏移（RandomAccessFile.seek），不用等前面；
 *   4. 每片的下到的字节数记在 [ChunkState.done] 里 → 断了再连接着下（断点续传）。
 *
 * 不支持 Range 的服务器自动回退成单线程流式下载（[downloadFull]）。
 */
class ChunkDownloader(private val client: OkHttpClient) {

    private val inflight = ConcurrentHashMap<Long, MutableSet<Call>>()
    private val cancelledIds: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())

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

    // ---------------------------------------------------------------- 分片下载

    /**
     * 按 [task].chunks 并发下载。
     *
     * 关键点：**片数可以到 256，但真正在飞的请求要限流**。
     * 直接 `chunks.map { async }` 会同时打出 256 个 TCP 连接 ——
     * 轻则手机瞬时内存/带宽被打满、OkHttp 排队超时，重则被 CDN 判定为异常流量。
     * 所以这里用信号量把在途请求压在 [MAX_INFLIGHT] 内，
     * 多出来的片在协程里排队等信号量，进度与断点续传都不受影响。
     *
     * @return true 全部片都完成。
     * @throws RangeIgnored 服务器无视 Range（需要调用方回退到单线程）。
     */
    suspend fun downloadChunked(
        task: DownloadTask,
        dest: File,
        onProgress: () -> Unit,
    ): Boolean {
        val chunks = task.chunks
        if (chunks.isEmpty()) return false

        // 在途上限：跟分片时用的线程数保持一致（任务未单独指定就用全局默认 8）。
        // 客户端连接池也是按这个量级配的（见 DownloadManager.init）。
        val inflightCap = task.effectiveThreads(8).coerceAtMost(MAX_INFLIGHT)

        return coroutineScope {
            val gate = Semaphore(inflightCap)
            val results = chunks.map { chunk ->
                async(Dispatchers.IO) {
                    // 拿不到许可就在这里挂起等，不占线程、不发请求。
                    gate.acquire()
                    try {
                        runChunk(task, chunk, dest, onProgress)
                    } finally {
                        gate.release()
                    }
                }
            }.awaitAll()
            results.all { it }
        }
    }

    private suspend fun runChunk(
        task: DownloadTask,
        chunk: ChunkState,
        dest: File,
        onProgress: () -> Unit,
    ): Boolean {
        var attempt = 0
        while (true) {
            if (isCancelled(task.id)) return false
            try {
                downloadChunkOnce(task, chunk, dest, onProgress)
                return chunk.finished
            } catch (e: RangeIgnoredException) {
                throw e
            } catch (e: Throwable) {
                attempt++
                if (attempt > CHUNK_RETRIES || isCancelled(task.id)) {
                    throw IllegalStateException(
                        "分片 ${chunk.start}-${chunk.end} 失败：${e.message ?: e.javaClass.simpleName}"
                    )
                }
                // 指数退避：1s、2s、4s
                delay(1000L shl (attempt - 1))
            }
        }
    }

    private fun downloadChunkOnce(
        task: DownloadTask,
        chunk: ChunkState,
        dest: File,
        onProgress: () -> Unit,
    ) {
        val from = chunk.start + chunk.done
        if (from > chunk.end) return

        val req = Request.Builder()
            .url(task.url)
            .header("Range", "bytes=$from-${chunk.end}")
            .header("User-Agent", RangeProbe.UA)
            .header("Accept", "*/*")
            .apply { task.headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
            .get()
            .build()

        val call = client.newCall(req)
        track(task.id, call)
        try {
            call.execute().use { resp ->
                // 我们明确要了 Range，服务器却回 200 = 它不支持分片
                if (resp.code == 200) throw RangeIgnoredException()
                if (resp.code != 206) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("响应为空")

                RandomAccessFile(dest, "rw").use { raf ->
                    raf.seek(from)
                    val buf = ByteArray(BUFFER)
                    body.byteStream().use { input ->
                        while (true) {
                            if (isCancelled(task.id)) throw InterruptedException("已取消")
                            val read = input.read(buf)
                            if (read <= 0) break
                            raf.write(buf, 0, read)
                            chunk.done += read
                            onProgress()
                            if (chunk.done >= chunk.size) break
                        }
                    }
                }
                if (chunk.done > chunk.size) chunk.done = chunk.size
            }
        } finally {
            untrack(task.id, call)
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
         * 真正在飞的网络请求上限。
         *
         * 与 [MAX_THREADS] 区分：这个是**同时打开的连接数**。
         * 256 个连接同时握手会把手机的 NAT 表和瞬时内存打爆，
         * 所以实际并发压在这里，片数可以更多、排队即可。
         */
        const val MAX_INFLIGHT = 32
    }
}
