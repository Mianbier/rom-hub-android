package org.linbaogu.romhub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.linbaogu.romhub.core.Prefs
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
internal val RomJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

class ApiException(message: String) : Exception(message)

/**
 * 后端 REST 客户端。所有接口都来自 rom-hub 的 server/app/api.py。
 */
object Api {

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 当前用的服务器地址。
     *
     * 服务端可能跑在手机本机（127.0.0.1:8787）、局域网电脑（192.168.x.x:8787）
     * 或公网域名上，用户切网络 / 换设备跑服务都得能连上，所以地址是**可变的**：
     * 存在 Prefs 里，请求失败时由 [pickNextBase] 自动换到下一个能通的。
     */
    fun currentBase(ctx: Context): String = Prefs.serverBase(ctx)

    /** 候选地址：手机本机 → 公网兜底。 */
    private fun candidates(ctx: Context): List<String> =
        (Prefs.localBases() + Prefs.DEFAULT_BASE).map { it.trimEnd('/') }.distinct()

    /** 探一下这个地址通不通（很短超时，失败就换下一个，不干等）。 */
    suspend fun probe(base: String, timeoutMs: Long = 2500): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = client.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val r = Request.Builder().url(base.trimEnd('/') + "/api/app/update").get().build()
            c.newCall(r).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * 自动挑一个能用的地址：本机服务优先（手机跑服务端时最稳），
     * 其次局域网/公网。找到就写回 Prefs，之后所有请求都走它。
     *
     * @return 可用的地址，都没有则 null。
     */
    suspend fun autoDetect(ctx: Context): String? {
        // 当前地址先试，避免无谓切换
        val cur = currentBase(ctx).trimEnd('/')
        if (probe(cur)) return cur
        for (cand in candidates(ctx)) {
            if (cand == cur) continue
            if (probe(cand)) {
                Prefs.setServerBase(ctx, cand)
                return cand
            }
        }
        return null
    }

    private suspend fun pickNextBase(ctx: Context, tried: Set<String>): String? {
        for (cand in candidates(ctx)) {
            if (cand in tried) continue
            if (probe(cand)) return cand
        }
        return null
    }

    /**
     * 服务端上的最新 App 版本（接口每次读 server/data/app_update.json，改完即生效）。
     * 拿不到就返回 null —— 更新提示是锦上添花，不能因为接口挂了就影响使用。
     */
    suspend fun appUpdate(ctx: Context): AppUpdateInfo? =
        runCatching {
            RomJson.decodeFromString<AppUpdateInfo>(raw(ctx, "/api/app/update", timeoutSec = 15))
        }.getOrNull()

    private suspend fun raw(
        ctx: Context,
        path: String,
        method: String = "GET",
        jsonBody: String? = null,
        token: String? = null,
        timeoutSec: Long = 30,
    ): String = withContext(Dispatchers.IO) {
        val tried = LinkedHashSet<String>()
        var sameAddrRetried = false

        while (true) {
            val curBase = Prefs.serverBase(ctx)
            tried += curBase.trimEnd('/')

            val builder = Request.Builder().url(curBase.trimEnd('/') + path)
            when (method) {
                "POST" -> builder.post((jsonBody ?: "{}").toRequestBody(JSON_MT))
                "PATCH" -> builder.patch((jsonBody ?: "{}").toRequestBody(JSON_MT))
                "DELETE" -> builder.delete()
                else -> builder.get()
            }
            if (!token.isNullOrBlank()) {
                builder.header("x-token", token)
                builder.header("X-Token", token)
            }
            val localClient = if (timeoutSec > 30) {
                client.newBuilder().readTimeout(timeoutSec, TimeUnit.SECONDS).build()
            } else {
                client
            }

            val result = runCatching {
                localClient.newCall(builder.build()).execute().use { r ->
                    r.code to r.body?.string().orEmpty()
                }
            }

            val code = result.getOrNull()?.first
            if (code != null && code in 200..299) {
                return@withContext result.getOrNull()?.second.orEmpty()
            }

            // 连不上，或 502/503/504（服务正在重启 / 隧道抖动）：先原地重试一次
            val retryable = result.isFailure || code == null || code in 502..504
            if (retryable && !sameAddrRetried) {
                sameAddrRetried = true
                delay(600)
                continue
            }

            // 还是不行 → 换地址重来（手机本机服务、局域网、公网之间倒）
            if (retryable) {
                val next = pickNextBase(ctx, tried)
                if (next != null) {
                    Prefs.setServerBase(ctx, next)
                    sameAddrRetried = false
                    continue
                }
            }

            result.exceptionOrNull()?.let { e ->
                throw ApiException(connectionHint(e))
            }
            val hint = when (code) {
                401 -> "令牌无效或未通过审核"
                403 -> "没有权限"
                404 -> "接口不存在"
                409 -> "数据已存在"
                422 -> "参数不合法"
                502, 503, 504 -> "服务器连不上：手机本机、局域网、公网三个地址都试过了"
                in 500..599 -> "服务端错误"
                else -> "HTTP $code"
            }
            throw ApiException("$hint（$code）")
        }
        @Suppress("UNREACHABLE_CODE") ""
    }

    private fun connectionHint(e: Throwable): String {
        val msg = (e.message ?: "").lowercase()
        return when {
            msg.contains("timeout") || msg.contains("timed out") ->
                "连接超时：服务端没响应（手机本机服务没起？或不在同一个网）"
            msg.contains("refused") ->
                "连接被拒绝：这个地址上没有服务在跑"
            msg.contains("unable to resolve") || msg.contains("nodename") ->
                "域名解析失败：检查网络"
            else -> "网络异常：${e.message ?: e.javaClass.simpleName}"
        }
    }

    private inline fun <reified T> decode(text: String): T =
        runCatching { RomJson.decodeFromString<T>(text) }
            .getOrElse { throw ApiException("数据解析失败：${it.message?.take(120)}") }

    private inline fun <reified T> encode(value: T): String = RomJson.encodeToString(value)

    // ------------------------------------------------------------- 基础

    suspend fun stats(ctx: Context): RomStats =
        decode(raw(ctx, "/api/rom-stats"))

    // ------------------------------------------------------------- 机型 / 版本

    suspend fun devices(ctx: Context, keyword: String = "", brand: String = "all"): DeviceListResp =
        decode(raw(ctx, "/api/devices?brand=$brand&keyword=${enc(keyword)}"))

    suspend fun device(ctx: Context, code: String): DeviceDetailResp =
        decode(raw(ctx, "/api/devices/${enc(code)}"))

    suspend fun romVersions(
        ctx: Context,
        code: String,
        region: String = "all",
        branch: String = "all",
        limit: Int = 300,
    ): RomVersionListResp =
        decode(
            raw(
                ctx,
                "/api/rom-versions?codename=${enc(code)}&region=$region&branch=$branch&limit=$limit",
            )
        )

    suspend fun romVersion(ctx: Context, vid: Long): RomVersion =
        decode(raw(ctx, "/api/rom-versions/$vid"))

    /** 给某个版本现签一个官方高速直链（签名会过期，所以点下载时现取）。 */
    suspend fun fastLink(ctx: Context, vid: Long): String =
        decode<FastLinkResp>(raw(ctx, "/api/rom-versions/$vid/fast-link", timeoutSec = 60)).url

    /** 让服务端按需补一次直链（前端打开设备页时也会自己调）。 */
    suspend fun resolvePending(ctx: Context, code: String, region: String, branch: String): String =
        raw(
            ctx,
            "/api/rom-versions/resolve-pending?codename=${enc(code)}&region=$region&branch=$branch&limit=12",
            method = "POST",
            timeoutSec = 60,
        )

    // ------------------------------------------------------------- 动态

    suspend fun romUpdates(
        ctx: Context,
        since: String = "",
        limit: Int = 100,
        state: String = "",
    ): RomUpdateListResp =
        decode(
            raw(ctx, "/api/rom-updates?since=${enc(since)}&limit=$limit&state=${enc(state)}")
        )

    suspend fun updateStates(ctx: Context): Map<String, Int> {
        val text = raw(ctx, "/api/rom-updates/states")
        val root = RomJson.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
        val counts = root["counts"] as? JsonObject ?: return emptyMap()
        return counts.mapNotNull { (k, v) ->
            val n = (v as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            if (n == null) null else k to n
        }.toMap()
    }

    // ------------------------------------------------------------- 移植包

    suspend fun ports(ctx: Context, codename: String = "", limit: Int = 200): PortListResp =
        decode(raw(ctx, "/api/ports?codename=${enc(codename)}&limit=$limit"))

    suspend fun port(ctx: Context, id: Long): PortDetailResp =
        decode(raw(ctx, "/api/ports/$id"))

    suspend fun parsePortLink(ctx: Context, shareUrl: String, token: String): PortParseResp =
        decode(
            raw(
                ctx,
                "/api/ports/parse",
                method = "POST",
                jsonBody = """{"url":${RomJson.encodeToString(shareUrl)}}""",
                token = token,
            )
        )

    suspend fun uploadPort(ctx: Context, req: PortUploadReq, token: String): PortUploadResp =
        decode(raw(ctx, "/api/ports", method = "POST", jsonBody = encode(req), token = token))

    /**
     * 上传一张公告配图（multipart），返回可以直接写进公告文本的 URL。
     *
     * 服务端存到 data/uploads/ 下，返回 {"url":"https://域名/uploads/日期/xxx.png"}；
     * 这个地址是公网固定域名，换网络也不会失效。失败时抛 ApiException。
     */
    suspend fun uploadImage(
        ctx: Context,
        bytes: ByteArray,
        fileName: String,
        mime: String,
        token: String,
    ): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val req = Request.Builder()
            .url(currentBase(ctx).trimEnd('/') + "/api/upload/image")
            .post(body)
            .header("x-token", token)
            .header("X-Token", token)
            .build()
        client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
            .newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw ApiException("图片上传失败（${resp.code}）")
                }
                val url = (RomJson.parseToJsonElement(text) as? JsonObject)
                    ?.get("url")
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                if (url.isBlank()) throw ApiException("服务端没返回图片地址")
                url
            }
    }

    // ------------------------------------------------------------- 公告

    suspend fun notices(ctx: Context): NoticeListResp = decode(raw(ctx, "/api/notices"))

    // ------------------------------------------------------------- 开发者账号

    suspend fun devApply(ctx: Context, req: DevApplyReq): DevApplyResp =
        decode(raw(ctx, "/api/dev/apply", method = "POST", jsonBody = encode(req)))

    suspend fun devLogin(ctx: Context, username: String, password: String): DevLoginResp =
        decode(
            raw(
                ctx,
                "/api/dev/login",
                method = "POST",
                jsonBody = encode(DevLoginReq(username = username, password = password)),
            )
        )

    suspend fun devMe(ctx: Context, token: String): DevMeResp =
        decode(raw(ctx, "/api/dev/me", token = token))

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
