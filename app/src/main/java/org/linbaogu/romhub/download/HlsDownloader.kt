/*
 * 搬运自 YunX (云析) - Copyright (C) 2026 CYQawa
 * 原文件：app/src/main/kotlin/com/yunx/app/data/download/HlsDownloader.kt
 * 许可：GNU Affero General Public License v3.0（见项目根 LICENSE）
 *
 * 改动说明：包名 com.yunx.app.data.download → org.linbaogu.romhub.download；
 *          HttpClients 改为引用本项目的 org.linbaogu.romhub.pan.HttpClients；日志 TAG 去掉云析前缀。
 *          2026-10-05：分片下载由「顺序」改为「有界并行」，并发段数对应设置里的 M3U8 分片线程数。
 */

package org.linbaogu.romhub.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.linbaogu.romhub.pan.HttpClients
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/**
 * HLS（m3u8）下载器。
 *
 * 与普通文件分片下载的区别：HLS 的「片」是**播放列表里已经切好的 ts / fmp4 段**，
 * 粒度由源站决定，我们不能自己随便切。所以我们能控制的是「同时拉多少段」——
 * 这正是设置里「M3U8 分片线程数」的作用。
 *
 * ## 并发模型（2026-10-05 改）
 *
 * 老实现是 `segments.forEachIndexed { ... }` **顺序**拉取，无论线程数设多少都只有
 * 一条连接在跑 —— HLS 视频动辄几百段，速度完全上不去。
 *
 * 新实现：
 *   · 用 [Semaphore] 限制**同时在飞的段数** = 调用方传入的 [download] 的 `threads`；
 *   · 每段先下到**内存 buffer**，完成后按播放列表顺序写入目标文件；
 *   · 写入串行进行 —— 保证输出顺序与播放列表一致（HLS 段有序，乱序拼出来播不了）。
 *
 * 为什么不并发 `RandomAccessFile.seek` 写同一个文件：那正是普通分片下载踩过的坑
 * （`lseek+write` 非原子 → 字节错位）。且 HLS 段大小不一，无法预先算偏移，
 * 所以走「内存暂存 + 顺序落盘」。
 *
 * ## 安全约束（保持不变）
 *
 * **凭证（Cookie / Authorization）绝不跨源转发**，见 [HlsRequestPolicy]。
 */
object HlsDownloader {
    private const val TAG = "RomHub-HLS"
    private const val MAX_REDIRECTS = 5
    private const val MAX_PLAYLIST_BYTES = 1024 * 1024L
    private const val MAX_SEGMENTS = 20_000
    private const val MAX_SEGMENT_BYTES = 512L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024 * 1024
    private const val COPY_BUFFER_SIZE = 64 * 1024

    /** 段并发上限。用户设置还会再夹一层，这里只是绝对天花板。 */
    private const val MAX_SEGMENT_THREADS = 32

    // Redirects are handled here so a cross-origin hop cannot inherit Cookie/Authorization.
    private val client get() = HttpClients.apiClient().newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * @param threads 同时拉取的段数（来自设置里的「M3U8 分片线程数」）。
     *                1 = 顺序下载（老行为）；越大越快，但也越容易被源站风控。
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        destFile: File,
        threads: Int = 4,
        onBytes: suspend (Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val credentialOrigin = HlsRequestPolicy.initialUrl(url) ?: run {
            Log.w(TAG, "拒绝非 HTTPS 或无效的 HLS 地址")
            return@withContext false
        }

        runCatching {
            val master = fetchText(credentialOrigin, credentialOrigin, headers) ?: return@runCatching false
            val mediaUrl = resolveMediaPlaylist(master.finalUrl, master.text) ?: return@runCatching false
            val media = if (mediaUrl == master.finalUrl) master
            else fetchText(mediaUrl, credentialOrigin, headers) ?: return@runCatching false

            if (media.text.contains("#EXT-X-KEY") || media.text.contains("#EXT-X-BYTERANGE")) {
                Log.w(TAG, "HLS 含不支持的加密或 BYTERANGE")
                return@runCatching false
            }

            val initUri = parseMapUri(media.text)?.let { HlsRequestPolicy.resolve(media.finalUrl, it) }
            val rawSegments = parseSegments(media.text)
            if (rawSegments.isEmpty()) return@runCatching false
            val segments = rawSegments.map { raw ->
                HlsRequestPolicy.resolve(media.finalUrl, raw)
                    ?: throw IllegalArgumentException("HLS 分片地址不是受支持的 HTTPS URL")
            }

            destFile.parentFile?.mkdirs()

            // 写入位置游标：内容按顺序追加，顺序由本函数的控制流保证（不是并发协程）。
            var writeOffset = 0L

            // 把一段字节写到当前位置并推进游标。只在本协程（串行）里调用。
            // 用 RandomAccessFile 是为了兼容「先写了 init 段、再追加媒体段」的场景；
            // 每次打开-写-关闭，避免长期持有句柄。
            fun commit(bytes: ByteArray) {
                RandomAccessFile(destFile, "rw").use { raf ->
                    raf.seek(writeOffset)
                    raf.write(bytes)
                }
                writeOffset += bytes.size
            }

            // 建/清空目标文件
            FileOutputStream(destFile, false).use { }

            // ---- init 段（fmp4 的 moov 等），必须最先写 ----
            if (initUri != null) {
                val initBytes = fetchBytes(initUri, credentialOrigin, headers) ?: return@runCatching false
                if (initBytes.isEmpty()) return@runCatching false
                commit(initBytes)
                onBytes(initBytes.size.toLong())
            }

            val limit = threads.coerceIn(1, MAX_SEGMENT_THREADS)
            if (limit <= 1) {
                // 单线程：逐段拉完立即写，内存占用恒定
                segments.forEachIndexed { index, segment ->
                    val bytes = fetchBytes(segment, credentialOrigin, headers) ?: return@runCatching false
                    if (bytes.isEmpty()) return@runCatching false
                    if (writeOffset + bytes.size > MAX_TOTAL_BYTES) {
                        throw IllegalStateException("HLS 总下载量超过限制")
                    }
                    commit(bytes)
                    onBytes(bytes.size.toLong())
                    if (index % 10 == 0) Log.d(TAG, "HLS 分片 ${index + 1}/${segments.size}")
                }
            } else {
                // 并发拉取 + 保序落盘：
                //   · async 各段并发取回 ByteArray（受 sem 限流）
                //   · 主协程按 index 顺序 await + commit —— 顺序由 await 次序保证，
                //     即使第 5 段比第 2 段先下完，也一定等第 2 段写完才轮到它。
                //   任一段失败立即抛出 → 整体失败（catch 里会删掉半成品）。
                val sem = Semaphore(limit)
                coroutineScope {
                    val deferred = segments.mapIndexed { index, segment ->
                        async {
                            sem.withPermit {
                                val bytes = fetchBytes(segment, credentialOrigin, headers)
                                    ?: throw IllegalStateException("HLS 分片 $index 拉取失败")
                                if (bytes.isEmpty()) throw IllegalStateException("HLS 分片 $index 为空")
                                if (writeOffset + bytes.size > MAX_TOTAL_BYTES) {
                                    throw IllegalStateException("HLS 总下载量超过限制")
                                }
                                bytes
                            }
                        }
                    }
                    deferred.forEachIndexed { index, d ->
                        val bytes = d.await()
                        commit(bytes)
                        onBytes(bytes.size.toLong())
                        if (index % 10 == 0) Log.d(TAG, "HLS 分片 ${index + 1}/${segments.size}")
                    }
                }
            }
            Log.d(TAG, "HLS 下载完成 segments=${segments.size} size=$writeOffset threads=$limit")
            true
        }.onFailure {
            Log.e(TAG, "HLS 下载失败: ${it.message}")
        }.getOrDefault(false).also { success ->
            if (!success) destFile.delete()
        }
    }

    private data class FetchedText(val finalUrl: HttpUrl, val text: String)

    private suspend fun fetchText(
        startUrl: HttpUrl,
        credentialOrigin: HttpUrl,
        headers: Map<String, String>
    ): FetchedText? {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val result = executeCancellable(requestFor(current, credentialOrigin, headers)) { response ->
                redirectTarget(response, current)?.let { return@executeCancellable it to null }
                if (!response.isSuccessful) return@executeCancellable null
                val body = response.body ?: return@executeCancellable null
                null to readBoundedText(body.byteStream(), body.contentLength())
            } ?: return null
            val redirect = result.first
            if (redirect == null) return FetchedText(current, result.second ?: return null)
            if (redirectCount >= MAX_REDIRECTS) return null
            current = redirect
        }
        return null
    }

    /**
     * 一次请求把整段读成 ByteArray（跟随重定向，最多 [MAX_REDIRECTS] 跳）。
     * 失败返回 null。
     */
    private suspend fun fetchBytes(
        startUrl: HttpUrl,
        credentialOrigin: HttpUrl,
        headers: Map<String, String>,
    ): ByteArray? {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val result = executeCancellable(requestFor(current, credentialOrigin, headers)) { response ->
                redirectTarget(response, current)?.let { return@executeCancellable Redirect(it) }
                if (!response.isSuccessful) return@executeCancellable Failed
                val body = response.body ?: return@executeCancellable Failed
                if (body.contentLength() > MAX_SEGMENT_BYTES) {
                    throw IllegalStateException("HLS 分片超过大小限制")
                }
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var written = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        written += count
                        if (written > MAX_SEGMENT_BYTES) {
                            throw IllegalStateException("HLS 分片超过大小限制")
                        }
                        out.write(buffer, 0, count)
                    }
                }
                Ok(out.toByteArray())
            }
            when (result) {
                is Ok -> return result.bytes
                is Redirect -> {
                    if (redirectCount >= MAX_REDIRECTS) return null
                    current = result.url
                }
                Failed -> return null
            }
        }
        return null
    }

    private sealed interface FetchResult
    private data class Ok(val bytes: ByteArray) : FetchResult
    private data class Redirect(val url: HttpUrl) : FetchResult
    private data object Failed : FetchResult

    private fun requestFor(
        target: HttpUrl,
        credentialOrigin: HttpUrl,
        headers: Map<String, String>
    ): Request = Request.Builder()
        .url(target)
        .apply {
            HlsRequestPolicy.headersFor(target, credentialOrigin, headers)
                .forEach { (name, value) -> header(name, value) }
        }
        .get()
        .build()

    private fun redirectTarget(response: Response, current: HttpUrl): HttpUrl? {
        if (response.code !in 300..399) return null
        val location = response.header("Location") ?: return null
        return HlsRequestPolicy.resolve(current, location)
            ?: throw IllegalArgumentException("HLS 重定向到非 HTTPS 地址")
    }

    private suspend fun <T> executeCancellable(request: Request, block: suspend (Response) -> T): T {
        val call = client.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        val response = call.execute()
        return try {
            block(response)
        } finally {
            response.close()
            cancelHandle?.dispose()
        }
    }

    private fun readBoundedText(input: java.io.InputStream, declaredLength: Long): String {
        if (declaredLength > MAX_PLAYLIST_BYTES) throw IllegalStateException("HLS 播放列表超过大小限制")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (output.size().toLong() + count > MAX_PLAYLIST_BYTES) {
                    throw IllegalStateException("HLS 播放列表超过大小限制")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun resolveMediaPlaylist(playlistUrl: HttpUrl, text: String): HttpUrl? {
        val lines = text.lineSequence().toList()
        for (index in lines.indices) {
            if (lines[index].startsWith("#EXT-X-STREAM-INF")) {
                val next = lines.getOrNull(index + 1)?.trim() ?: continue
                if (next.isNotBlank() && !next.startsWith("#")) {
                    return HlsRequestPolicy.resolve(playlistUrl, next)
                }
            }
        }
        return playlistUrl
    }

    private fun parseSegments(text: String): List<String> = buildList {
        for (line in text.lineSequence()) {
            val value = line.trim()
            if (value.isNotBlank() && !value.startsWith("#")) {
                if (size >= MAX_SEGMENTS) throw IllegalStateException("HLS 分片数量超过限制")
                add(value)
            }
        }
    }

    private fun parseMapUri(text: String): String? {
        val line = text.lineSequence().firstOrNull { it.startsWith("#EXT-X-MAP") } ?: return null
        return Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
    }
}
