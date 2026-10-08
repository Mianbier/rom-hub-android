package org.linbaogu.romhub.data.brand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 「全量包提取」站（`opusrom.top`）的**直链解析**。
 *
 * 静态数据（[OpusIndex]）里只有版本元信息，一个下载直链都没有 ——
 * 实测 vivo/iQOO 全部 1724 个版本都是 `needsResolve=true` 且零直链字段。
 * 所以要真下载，必须走这个类现取直链。
 *
 * ### 完整链路（逆向自 app.js，2026-09 实测）
 *
 * 1. `POST /api/challenge/start`  `{action,key,fp,behavior}` → `{id, pow:{prefix,difficulty}}`
 * 2. 算 PoW：找 nonce 使 `sha256(prefix + nonce)` 的**前 difficulty 位为 0**（difficulty=16，约 10ms）
 * 3. `POST /api/challenge/verify` `{id,action,key,elapsed,moves,fp,behavior,pow_nonce}`
 *    → **`{ticket, sid, secret}`**
 * 4. `GET /api/resolve?brand&device&major&version&flash&model`
 *    头 `X-ROM-Sid / Ts / Nonce / Sig` + `X-ROM-Ticket` → `{url}`
 *
 * ### 三个把人坑进去的细节
 *
 * - **secret 是 hex 串，HMAC 前必须先 `hexToBytes()` 解码成字节**。
 *   app.js 里是 `crypto.subtle.importKey('raw', hexToBytes(secretHex), ...)`。
 *   拿 hex 字符串的 UTF-8 字节当密钥，签名永远对不上（会一直 401）。
 * - **nonce 必须每次重新随机生成**。写死会触发服务端的「nonce 已使用」拒绝，
 *   而那**不算签名失败**——连续踩满 12 次就会被判定为爆破，**软封 IP 6 小时**。
 *   所以这里绝不能做「多组合试探」，必须一次算对。
 * - **canonical 里的 query 串要和真实 URL 完全一致**，包括参数顺序和编码方式。
 *   编码遵循 WHATWG `URLSearchParams` 规则：保留 `A-Za-z0-9` 与 `* - . _`，空格转 `+`。
 *
 * canonical 结构（`\n` 连接）：
 * ```
 * METHOD \n path \n rawQuery \n ts \n nonce \n sha256hex(body)
 * ```
 * 签名 `X-ROM-Sig = HMAC-SHA256(hexToBytes(secret), canonical)`，输出小写 hex。
 */
class OpusResolver(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * 解析一个版本的真实下载直链。
     *
     * @param version opusrom 静态数据里的条目，自带 `apiBrand/apiDevice/…` 解析参数
     * @return 直链；失败时抛 [OpusException]
     */
    suspend fun resolve(version: OpusVersion): String = withContext(Dispatchers.IO) {
        val brand = version.apiBrand
        val device = version.apiDevice
        val major = version.apiMajor
        val ver = version.version
        val flash = version.apiFlash
        val model = version.oplusModel

        if (brand.isBlank() || device.isBlank() || ver.isBlank()) {
            throw OpusException("这个版本缺少解析参数，无法取直链")
        }

        val key = listOf(brand, device, major, ver, flash, model).joinToString("|")

        // ---------------------------------------------------- 1. 申请 challenge
        val start = postJson(
            "/challenge/start",
            buildJsonObject {
                put("action", "resolve")
                put("key", key)
                put("fp", FINGERPRINT)
                put("behavior", BEHAVIOR)
            },
        )
        val cid = start.str("id")
        if (cid.isBlank()) throw OpusException("取直链：challenge 申请失败（${start.errorHint()}）")

        // ---------------------------------------------------- 2. 算 PoW
        val pow = start["pow"] as? JsonObject
        val prefix = pow?.str("prefix").orEmpty()
        val difficulty = pow?.str("difficulty")?.toIntOrNull() ?: DEFAULT_DIFFICULTY
        val powNonce = if (prefix.isBlank()) null else solvePow(prefix, difficulty)

        // ---------------------------------------------------- 3. 换签名票据
        val verified = postJson(
            "/challenge/verify",
            buildJsonObject {
                put("id", cid)
                put("action", "resolve")
                put("key", key)
                put("elapsed", 900)
                put("moves", BEHAVIOR_MOVES)
                put("fp", FINGERPRINT)
                put("behavior", BEHAVIOR)
                if (powNonce != null) put("pow_nonce", powNonce)
            },
        )
        val sid = verified.str("sid")
        val secret = verified.str("secret")
        val ticket = verified.str("ticket")
        if (sid.isBlank() || secret.isBlank()) {
            throw OpusException("取直链：签名校验未通过（${verified.errorHint()}）")
        }

        // ---------------------------------------------------- 4. 换直链
        // 参数顺序即签名顺序，不能改
        val rawQuery = listOf(
            "brand" to brand,
            "device" to device,
            "major" to major,
            "version" to ver,
            "flash" to flash,
            "model" to model,
        ).joinToString("&") { (k, v) -> "$k=${OtaCrypto.formEncode(v)}" }

        val ts = System.currentTimeMillis().toString()
        // 每次都要新的 nonce —— 重放会被判「nonce 已使用」
        val nonce = OtaCrypto.hex(OtaCrypto.randomBytes(12))
        val canonical = listOf(
            "GET",
            RESOLVE_PATH,
            rawQuery,
            ts,
            nonce,
            OtaCrypto.hex(OtaCrypto.sha256(ByteArray(0))),   // GET 无 body，即空串的哈希
        ).joinToString("\n")
        val sig = hmacSha256Hex(OtaCrypto.unHex(secret), canonical)

        val url = "$API$RESOLVE_PATH?$rawQuery"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://$HOST/")
            .header("X-ROM-Sid", sid)
            .header("X-ROM-Ts", ts)
            .header("X-ROM-Nonce", nonce)
            .header("X-ROM-Sig", sig)
            .header("X-ROM-Ticket", ticket)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // 不加「取直链失败」前缀 —— 上层会统一加，多层叠加会让用户看到
                // 「取直链失败：取直链失败：取直链失败」这种叠加文案。
                throw OpusException("解析接口返回 HTTP ${resp.code} ${text.take(200)}")
            }
            val obj = runCatching { JSON.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: throw OpusException("取直链：响应认不出（${text.take(200)}）")
            val link = obj.str("url").ifBlank { obj.str("downloadUrl") }
            if (link.isBlank()) throw OpusException("取直链：接口没返回链接（${obj.errorHint()}）")
            link
        }
    }

    // ------------------------------------------------------------- 内部

    private fun postJson(path: String, payload: JsonObject): JsonObject {
        val body = JSON.encodeToString(JsonObject.serializer(), payload)
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$API$path")
            .post(body)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://$HOST/")
            .header("Origin", "https://$HOST")
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            // 非 2xx 时服务端把原因写在 JSON 里，交给上层解析成人话
            val obj = runCatching { JSON.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (obj == null) {
                throw OpusException("请求 $path 失败：HTTP ${resp.code} ${text.take(200)}")
            }
            return obj
        }
    }

    /**
     * PoW：找 nonce 让 `sha256(prefix + nonce)` 的前 [difficulty] 个二进制位为 0。
     *
     * 从 app.js 抄的 salt 是 `"a1b2"`。difficulty 通常 16，约 2^16 次哈希、10ms 内完成。
     */
    private fun solvePow(prefix: String, difficulty: Int, maxIter: Int = 1 shl 23): String? {
        val need = difficulty.coerceIn(1, 24)
        val md = MessageDigest.getInstance("SHA-256")
        for (i in 0 until maxIter) {
            val nonce = SALT + i.toString(16)
            val d = md.digest((prefix + nonce).toByteArray())
            if (leadingZeroBits(d) >= need) return nonce
        }
        return null
    }

    /** 统计字节数组开头有多少个 0 位。 */
    private fun leadingZeroBits(bytes: ByteArray): Int {
        var bits = 0
        for (b in bytes) {
            if (bits >= 24) break
            val v = b.toInt() and 0xFF
            if (v == 0) { bits += 8; continue }
            bits += Integer.numberOfLeadingZeros(v) - 24
            break
        }
        return bits
    }

    /**
     * HMAC-SHA256 → 小写 hex。
     *
     * ⚠️ 密钥传的是**字节**，调用方必须已把 hex 串解码过（见类注释）。
     */
    private fun hmacSha256Hex(key: ByteArray, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return OtaCrypto.hex(mac.doFinal(msg.toByteArray()))
    }

    companion object {
        const val HOST = "opusrom.top"
        private const val API = "https://$HOST/api"
        private const val RESOLVE_PATH = "/resolve"
        private const val DEFAULT_DIFFICULTY = 16
        private const val SALT = "a1b2"
        private const val BEHAVIOR_MOVES = 42

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** 浏览器指纹。服务端只看有没有明显异常，不会逐项核对。 */
        private val FINGERPRINT: JsonObject = buildJsonObject {
            put("ua", DESKTOP_UA)
            put("lang", "zh-CN")
            put("platform", "Win32")
            put("timezone", "Asia/Shanghai")
            put("screen", "1920x1080x24")
            put("viewport", "1920x945x1")
            put("cores", 8)
            put("memory", 8)
            put("touch", 0)
            put("plugins", 0)
            put("webdriver", false)
            put("canvas", "0")
            put("cookie", true)
        }

        /** 行为轨迹。同样只看形状是否合理。 */
        private val BEHAVIOR: JsonObject = buildJsonObject {
            put("age", 12000)
            put("moves", BEHAVIOR_MOVES)
            put("touches", 0)
            put("clicks", 3)
            put("keys", 0)
            put("scrolls", 2)
            put("focusChanges", 1)
            put("debugHits", 0)
        }

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private fun JsonObject.str(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun JsonObject.intOrNull(key: String): Int? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

        /**
         * 出错时给用户看的话。
         *
         * 两件事必须做：
         *  1. **绝不把源站返回的 `error` 原文透出去** —— 源站对 IP 封禁时会回一句
         *     带脏话的提示（`ip_banned: true` + 讽刺文案）。原样显示等于把别人的
         *     情绪甩给用户看，所以封禁时走下面的友好分支，只讲事实。
         *  2. 封禁要报**还剩多久**（`remain` 秒），否则用户不知道该等还是要换网。
         *     soft 封禁（remain > 0 且 ip_banned）通常是连续签名失败累计出来的，
         *     等它自然过期即可；非 soft 的 permanent 封禁则要提示换网络。
         */
        private fun JsonObject.errorHint(): String {
            val banned = str("ip_banned").trim().lowercase() == "true"
            val remain = str("remain").trim().toIntOrNull() ?: 0
            val soft = str("soft").trim().lowercase() != "false"

            if (banned || remain > 0) {
                val tail = if (remain > 0) {
                    val h = remain / 3600
                    val m = (remain % 3600) / 60
                    val secs = remain % 60
                    val span = buildString {
                        if (h > 0) append("${h}小时")
                        if (m > 0) append("${m}分钟")
                        if (h == 0 && secs > 0) append("${secs}秒")
                    }.ifBlank { "片刻" }
                    "约 $span"
                } else {
                    "永久"
                }
                return if (soft) {
                    "当前网络已被数据源临时限制，$tail 后自动恢复。可切换网络（Wi-Fi / 移动数据）后重试。"
                } else {
                    "当前网络已被数据源限制（$tail）。请切换网络（Wi-Fi / 移动数据）后重试。"
                }
            }

            // 非封禁的普通错误：源站文案一般还算正常，但仍然做长度收敛
            return str("error").ifBlank { str("message") }.ifBlank { str("reason") }.take(160)
        }
    }
}
