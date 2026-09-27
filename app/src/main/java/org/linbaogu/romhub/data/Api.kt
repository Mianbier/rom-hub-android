package org.linbaogu.romhub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
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

    // 服务器地址固定（已去掉修改入口）
    private fun base(ctx: Context): String = Prefs.DEFAULT_BASE

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
        val url = base(ctx).trimEnd('/') + path
        val builder = Request.Builder().url(url)
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
        val req = builder.build()
        val localClient = if (timeoutSec > 30) {
            client.newBuilder().readTimeout(timeoutSec, TimeUnit.SECONDS).build()
        } else {
            client
        }
        localClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val hint = when (resp.code) {
                    401 -> "令牌无效或未通过审核"
                    403 -> "没有权限"
                    404 -> "接口不存在"
                    409 -> "数据已存在"
                    422 -> "参数不合法"
                    in 500..599 -> "服务端错误"
                    else -> "HTTP ${resp.code}"
                }
                throw ApiException("$hint（${resp.code}）")
            }
            text
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
