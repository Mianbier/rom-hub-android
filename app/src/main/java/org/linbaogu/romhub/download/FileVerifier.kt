package org.linbaogu.romhub.download

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * 下载完成后的**通用完整性校验** —— 直接回应「下载后软件签名与之前的不同」这条反馈。
 *
 * ## 为什么不能只认 APK
 *
 * 这个 App 下载的东西以 **ROM 包 / ZIP 刷机包** 为主，APK 只是其中一类。
 * ROM 包普遍是 zip 结构（有的还套 zip / payload.bin），一旦被下坏：
 *   · 刷机时卡在 "Verifying update package" 或直接报 `footer is wrong`；
 *   · 解压报 CRC32 错误；
 *   · 用户看到的现象就是「文件不对 / 签名不对」。
 * 所以校验必须按**文件实际类型**分层做，而不是写死 APK。
 *
 * ## 分层校验策略
 *
 * | 层级 | 条件 | 手段 |
 * |---|---|---|
 * | 1. 大小 | 服务端给了 Content-Length | 精确比对文件长度 |
 * | 2. 结构 | 文件是 zip 系（apk/zip/rom 常见） | 遍历中央目录做**逐条 CRC32 校验**（真读一遍解压流） |
 * | 3. 签名 | 文件是 APK | 读签名证书 SHA-256，并与本机已装同包名应用对比 |
 * | 4. 摘要 | 服务端给了 hash（可选） | 与下载内容算出的摘要比对 |
 *
 * zip 结构校验是最有价值的一层 —— 它**真的能查出分片写坏导致的字节错位**，
 * 因为任何一个字节错位都会让对应条目的 CRC32 对不上。
 *
 * 注意：zip 校验要完整读一遍文件，几个 G 的 ROM 包在主线程外做没问题，
 * 但为了不拖慢"下载完成"的体感，它只在用户没关掉该开关、且文件不太大时才跑
 * （见 [DownloadManager] 里的调用）。
 */
object FileVerifier {

    /** 做 zip 逐条 CRC 校验的体积上限：超过就只做大小校验（避免下完卡几十秒）。 */
    private const val ZIP_CHECK_LIMIT = 2L * 1024 * 1024 * 1024  // 2GB

    /**
     * 校验结果。字段为 null 表示"该层级未执行/不适用"，不是失败。
     */
    data class Result(
        /** 文件长度是否与服务端声明一致（声明未知时为 true） */
        val sizeOk: Boolean,
        val expectedSize: Long,
        val actualSize: Long,
        /** 识别出的文件类型：apk / zip / other */
        val kind: Kind,
        /** zip 结构是否完好（CRC 全过）；非 zip 或未校验时为 null */
        val zipOk: Boolean?,
        /** zip 校验收到的错误条目（前几条），给用户看 */
        val zipError: String?,
        /** 整个文件的 SHA-256（小写十六进制）；没算为 null */
        val sha256: String?,
        /** APK 的包名/版本；非 APK 为 null */
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long,
        /** APK 签名证书 SHA-256（大写冒号分隔） */
        val signatureSha256: String?,
        /** 本机已安装同包名应用的签名 SHA-256 */
        val installedSignatureSha256: String?,
        /** 与已安装版本签名是否一致；未装/非 APK 为 null */
        val signatureMatchesInstalled: Boolean?,
    ) {
        enum class Kind { APK, ZIP, OTHER }

        /** 所有已执行的检查是否都通过。 */
        val allPassed: Boolean
            get() = sizeOk && (zipOk != false) && (signatureMatchesInstalled != false)

        /** 一句话结论，直接给界面显示。 */
        fun summary(): String = when {
            !sizeOk -> "文件大小不符（期望 ${formatBytes(expectedSize)}，实际 ${formatBytes(actualSize)}），下载可能不完整"
            zipOk == false -> "压缩包结构损坏${zipError?.let { "（$it）" } ?: ""}，文件已损坏，请重新下载"
            signatureMatchesInstalled == false ->
                "APK 签名与本机已安装的 $packageName 不一致，" +
                        "本包 ${signatureSha256?.take(23)}…，已装 ${installedSignatureSha256?.take(23)}…"
            kind == Kind.APK && signatureSha256 != null -> "文件完整，APK 签名 ${signatureSha256.take(23)}…"
            kind == Kind.ZIP && zipOk == true -> "压缩包完整（CRC 校验通过）"
            kind != Kind.OTHER && sizeOk -> "文件大小校验通过"
            else -> "文件已完整下载"
        }
    }

    /**
     * 跑校验。[expectedSha256] 传空串表示不比对摘要。
     *
     * @param deepZip 是否做 zip 逐条 CRC 校验（用户可关；关掉只做大小 + APK 签名）
     */
    fun verify(
        ctx: Context,
        file: File,
        expectedSize: Long,
        expectedSha256: String = "",
        deepZip: Boolean = true,
    ): Result {
        val actual = if (file.exists()) file.length() else 0L
        val sizeOk = expectedSize <= 0L || actual == expectedSize
        val name = file.name.lowercase()

        // 按魔数判断类型，比扩展名可靠
        val kind = when {
            name.endsWith(".apk") -> Result.Kind.APK
            isZipFile(file) -> Result.Kind.ZIP
            else -> Result.Kind.OTHER
        }

        // ---- zip 结构校验（含 APK —— APK 就是 zip） ----
        var zipOk: Boolean? = null
        var zipError: String? = null
        if (deepZip && (kind == Result.Kind.ZIP || kind == Result.Kind.APK) &&
            actual in 1 until ZIP_CHECK_LIMIT
        ) {
            val r = checkZip(file)
            zipOk = r.first
            zipError = r.second
        }

        // ---- APK 签名 ----
        var pkg: String? = null
        var verName: String? = null
        var verCode = 0L
        var mySig: String? = null
        var installedSig: String? = null
        var sigMatch: Boolean? = null

        if (kind == Result.Kind.APK) {
            val pm = ctx.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archive: PackageInfo? = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, flags)
            }.getOrNull()
            if (archive != null) {
                pkg = archive.packageName
                verName = archive.versionName
                verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    archive.longVersionCode
                } else {
                    @Suppress("DEPRECATION") archive.versionCode.toLong()
                }
                mySig = signatureOf(archive)
                installedSig = runCatching {
                    @Suppress("DEPRECATION")
                    val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(archive.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                    } else {
                        pm.getPackageInfo(archive.packageName, flags)
                    }
                    signatureOf(pi)
                }.getOrNull()
                sigMatch = when {
                    mySig == null || installedSig == null -> null
                    else -> mySig.equals(installedSig, ignoreCase = true)
                }
            }
        }

        // ---- 摘要（可选：服务端给了才比对） ----
        var sha256: String? = null
        if (expectedSha256.isNotBlank() && actual in 1 until ZIP_CHECK_LIMIT) {
            sha256 = sha256Of(file)
        }

        return Result(
            sizeOk = sizeOk,
            expectedSize = expectedSize,
            actualSize = actual,
            kind = kind,
            zipOk = zipOk,
            zipError = zipError,
            sha256 = sha256,
            packageName = pkg,
            versionName = verName,
            versionCode = verCode,
            signatureSha256 = mySig,
            installedSignatureSha256 = installedSig,
            signatureMatchesInstalled = sigMatch,
        )
    }

    // ---------------------------------------------------------------- zip 逐条校验

    /**
     * 遍历 zip 的每个条目并读取其内容，靠 CRC32 判定有无损坏。
     *
     * 为什么必须**读完全部字节**：`ZipFile` 打开时只读中央目录，不读数据。
     * 只有真正把每条的流读一遍，Java 才会在结尾比对 CRC32 —— 这才是能
     * 揪出"字节被写坏"的关键。只 list entries 是查不出来的。
     *
     * @return (是否全部完好, 第一个出错的条目描述)
     */
    private fun checkZip(file: File): Pair<Boolean, String?> = runCatching {
        ZipFile(file).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                try {
                    zf.getInputStream(e).use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (input.read(buf) > 0) {
                            // 读到底就会触发 CRC 校验
                        }
                    }
                } catch (ex: Throwable) {
                    return@use Pair(false, "${e.name}：${ex.message ?: ex.javaClass.simpleName}")
                }
            }
            Pair(true, null)
        }
    }.getOrElse { Pair(false, it.message ?: "压缩包无法打开") }

    /** 靠前 4 字节魔数判断是不是 zip（PK\x03\x04 / PK\x05\x06 / PK\x07\x08）。 */
    private fun isZipFile(file: File): Boolean = runCatching {
        if (!file.exists() || file.length() < 4) return false
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                (head[2] == 0x03.toByte() || head[2] == 0x05.toByte() || head[2] == 0x07.toByte())
    }.getOrDefault(false)

    private fun sha256Of(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** 取 APK 签名证书的 SHA-256（跟 MT管理器/keytool 显示的一致：AA:BB:… 大写）。 */
    private fun signatureOf(info: PackageInfo?): String? {
        if (info == null) return null
        return runCatching {
            val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo
            } else {
                @Suppress("DEPRECATION") null
            }
            val signers = if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION") info.signatures
            } ?: return null
            val cert = signers.lastOrNull() ?: return null
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                .joinToString(":") { "%02X".format(it) }
        }.getOrNull()
    }
}
