package org.linbaogu.romhub.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密后落盘。
 *
 * 开发者令牌、账号密码都走这里，明文不写进 SharedPreferences，
 * 密钥本身由系统 Keystore 保管（不可导出，卸载应用即销毁）。
 */
object SecureStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "romhub_store_key_v1"
    private const val PREF = "romhub_secure"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(context: Context, key: String, value: String?) {
        val p = prefs(context)
        if (value == null) {
            p.edit().remove(key).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + body.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(body, 0, packed, iv.size, body.size)
            p.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        }.onFailure {
            // Keystore 异常（例如密钥被系统清掉）时降级为不保存，避免直接崩
            p.edit().remove(key).apply()
        }
    }

    fun get(context: Context, key: String): String? {
        val raw = prefs(context).getString(key, null) ?: return null
        return runCatching {
            val packed = Base64.decode(raw, Base64.NO_WRAP)
            if (packed.size <= IV_LEN) return null
            val iv = packed.copyOfRange(0, IV_LEN)
            val body = packed.copyOfRange(IV_LEN, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }
}
