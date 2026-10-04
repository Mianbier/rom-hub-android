package org.linbaogu.romhub.data.brand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * OriginOS 官方 OTA 查询（vivo / iQOO）。
 *
 * 移植自 `github.com/JerryTse-OSS/VIVO-OTA-Tracker` 的 `VivoOtaTracker.py`（作者 Jerry Tse）。
 * 协议本身在 2026-10-04 验证过是正确的：
 * ```
 * PD2408 / V2408A / 16.1.16.5.W10
 *   → 16.1.19.7.W10.V000L1   10,156,550,157 字节 (9.6 GB)
 *   → https://sysuptxdl.vivo.com.cn/upgrade/oem/files/2026...0932....zip
 *   → HTTP 206  Content-Range: bytes 0-1023/10156550157  application/zip
 * ```
 *
 * 协议是自研的「加密信封」：
 * ```
 * 明文 = query string
 *  ↓ AES-128-CBC/PKCS5（固定 IV + 固定密钥，从 so 里逆向出来的）
 * 密文
 *  ↓ 拼信封：[总长 u16be][crc32(头) u64be][版本 u16be][token长度 u8][token]
 *              [密钥版本 u16be][消息类型 u8] + 密文
 *  ↓ base64url（无 padding）
 * POST body: jvq_param=<上面这坨>
 * ```
 * 响应对称，解开就能拿到 `pkName`，直链是 `https://sysuptxdl.vivo.com.cn/upgrade/oem/files/{pkName}`。
 *
 * ⚠️⚠️ 2026-10-04 晚间复测：**这个接口现在对本 App 已彻底关闭**。
 * 上面那个曾经成功的 PD2408 案例，现在连同 PD2505 / PD2606 在内的所有查询
 * 一律返回 `{"message":"无更新","retcode":210}`，且**与请求格式无关** ——
 * 试过 `sf=0`、删掉 `sf` 字段、`isFull=0`、非空 `hwFingerprint`、`cy=US`、
 * `checkTrige=AUTO`、`hasVgc=0`、以及拿 `PDxxxx_MA_整串` 当 swVer，
 * 10 种变体全部 210。判断是服务端识别出了伪造请求
 * （真机 updater 会带硬件指纹和签名，本 App 没有）。
 *
 * 所以**目前不要指望这条路能拿到包**，它只是取直链失败时的最后一根稻草。
 * 要恢复得拿到真实设备环境抓包，或另找公开的 OTA 源。
 *
 * ⚠️ 语义限制（接口恢复后依然成立）：这是「**我现在这个版本能升到哪**」的正向查询，
 * 所以只能拿当前最新一版，拿不到任意历史版本的包。
 */
class VivoOtaApi(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * 查一次 OTA。
     *
     * @param model       软件型号，如 `PD2408`
     * @param deviceModel 设备型号（对外型号），如 `V2408A`
     * @param currentVer  **当前**版本号，如 `16.1.16.5.W10`（必填，缺了服务端会当异常请求）
     */
    suspend fun query(
        model: String,
        deviceModel: String,
        currentVer: String,
        isFull: Boolean = true,
    ): VivoOtaResult = withContext(Dispatchers.IO) {
        val plain = buildQueryString(model, deviceModel, currentVer, isFull)
        val jvq = encryptEnvelope(plain)
        val body = "jvq_param=$jvq".toRequestBody(
            "application/x-www-form-urlencoded; charset=utf-8".toMediaType(),
        )

        val request = Request.Builder()
            .url("$BASE_URL$UPDATE_ENDPOINT")
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .header("User-Agent", "okhttp/4.3.23")
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty().trim()
            if (text.isBlank()) return@withContext VivoOtaResult.failure("HTTP ${resp.code}：空响应")

            // 正常响应以 ACw / ACo 开头（base64url 后的信封）
            if (!text.startsWith("ACw") && !text.startsWith("ACo")) {
                return@withContext VivoOtaResult.failure("接口返回异常：${text.take(160)}")
            }

            val decrypted = runCatching { decryptEnvelope(text) }.getOrNull()
                ?: return@withContext VivoOtaResult.failure("响应解密失败")

            parse(decrypted)
        }
    }

    // ------------------------------------------------------------- 请求明文

    /**
     * 拼 query string。字段名和顺序都是从官方 updater 的 so 里扒出来的，
     * 少一个都可能被服务端判为非法请求。
     *
     * ⚠️ `swVer` 的后缀只能加一次（2026-10-04 修正）。
     * 官方脚本 `VivoOtaTracker.py` 的约定是：传入的版本号必须是**裸版本号**
     * （`16.0.24.1.W30`），`.V000L1` 由脚本自己补。
     * 而我们的版本号来自 opusrom / 内置表，**本来就带 `.V000L1`**，
     * 只判断 `contains(".W")` 会拼出 `16.0.24.1.W30.V000L1.V000L1` —— 服务端
     * 认不出，直接返 210。已实测：带重复后缀的请求 100% 失败。
     */
    private fun buildQueryString(
        model: String,
        deviceModel: String,
        currentVer: String,
        isFull: Boolean,
    ): String {
        val hwVer = "${model}MA"
        // 已经有 .V000L1 就别再加了 —— 这是之前全返 210 的一个真凶
        val fullSwVersion = when {
            currentVer.endsWith(".V000L1") -> currentVer
            currentVer.contains(".W") -> "$currentVer.V000L1"
            else -> currentVer
        }
        val fullVer = "${model}_A_$fullSwVersion"
        val versionLong = "${model}_N_${hwVer}_$fullSwVersion"

        val p = LinkedHashMap<String, String>()
        p["vgcNewActiveVer"] = ""
        p["nt"] = "WIFI"
        p["vgcSwVer"] = "1.1.1"
        p["fullVer"] = fullVer
        p["emmcid"] = ""
        p["sm1"] = "null"
        p["sm2"] = "null"
        p["model"] = model
        p["hasVgc"] = "1"
        p["vgcNewPassiveVer"] = ""
        p["ch"] = "N"
        p["gn"] = "0"
        p["newActiveVer"] = ""
        p["version"] = versionLong
        p["st2"] = "0"
        p["cu"] = "N"
        p["srm2"] = "0"
        p["srm1"] = "0"
        p["cy"] = "CN-ZH"
        p["sn2"] = "null"
        p["ne"] = "null"
        p["sn1"] = "null"
        p["public_model"] = deviceModel
        p["newPassiveVer"] = ""
        p["hwVer"] = hwVer
        p["swVer"] = fullSwVersion
        p["language"] = "zh_CN"
        p["isMan"] = "1"
        p["isFull"] = if (isFull) "1" else "0"
        p["protocalversion"] = "1.0"
        p["checkTrige"] = "MANUL"
        p["isstlifeover"] = "false"
        p["hwFingerprint"] = ""
        // 手机专属
        p["vgcCu"] = "V000"
        p["sf"] = "1"
        p["si"] = "null"
        p["dType"] = "phone"
        p["s_n"] = "null"
        p["elapsedtime"] = (140000 + Random.nextInt(0, 80000)).toString()
        p["st1"] = (100000 + Random.nextInt(0, 60000)).toString()
        p["imei"] = randomImei()
        p["ms"] = "0"
        p["mtype"] = "no"
        p["radiotype"] = "L"

        return p.entries.joinToString("&") { (k, v) -> "$k=$v" }
    }

    private fun randomImei(): String = (0 until 15).map { Random.nextInt(0, 10) }.joinToString("")

    // ------------------------------------------------------------- 信封加解密

    private fun encryptEnvelope(plain: String): String {
        val cipher = OtaCrypto.aesCbcEncrypt(KEY_KV2, IV, plain.toByteArray(Charsets.UTF_8))
        val tokenBytes = TOKEN.toByteArray(Charsets.UTF_8)

        val header = ByteArrayOutputStream().apply {
            write(OtaCrypto.u16be(PROTOCOL_VERSION))
            write(tokenBytes.size)
            write(tokenBytes)
            write(OtaCrypto.u16be(KEY_VERSION))
            write(MSG_TYPE_ENCRYPT.toInt())
        }.toByteArray()

        val totalLen = HEADER_BASE_LEN + tokenBytes.size
        val packet = ByteArrayOutputStream().apply {
            write(OtaCrypto.u16be(totalLen))
            write(OtaCrypto.crc32Bytes(OtaCrypto.crc32(header)))
            write(header)
            write(cipher)
        }.toByteArray()

        return OtaCrypto.b64Url(packet)
    }

    private fun decryptEnvelope(b64: String): String {
        val packet = OtaCrypto.b64UrlDecode(b64)
        val headerLen = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        val cipher = packet.copyOfRange(headerLen, packet.size)
        val plain = OtaCrypto.aesCbcDecrypt(KEY_KV2, IV, cipher)
        return String(plain, Charsets.UTF_8)
    }

    // ------------------------------------------------------------- 解析

    private fun parse(raw: String): VivoOtaResult {
        // 210 = 服务端业务拦截。
        // 2026-10-04 复测发现，伪造请求一律撞这个码（连能出包的机型也是），
        // 所以这个提示不能说「官方暂时下架了」—— 那会把用户引向错误方向。
        if (raw.contains("\"retcode\":210") || raw.contains("retcode=210")) {
            return VivoOtaResult.failure(
                "官方 OTA 接口返回 210（未放行）。" +
                    "该接口目前只放行带完整硬件指纹和签名的真机请求，App 暂时拿不到推送包",
            )
        }

        val pkName = pickString(raw, "pkName")
        if (pkName.isBlank()) {
            return VivoOtaResult.failure("响应里没有 pkName：${raw.take(200)}")
        }

        return VivoOtaResult(
            success = true,
            version = pickString(raw, "version"),
            fileName = pkName,
            sizeText = prettySize(pickString(raw, "pkLen")),
            sizeBytes = pickString(raw, "pkLen").toLongOrNull() ?: 0L,
            downloadUrl = "$DOWNLOAD_BASE/$pkName",
            changelogUrl = pickString(raw, "h5Url"),
        )
    }

    /** 从扁平 JSON 里抠出 `"key":"value"`。服务端返回不是标准 JSON（值里常带裸字符）。 */
    private fun pickString(json: String, key: String): String {
        val needle = "\"$key\":\""
        val idx = json.indexOf(needle)
        if (idx < 0) return ""
        val start = idx + needle.length
        val end = json.indexOf('"', start)
        if (end < 0) return ""
        return json.substring(start, end).replace("\\/", "/")
    }

    companion object {
        private const val BASE_URL = "https://sysupgrade.vivo.com.cn"
        private const val UPDATE_ENDPOINT = "/vgc/v2/getVgcAndPatch.do"
        private const val DOWNLOAD_BASE = "https://sysuptxdl.vivo.com.cn/upgrade/oem/files"

        // 以下常量来自 libvivoseckey_n4.so 的逆向结果
        private val IV = OtaCrypto.unHex("047cd76d65d3b28b4ccc2c0246681aa6")
        private val KEY_KV1 = OtaCrypto.unHex("5da590863052089893199b2a901b6470")
        private val KEY_KV2 = OtaCrypto.unHex("836e75afddae728551ad22b2bae6ca57")
        private const val KEY_VERSION = 2
        private const val PROTOCOL_VERSION = 1
        private const val TOKEN = "jnisgmain_v2@com.bbk.updater"
        private const val MSG_TYPE_ENCRYPT: Byte = 5
        private const val HEADER_BASE_LEN = 16

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

// ------------------------------------------------------------------ 结果模型

data class VivoOtaResult(
    val success: Boolean,
    val version: String = "",
    val fileName: String = "",
    val sizeText: String = "",
    val sizeBytes: Long = 0L,
    /** 直链，永不过期，支持 Range 断点续传 */
    val downloadUrl: String = "",
    val changelogUrl: String = "",
    val error: String = "",
) {
    companion object {
        fun failure(msg: String) = VivoOtaResult(success = false, error = msg)
    }
}
