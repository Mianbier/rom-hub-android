package org.linbaogu.romhub.data.brand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ColorOS 官方 OTA 查询（OPPO / 一加 / 真我 Realme）。
 *
 * 移植自 `github.com/006lp/OPlus-Tracker` 的 `tomboy_pro.py`（作者 Jerry Tse），
 * **2026-10-04 已用真实机型实测通过**：
 * ```
 * PJZ110_11.C.84_1840_202601060309 / cn
 *   → PJZ110_16.0.10.501(CN01)
 *   → https://gauss-compota-c-cn.allawnfs.com/.../xxx.zip?sign=..&Expires=..
 *   → HTTP 206  8975091784 字节  application/zip
 * ```
 *
 * 协议要点：
 *  1. 随机 32 字节 AES 密钥 + 16 字节 IV
 *  2. 请求 JSON 用 **AES-CTR** 加密
 *  3. 这把 AES 密钥 base64 后再用 **RSA-OAEP(SHA-1)** 加密，放进 `protectedKey` 请求头
 *  4. 请求体是 `{"params": "<又一层 JSON 字符串>"}` —— params 是**字符串**不是对象
 *  5. 响应的 `body` 字段同样是一段 JSON 字符串，里面才有 cipher/iv
 *
 * 直链有两类：
 *  - `downloadCheck?…` → 需再发一次 GET（带 `userId` 头、不跟随重定向）读 302 Location
 *  - `gauss-*` 阿里云签名链 → 直接就是直链，但**约 24 小时过期**
 * `download?…` 型需要设备密钥盒（keybox.xml），App 拿不到，不支持。
 */
class ColorOtaApi(
    private val client: OkHttpClient = defaultClient(),
) {

    // ------------------------------------------------------------- 对外

    /**
     * 查一次 OTA。
     *
     * @param model      机型代号，如 `PJZ110`（不带版本后缀）
     * @param otaVersion 完整 OTA 版本串，如 `PJZ110_11.C.84_1840_202601060309`；
     *                   传 `PJZ110_11.C` 这类前缀也行，内部会自动补全
     * @param region     区域键，见 [Region]
     */
    suspend fun query(
        model: String,
        otaVersion: String,
        region: Region = Region.CN,
        resolveLinks: Boolean = true,
    ): ColorOtaResult = withContext(Dispatchers.IO) {
        val cfg = region.config
        val publicKeyPem = region.publicKey
        val fullVersion = completeVersion(model, otaVersion)

        val aesKey = OtaCrypto.randomBytes(32)
        val iv = OtaCrypto.randomBytes(16)
        val deviceId = randomUpperAlnum(64)

        val protectedKey = OtaCrypto.b64(
            OtaCrypto.rsaOaepSha1Encrypt(publicKeyPem, OtaCrypto.b64(aesKey).toByteArray())
        )

        val plain = buildJsonObject {
            put("mode", "0")
            put("time", System.currentTimeMillis())
            put("isRooted", "0")
            put("isLocked", true)
            put("type", "0")
            put("deviceId", "0".repeat(64))
            put("isSuportPki", false)
            put("opex", buildJsonObject { put("check", true) })
        }

        val cipherText = OtaCrypto.aesCtr(aesKey, iv, plain.toString().toByteArray())
        val bodyJson = buildJsonObject {
            put(
                "params",
                buildJsonObject {
                    put("cipher", OtaCrypto.b64(cipherText))
                    put("iv", OtaCrypto.b64(iv))
                }.toString(),
            )
        }

        val request = Request.Builder()
            .url("https://${cfg.host}/update/v3")
            .post(bodyJson.toString().toRequestBody(JSON_MT))
            .apply {
                header("language", cfg.language)
                header("newLanguage", cfg.language)
                header("androidVersion", "unknown")
                header("colorOSVersion", "unknown")
                header("romVersion", "unknown")
                header("infVersion", "1")
                header("otaVersion", fullVersion)
                header("model", model)
                header("mode", "0")
                header("nvCarrier", cfg.carrierId)
                header("pipelineKey", "ALLNET")
                header("operator", "ALLNET")
                header("companyId", "")
                header("version", "2")
                header("deviceId", deviceId)
                header("Content-Type", "application/json; charset=utf-8")
                // 协商版本号 = 公钥版本号 + 未来时间戳
                header(
                    "protectedKey",
                    buildJsonObject {
                        put(
                            "SCENE_1",
                            buildJsonObject {
                                put("protectedKey", protectedKey)
                                // 协议里这是一个「未来时间戳」：当前 epoch 纳秒 + 1 天
                                put(
                                    "version",
                                    (System.currentTimeMillis() * 1_000_000L + 86_400_000_000_000L)
                                        .toString(),
                                )
                                put("negotiationVersion", cfg.publicKeyVersion)
                            },
                        )
                    }.toString(),
                )
            }
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root = runCatching { PLAIN_JSON.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: return@withContext ColorOtaResult.failure("HTTP ${resp.code}：响应不是 JSON")

            val code = root["responseCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (code != 200) {
                val err = root["error"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                return@withContext ColorOtaResult.failure(
                    "官方接口返回 $code${if (err.isNotBlank()) "：$err" else ""}",
                )
            }

            val bodyStr = root["body"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            if (bodyStr.isBlank()) return@withContext ColorOtaResult.failure("响应里没有 body")

            val inner = runCatching { PLAIN_JSON.parseToJsonElement(bodyStr) as? JsonObject }.getOrNull()
                ?: return@withContext ColorOtaResult.failure("body 不是 JSON")

            val rCipher = inner["cipher"]?.jsonPrimitive?.contentOrNull
                ?.let { b64Decode(it) } ?: return@withContext ColorOtaResult.failure("body 缺 cipher")
            val rIv = inner["iv"]?.jsonPrimitive?.contentOrNull
                ?.let { b64Decode(it) } ?: return@withContext ColorOtaResult.failure("body 缺 iv")

            val decrypted = OtaCrypto.aesCtrDecrypt(aesKey, rIv, rCipher)
            val payload = runCatching {
                PLAIN_JSON.parseToJsonElement(String(decrypted)) as? JsonObject
            }.getOrNull() ?: return@withContext ColorOtaResult.failure("解密后不是 JSON")

            parsePayload(payload, resolveLinks)
        }
    }

    // ------------------------------------------------------------- 解析

    private fun parsePayload(body: JsonObject, resolveLinks: Boolean): ColorOtaResult {
        val components = mutableListOf<ColorOtaPackage>()

        (body["components"] as? kotlinx.serialization.json.JsonArray)?.forEach { el ->
            val comp = el as? JsonObject ?: return@forEach
            val packets = comp["componentPackets"] as? JsonObject ?: return@forEach
            val manual = fixGaussUrl(packets.str("manualUrl"))
            val auto = fixGaussUrl(packets.str("url"))

            var finalLink = manual
            var expires = 0L
            if (resolveLinks && manual.contains("downloadCheck")) {
                val resolved = runCatching { followDownloadCheck(manual) }.getOrNull()
                if (!resolved.isNullOrBlank()) {
                    finalLink = resolved
                    expires = extractExpires(resolved)
                }
            }

            components += ColorOtaPackage(
                name = comp.str("componentName").ifBlank { "Unknown" },
                version = comp.str("componentVersion").ifBlank { "Unknown" },
                link = finalLink,
                originalLink = manual,
                autoLink = auto,
                size = packets.str("size"),
                md5 = packets.str("md5"),
                expiresAt = expires,
            )
        }

        val desc = body["description"] as? JsonObject
        return ColorOtaResult(
            success = true,
            versionName = body.str("realVersionName").ifBlank { body.str("versionName") },
            otaVersion = body.str("realOtaVersion").ifBlank { body.str("otaVersion") },
            securityPatch = body.str("securityPatch"),
            changelog = fixGaussUrl(desc?.str("panelUrl").orEmpty()),
            publishedAt = body["publishedTime"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            packages = components,
        )
    }

    /**
     * 解析 `downloadCheck?…` 动态链：带 `userId` 头发请求、**不跟随重定向**，
     * 302 的 `Location` 才是真直链。
     */
    private fun followDownloadCheck(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("userId", "oplus-ota|00000001")
            .header("Range", "bytes=0-")
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 302) return resp.header("Location")
            return null
        }
    }

    /** 阿里云签名链的过期时间戳（秒）。0 = 未知/不过期。 */
    private fun extractExpires(url: String): Long {
        Regex("(?:^|[?&])(?:Expires|x-oss-expires)=(\\d+)").find(url)
            ?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return 0L
    }

    /** 站点把 auto 域换成 manual 域，manual 域的链更稳定。 */
    private fun fixGaussUrl(url: String): String = url
        .replace("https://gauss-otacostauto-cn.allawnfs.com/", "https://gauss-componentotacostmanual-cn.allawnfs.com/")

    // ------------------------------------------------------------- 工具

    /**
     * 补全版本串。
     *
     * 完整形态是 `{model}_{版本号}_{build}_{时间戳}`，例如
     * `PJZ110_11.C.84_1840_202601060309`（实测这串能查到 16.0.10.501(CN01)）。
     *
     * 注意**版本号本身是用 `.` 分隔的**（`11.C.84`、`16.0.10.501(CN01)`），
     * 只有段与段之间才是 `_`。所以不能简单数 `_` 的个数——
     * 只给版本号（`PJZ110_16.0.10.501(CN01)`）会被误判成「只有 1 段」。
     *
     * 正确判据：剥掉 `{model}_` 前缀后，看剩下的是「版本号」还是「版本号_build_时间戳」。
     */
    fun completeVersion(model: String, otaVersion: String): String {
        val v = otaVersion.trim()
        if (v.isBlank()) return model + "_01_0001_197001010000"

        val prefix = model + "_"
        if (!v.startsWith(prefix)) {
            // 只给了版本号（`12.1` / `16.0.10.501(CN01)`），补上 model 与尾段
            return prefix + v + "_0001_197001010000"
        }

        val rest = v.substring(prefix.length)
        // rest 里还有下划线说明 build 与时间戳都在，是完整串
        return if (rest.contains("_")) v else prefix + rest + "_0001_197001010000"
    }

    private fun randomUpperAlnum(n: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder(n)
        repeat(n) { sb.append(chars[OtaCrypto.randomBytes(1)[0].toInt().and(0x7F) % chars.length]) }
        return sb.toString()
    }

    companion object {
        private val JSON_MT = "application/json; charset=utf-8".toMediaType()

        /** 厂商接口是 camelCase，不能用 Api.kt 里那个 SnakeCase 的 RomJson。 */
        private val PLAIN_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private fun JsonObject.str(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}

// ------------------------------------------------------------------ 区域

/**
 * 区域配置。服务器地址与公钥来自 `OPlus-Tracker/config.py`（原样照搬，未改动）。
 */
enum class Region(
    val host: String,
    val language: String,
    val carrierId: String,
    val publicKeyVersion: String,
    val publicKey: String,
) {
    CN(
        "component-ota-cn.allawntech.com", "zh-CN", "10010111", "1615879139745",
        """-----BEGIN RSA PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApXYGXQpNL7gmMzzvajHa
        oZIHQQvBc2cOEhJc7/tsaO4sT0unoQnwQKfNQCuv7qC1Nu32eCLuewe9LSYhDXr9
        KSBWjOcCFXVXteLO9WCaAh5hwnUoP/5/Wz0jJwBA+yqs3AaGLA9wJ0+B2lB1vLE4
        FZNE7exUfwUc03fJxHG9nCLKjIZlrnAAHjRCd8mpnADwfkCEIPIGhnwq7pdkbamZ
        coZfZud1+fPsELviB9u447C6bKnTU4AaMcR9Y2/uI6TJUTcgyCp+ilgU0JxemrSI
        PFk3jbCbzamQ6Shkw/jDRzYoXpBRg/2QDkbq+j3ljInu0RHDfOeXf3VBfHSnQ66H
        CwIDAQAB
        -----END RSA PUBLIC KEY-----""",
    ),
    EU(
        "component-ota-eu.allawnos.com", "en-GB", "01000100", "1615897067573",
        """-----BEGIN RSA PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh8/EThsK3f0WyyPgrtXb
        /D0Xni6UZNppaQHUqHWo976cybl92VxmehE0ISObnxERaOtrlYmTPIxkVC9MMueD
        vTwZ1l0KxevZVKU0sJRxNR9AFcw6D7k9fPzzpNJmhSlhpNbt3BEepdgibdRZbacF
        3NWy3ejOYWHgxC+I/Vj1v7QU5gD+1OhgWeRDcwuV4nGY1ln2lvkRj8EiJYXfkSq/
        wUI5AvPdNXdEqwou4FBcf6mD84G8pKDyNTQwwuk9lvFlcq4mRqgYaFg9DAgpDgqV
        K4NTJWM7tQS1GZuRA6PhupfDqnQExyBFhzCefHkEhcFywNyxlPe953NWLFWwbGvF
        KwIDAQAB
        -----END RSA PUBLIC KEY-----""",
    ),
    ;
}

/** enum 不允许额外的实例属性，所以用顶层扩展。 */
internal val Region.config: RegionConfig
    get() = RegionConfig(host, language, carrierId, publicKeyVersion, publicKey)

internal data class RegionConfig(
    val host: String,
    val language: String,
    val carrierId: String,
    val publicKeyVersion: String,
    val publicKey: String,
)

// ------------------------------------------------------------------ 结果模型

/** 一个组件（全量包 / 增量包）的下载信息。 */
data class ColorOtaPackage(
    val name: String,
    val version: String,
    /** 可直接下载的最终链（阿里云签名，约 24h 有效） */
    val link: String,
    /** 原始链，含 `downloadCheck?` 时还需再解析一次 */
    val originalLink: String,
    val autoLink: String,
    val size: String,
    val md5: String,
    /** 过期时间戳（秒）；0 表示未知或不过期 */
    val expiresAt: Long,
) {
    val isExpired: Boolean
        get() = expiresAt > 0 && System.currentTimeMillis() / 1000 > expiresAt

    /** 把 "12345678901" 这种字节数变成 "11.3 GB"。 */
    val sizeText: String get() = prettySize(size)
}

data class ColorOtaResult(
    val success: Boolean,
    val versionName: String = "",
    val otaVersion: String = "",
    val securityPatch: String = "",
    val changelog: String = "",
    val publishedAt: Long = 0L,
    val packages: List<ColorOtaPackage> = emptyList(),
    val error: String = "",
) {
    /** 全量包优先：名字里带 system / base / full 的就是它。 */
    val fullPackage: ColorOtaPackage?
        get() = packages.firstOrNull {
            val n = it.name.lowercase()
            n.contains("system") || n.contains("base") || n.contains("full")
        } ?: packages.firstOrNull()

    companion object {
        fun failure(msg: String) = ColorOtaResult(success = false, error = msg)
    }
}

internal fun JsonPrimitive.contentOrNullSafe(): String =
    if (this is kotlinx.serialization.json.JsonNull) "" else content

/** ColorOS 的 cipher / iv 都是标准 base64（不是 hex）。 */
private fun b64Decode(s: String): ByteArray = java.util.Base64.getDecoder().decode(s.trim())

/** "12345678901" → "11.3 GB"。非数字原样返回。 */
fun prettySize(raw: String): String {
    val n = raw.trim().toLongOrNull() ?: return raw
    if (n <= 0) return raw
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = n.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$n B" else String.format("%.1f %s", v, units[i])
}
