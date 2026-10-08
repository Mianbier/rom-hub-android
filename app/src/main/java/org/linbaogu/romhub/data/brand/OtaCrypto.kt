package org.linbaogu.romhub.data.brand

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * 厂商 OTA 接口用到的加解密原语。
 *
 * 这里全部是 Java 标准库能做的，没有 native 依赖：
 *  AES/CTR、AES/CBC/PKCS5Padding、RSA/ECB/OAEPWithSHA1AndMGF1Padding、CRC32。
 *
 * 对应的 Python 参考实现（实测跑通）：
 *  · ColorOS   → github.com/006lp/OPlus-Tracker `tomboy_pro.py`
 *  · OriginOS  → github.com/JerryTse-OSS/VIVO-OTA-Tracker `VivoOtaTracker.py`
 */
internal object OtaCrypto {

    private val secureRandom = SecureRandom()

    // ------------------------------------------------------------- 随机 & 编码

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }

    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0xF])
        }
        return out.toString()
    }

    fun unHex(s: String): ByteArray {
        val clean = s.trim()
        require(clean.length % 2 == 0) { "hex 串长度必须是偶数：$clean" }
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) or Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun b64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * 逆 [b64Url]。
     *
     * vivo 的响应体在 JS/Java 侧是先 URLDecode、再把 `-`→`+`、`_`→`/`，
     * 所以这里容忍 percent-encoding，并补齐 padding。
     */
    fun b64UrlDecode(s: String): ByteArray {
        var t = java.net.URLDecoder.decode(s.trim(), "UTF-8")
        t = t.replace('-', '+').replace('_', '/')
        val pad = (4 - t.length % 4) % 4
        if (pad > 0) t = t.padEnd(t.length + pad, '=')
        return Base64.getDecoder().decode(t)
    }

    /** 大端 u16，避免用已废弃的无参 `toBigEndian()`。 */
    fun u16be(v: Int): ByteArray = byteArrayOf(((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

    private const val HEX = "0123456789abcdef"

    // ------------------------------------------------------------- AES

    /** AES-CTR/NoPadding。ColorOS 请求与响应都用它。 */
    fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    fun aesCtrDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    /** AES-CBC/PKCS5Padding。OriginOS 请求与响应都用它。 */
    fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    // ------------------------------------------------------------- RSA

    /**
     * RSA-OAEP(SHA-1, MGF1-SHA1)。
     *
     * ⚠️ Java 默认的 OAEP 用 **MGF1-SHA-256**，和厂商不一致，必须显式指定 MGF1-SHA1，
     * 否则密文长度对不上、解出来是垃圾。
     */
    fun rsaOaepSha1Encrypt(publicKeyPem: String, plain: ByteArray): ByteArray {
        val key = parseRsaPublicKey(publicKeyPem)
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding")
        c.init(
            Cipher.ENCRYPT_MODE,
            key,
            OAEPParameterSpec(
                "SHA-1",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT,
            ),
        )
        return c.doFinal(plain)
    }

    fun parseRsaPublicKey(pem: String): java.security.interfaces.RSAPublicKey {
        val body = pem
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filterNot { it.isWhitespace() }
        val der = Base64.getDecoder().decode(body)
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(der)) as java.security.interfaces.RSAPublicKey
    }

    // ------------------------------------------------------------- 杂项

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun crc32(data: ByteArray): Long = CRC32().apply { update(data) }.value

    /**
     * CRC32 的 8 字节大端表示 —— OriginOS 协议包头里占 8 字节。
     * 注意是 **无符号** 值左移/右移，Python 那边写的是 `& 0xFFFFFFFF`。
     */
    fun crc32Bytes(value: Long): ByteArray {
        val v = value and 0xFFFFFFFFL
        return ByteArray(8) { i -> ((v ushr (8 * (7 - i))) and 0xFF).toByte() }
    }

    /** WHATWG `URLSearchParams.toString()` 的编码规则：保留 `*-._`，空格转 `+`。 */
    fun formEncode(s: String): String {
        val sb = StringBuilder()
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            when {
                c in 'a'.code..'z'.code -> sb.append(c.toChar())
                c in 'A'.code..'Z'.code -> sb.append(c.toChar())
                c in '0'.code..'9'.code -> sb.append(c.toChar())
                c == '*'.code || c == '-'.code || c == '.'.code || c == '_'.code -> sb.append(c.toChar())
                c == ' '.code -> sb.append('+')
                else -> {
                    sb.append('%')
                    sb.append(HEX[c ushr 4])
                    sb.append(HEX[c and 0xF])
                }
            }
        }
        return sb.toString()
    }

    fun formEncodeComponent(s: String): String = formEncode(s)
}
