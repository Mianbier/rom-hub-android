package org.linbaogu.romhub.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

/**
 * 下载前的探测：这条链接多大、能不能分片、存成什么文件名。
 *
 * 做法是只请求 `Range: bytes=0-0`（一个字节）：
 *   · 服务器回 206 + `Content-Range: bytes 0-0/123456789` → 总大小已知，且**支持分片**
 *   · 服务器回 200（无视 Range）→ 不支持分片，只能单线程顺着下
 * 这样既拿到信息，又几乎不耗流量 —— 直接 GET 一个几 G 的 ROM 包是不可接受的。
 */
object RangeProbe {

    const val UA = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val contentRangeRe = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

    data class Info(
        /** 总字节数；-1 表示服务器没说 */
        val total: Long,
        /** 支持断点/分片 */
        val resumable: Boolean,
        val fileName: String,
        val mime: String?,
    )

    suspend fun probe(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Info = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .apply { headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("服务器返回 HTTP ${resp.code}")
            }
            val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()
            val name = pickFileName(resp.header("Content-Disposition"), url, mime)

            val cr = contentRangeRe.find(resp.header("Content-Range").orEmpty())
            val total = cr?.groupValues?.getOrNull(3)
                ?.takeUnless { it == "*" }
                ?.toLongOrNull()
                ?: resp.header("Content-Length")?.toLongOrNull()
                ?: -1L

            // 只有 206 + Content-Range 才算「真支持分片」；200 是服务器忽略了 Range
            val resumable = resp.code == 206 && cr != null

            Info(total, resumable, name, mime)
        }
    }

    // ---------------------------------------------------------------- 文件名推断

    private fun pickFileName(disposition: String?, url: String, mime: String?): String {
        disposition?.let { d ->
            // filename*=UTF-8''xxx%20yyy（RFC 5987，优先）
            Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(d)?.let { m ->
                val v = m.groupValues[1].trim().trim('"')
                val encoded = v.substringAfter("''", v)
                decodeQuietly(encoded).takeIf { it.isNotBlank() }?.let { return it }
            }
            // filename="xxx"
            Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(d)?.let { m ->
                decodeQuietly(m.groupValues[1].trim().trim('"'))
                    .takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        // 从 URL 末段取，去掉 query
        val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val last = path.substringAfterLast('/')
        val decoded = decodeQuietly(last)
        if (decoded.isNotBlank() && decoded.contains('.')) return decoded

        return "download_${System.currentTimeMillis()}" + extFromMime(mime)
    }

    private fun extFromMime(mime: String?): String = when (mime?.lowercase()) {
        "application/zip", "application/x-zip-compressed" -> ".zip"
        "application/vnd.android.package-archive" -> ".apk"
        "application/octet-stream" -> ".bin"
        "text/plain" -> ".txt"
        else -> ""
    }

    private fun decodeQuietly(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    /** 文件名里的非法字符换成下划线，并且别让路径跑出目录。 */
    fun sanitize(name: String, fallback: String): String {
        var cleaned = name.replace(Regex("""[\\/:*?"<>|\x00-\x1f]"""), "_").trim()
        cleaned = cleaned.trimStart('.')          // 别生成 .hidden / ../x
        if (cleaned.isBlank()) cleaned = fallback
        if (cleaned.length > 120) {
            val ext = cleaned.substringAfterLast('.', "").take(10)
            val base = cleaned.substringBeforeLast('.').take(100)
            cleaned = if (ext.isNotBlank() && ext != cleaned) "$base.$ext" else cleaned.take(120)
        }
        return cleaned
    }
}
