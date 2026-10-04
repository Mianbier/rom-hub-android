/*
 * 云盘文件浏览 —— 由云析（CYQawa/YunX，AGPL-3.0）移植而来。
 *
 * 云析为 7 家网盘各写了一个 ViewModel（每家 ~680 行，逻辑高度重复），
 * 这里收拢成一个 [CloudApi] 动作接口 + 一个统一的浏览 ViewModel：
 * 各平台的差异（鉴权方式、fid 语义、方法名、有效期编码）在各自的适配器里收敛。
 */

package org.linbaogu.romhub.cloud

import android.content.Context
import org.linbaogu.romhub.pan.BaiduApi
import org.linbaogu.romhub.pan.BaiduConstants
import org.linbaogu.romhub.pan.C139Api
import org.linbaogu.romhub.pan.C139Constants
import org.linbaogu.romhub.pan.Pan115Api
import org.linbaogu.romhub.pan.Pan123Api
import org.linbaogu.romhub.pan.QuarkApi
import org.linbaogu.romhub.pan.QuarkCdn
import org.linbaogu.romhub.pan.QuarkConstants
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.pan.UCApi
import org.linbaogu.romhub.pan.UCConstants
import org.linbaogu.romhub.pan.XunleiApi
import org.linbaogu.romhub.pan.XunleiConstants
import org.linbaogu.romhub.pan.model.DownloadLink
import org.linbaogu.romhub.pan.model.QuotaInfo
import org.linbaogu.romhub.pan.model.ShareExpire
import org.linbaogu.romhub.pan.model.ShareFile
import org.linbaogu.romhub.pan.model.ShareInfo
import java.net.URLEncoder

/**
 * 一家网盘的「个人盘文件管理」能力。
 *
 * 云析的 7 个 CloudViewModel 里做的事，抽象成这一组方法。
 * 各家差异（鉴权形态、根目录 id、有效期编码）在实现类里收敛。
 */
interface CloudApi {

    /** 展示名对应的平台 */
    val platform: SharePlatform

    /** 根目录 id：多数平台是 "0"，115/139/迅雷是空串，百度是 "/" */
    val rootId: String

    /** 分享提取码规则（见 [PasscodeMode]；各平台文档要求不同） */
    val passcodeMode: PasscodeMode

    /**
     * 进入子目录时，要传给 [list] 的目录标识。
     *
     * 绝大多数平台 `fid` 就是目录 id，直接返回即可（默认实现）。
     *
     * ⚠️ 百度必须覆盖：[BaiduApi.listCloudFiles] 的 `dir` 参数要的是**绝对路径**
     * （`/子目录名`），而百度返回的 `fid` 是 `fs_id`（一串数字），拿它当 `dir`
     * 传过去服务端会返回**空列表** —— 表现为「根目录能列、点进去全空」
     * （用户实测）。百度把绝对路径存在 `fidToken` 里，所以这里取 `fidToken`。
     */
    fun dirIdOf(file: ShareFile): String = file.fid

    /**
     * 进子目录前再给一次机会：凭据不齐时返回一句话解释（界面直接显示），
     * 让「登录成功但用不了」这种问题不用抓日志也能看出卡在哪。
     * 默认没毛病返回 null。
     */
    fun credentialWarning(credential: String): String? = null

    /** 有效期档位（115 是 101..105，其余用中性码） */
    val expireOptions: List<Pair<String, Int>> get() = defaultExpireOptions

    /** 列目录 */
    suspend fun list(dirId: String, credential: String): List<ShareFile>

    /** 取下载直链 */
    suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink?

    /** 重命名；返回是否成功 */
    suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean

    /** 移动到目标目录 */
    suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean

    /** 删除 */
    suspend fun delete(files: List<ShareFile>, credential: String): Boolean

    /**
     * 新建文件夹。
     * @return 新目录的 id（成功）或 null（失败）。百度只返回布尔，成功时回传父路径 + 名称拼出的新路径。
     */
    suspend fun createFolder(name: String, parentId: String, credential: String): String?

    /** 创建分享并取回链接 */
    suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo?

    /** 下载请求头（Cookie + UA + Referer 等；各家防盗链要求不同） */
    fun downloadHeaders(credential: String): Map<String, String> = emptyMap()

    /**
     * 直链后处理：夸克要过一遍 CDN 节点挑选。
     * 默认原样返回。
     */
    suspend fun refineDownloadUrl(rawUrl: String, credential: String): String = rawUrl

    /** 空间用量（能查就查，查不到返回 null，界面不显示） */
    suspend fun quota(credential: String): QuotaInfo? = null

    /**
     * 全盘搜索（按文件名，跨目录）。
     *
     * 云析没这个能力，是 ROM Hub 新加的：7 家网盘的文件列表接口本身大多支持
     * 关键词搜索，只是各家参数名不同，所以在适配器里各写各的。
     * 不支持（或实现起来需要额外鉴权）的平台返回 null → 界面退回「当前目录内过滤」。
     *
     * @param keyword 关键词（空串表示不用搜）
     * @return 命中的文件列表；不支持搜索时返回 null
     */
    suspend fun search(keyword: String, credential: String): List<ShareFile>? = null

    /** 这家网盘支不支持全盘搜索（界面据此决定搜索框的提示文案）。 */
    val supportsSearch: Boolean get() = false

    /**
     * 上传本地文件到这个目录。
     *
     * 各家的上传协议差异极大（123/夸克是预签名直传、115 要分片、百度要 MD5 秒传+分片），
     * 所以这里是可空实现：默认返回 false（= 不支持），各适配器按自家能力覆盖。
     *
     * @param dirId 目标目录 id
     * @param fileName 上传后的文件名
     * @param size 文件字节数（-1 表示未知）
     * @param openStream 每次调用都要返回一个**全新的**输入流（重试时会再调一次）
     * @param onProgress (已传字节, 总字节)
     * @return 是否上传成功
     */
    suspend fun upload(
        dirId: String,
        fileName: String,
        size: Long,
        openStream: () -> java.io.InputStream,
        credential: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Boolean = false

    /** 这家网盘支不支持上传本地文件。 */
    val supportsUpload: Boolean get() = false
}

/** 提取码规则：各平台文档要求不同，必须区分（不能一律给"无提取码/设置提取码"二选一）。 */
enum class PasscodeMode {
    /** 可选：夸克 / UC / 123 */
    OPTIONAL,

    /** 必填 4 位：百度 */
    REQUIRED,

    /** 必填但可留空由服务端生成：迅雷 */
    REQUIRED_OR_AUTO,

    /** 服务端自动生成、不可设置：139 */
    SERVER_GENERATED,
}

/** 默认有效期档位（六平台通用；115 用自己的 101..105） */
val defaultExpireOptions = listOf(
    "永久有效" to ShareExpire.FOREVER,
    "1 天" to ShareExpire.ONE_DAY,
    "7 天" to ShareExpire.SEVEN_DAYS,
    "30 天" to ShareExpire.THIRTY_DAYS,
)

/** 各平台的下载 UA / Referer 常量集中在这里，避免实现类里散落全限定名 */
private object DlHeaders {
    fun quark(cookie: String) = mapOf(
        "Cookie" to cookie,
        "User-Agent" to QuarkConstants.API_USER_AGENT,
        "Referer" to QuarkConstants.DOWNLOAD_REFERER,
    )

    fun uc(cookie: String) = mapOf(
        "Cookie" to cookie,
        "Referer" to UCConstants.DOWNLOAD_REFERER,
    )

    fun baidu(cookie: String) = mapOf(
        "Cookie" to cookie,
        "User-Agent" to BaiduConstants.UA_NETDISK,
        "Referer" to "https://pan.baidu.com/disk/home",
    )

    fun c139(cookie: String) = mapOf(
        "Cookie" to cookie,
        "Referer" to "https://yun.139.com/",
    )
}

// ---------------------------------------------------------------- 夸克

class QuarkCloudApi(private val api: QuarkApi) : CloudApi {
    override val platform = SharePlatform.QUARK
    override val rootId: String get() = "0"
    override val passcodeMode = PasscodeMode.OPTIONAL

    override suspend fun list(dirId: String, credential: String): List<ShareFile> =
        api.listCloudFiles(dirId, credential) ?: emptyList()

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? =
        api.getDownloadLink(file.fid, credential)

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean =
        api.renameFile(file.fid, newName, credential)

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean =
        files.all { api.moveFile(it.fid, toDirId, credential) != null }

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean =
        files.all { api.deleteFile(it.fid, credential) != null }

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? =
        api.createFolder(name, parentId, credential)

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? {
        // 夸克 / UC 的 expired_type 取值恰好等于中性码本身，原值直传
        val shareId = api.createShare(
            fidList = files.map { it.fid },
            title = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件",
            urlType = urlType,
            passcode = passcode,
            expiredType = expiredType,
            cookie = credential,
        ) ?: return null
        return api.getShareInfo(shareId, credential)
    }

    override fun downloadHeaders(credential: String) = DlHeaders.quark(credential)

    override suspend fun refineDownloadUrl(rawUrl: String, credential: String): String =
        QuarkCdn.fastest(rawUrl, credential)

    override val supportsSearch = true
    override suspend fun search(keyword: String, credential: String): List<ShareFile>? =
        api.searchCloudFiles(keyword, credential)
}

// ---------------------------------------------------------------- UC

class UCCloudApi(private val api: UCApi) : CloudApi {
    override val platform = SharePlatform.UC
    override val rootId: String get() = "0"
    override val passcodeMode = PasscodeMode.OPTIONAL

    override suspend fun list(dirId: String, credential: String): List<ShareFile> =
        api.listCloudFiles(dirId, credential) ?: emptyList()

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? =
        api.getDownloadLink(file.fid, credential)

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean =
        api.renameFile(file.fid, newName, credential)

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean =
        files.all { api.moveFile(it.fid, toDirId, credential) != null }

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean =
        files.all { api.deleteFile(it.fid, credential) != null }

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? =
        api.createFolder(name, parentId, credential)

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? {
        val shareId = api.createShare(
            fidList = files.map { it.fid },
            title = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件",
            urlType = urlType,
            passcode = passcode,
            expiredType = expiredType,
            cookie = credential,
        ) ?: return null
        return api.getShareInfo(shareId, credential)
    }

    override fun downloadHeaders(credential: String) = DlHeaders.uc(credential)

    override val supportsSearch = true
    override suspend fun search(keyword: String, credential: String): List<ShareFile>? =
        api.searchCloudFiles(keyword, credential)
}

// ---------------------------------------------------------------- 百度

/**
 * 百度的「路径式」文件系统：它没有 fid，全部用绝对路径（根是 "/"）。
 * [ShareFile.fid] 在百度语境下存的就是路径。
 *
 * 两处与其它平台不同，所以单独实现：
 *  · 直链要两跳（fileMetas 取 dlink → locateDownload 把 dlink 换成最终地址）；
 *  · 创建分享的 period 是字面天数（0/1/7/30），不是中性码。
 */
class BaiduCloudApi(private val api: BaiduApi) : CloudApi {
    override val platform = SharePlatform.BAIDU
    override val rootId: String get() = "/"
    override val passcodeMode = PasscodeMode.REQUIRED

    /**
     * 百度的 `dir` 参数是**绝对路径**，也叫「path」，`fidToken` 存的正是它；
     * 而 `fid` 是 `fs_id`（一串数字），拿它当 `dir` 服务端会返回空列表 ——
     * 这就是「根目录有文件、点进去全空」（用户实测截图）。
     *
     * 三级取值，逐级兜底：
     *  1. `fidToken` 是绝对路径（以 `/` 开头）→ 直接用；
     *  2. 否则用 `pdirFid`（list 响应里回填的父目录绝对路径）+ 文件名现拼一个；
     *  3. 连 `pdirFid` 都没有 → 退回 `fid`（保底，至少不会崩）。
     */
    override fun dirIdOf(file: ShareFile): String {
        val token = file.fidToken.trim().trim('"')
        if (token.startsWith("/")) return token
        val parent = file.pdirFid.trim().trim('"').trimEnd('/')
        val name = file.fname.trim()
        if (name.isEmpty()) return token.ifBlank { file.fid }
        return if (parent.isEmpty()) "/$name" else "$parent/$name"
    }

    override suspend fun list(dirId: String, credential: String): List<ShareFile> =
        api.listCloudFiles(dirId.ifBlank { "/" }, credential)

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? =
        runCatching {
            // fileMetasDlink 只吃 fs_id，但百度 list 返回的 ShareFile.fid 存的是路径，
            // 所以这里用路径直接走 locateDownload（它内部会自己换 dlink）。
            val url = api.locateDownload(file.fid, credential)
            if (url.isBlank()) null
            else DownloadLink(
                fid = file.fid,
                filename = file.fname,
                downloadUrl = url,
                size = file.fsize,
            )
        }.getOrNull()

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean =
        api.renameFile(file.fid, newName, credential)

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean =
        api.moveFiles(files.map { it.fid }, toDirId, credential)

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean =
        api.deleteFiles(files.map { it.fid }, credential)

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? {
        val parent = parentId.ifBlank { "/" }
        val path = if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
        return if (api.createDir(path, credential)) path else null
    }

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? = runCatching {
        val fsIds = files.map { it.fid }
        val result = api.createShare(
            fsIds = fsIds,
            period = ShareExpire.baiduPeriod(expiredType),
            pwd = passcode,
            cookie = credential,
        )
        ShareInfo(
            shareUrl = result.link,
            passcode = result.pwd,
            pwdId = result.pwd,
            title = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件",
            expiredType = expiredType,
        )
    }.getOrNull()

    override fun downloadHeaders(credential: String) = DlHeaders.baidu(credential)

    override suspend fun quota(credential: String): QuotaInfo? = api.getQuota(credential)

    override val supportsSearch = true
    override suspend fun search(keyword: String, credential: String): List<ShareFile>? =
        api.searchCloudFiles(keyword, credential)
}

// ---------------------------------------------------------------- 115

class Pan115CloudApi(private val api: Pan115Api) : CloudApi {
    override val platform = SharePlatform.PAN115
    override val rootId: String get() = ""
    override val passcodeMode = PasscodeMode.OPTIONAL

    /** 115 的档位码是 101..105，不是中性码 —— 传错了弹窗里一个档位都选不中 */
    override val expireOptions: List<Pair<String, Int>> get() = ShareExpire.PAN115_OPTIONS

    override suspend fun list(dirId: String, credential: String): List<ShareFile> =
        api.listFiles(dirId, credential).files

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? =
        api.getDownloadLink(file, credential)

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean =
        runCatching { api.rename(file.fid, newName, credential); true }.getOrDefault(false)

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean =
        runCatching { api.move(files.map { it.fid }, toDirId, credential); true }.getOrDefault(false)

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean = runCatching {
        // 115 的删除接口要带父目录 cid
        api.delete(files.map { it.fid }, files.firstOrNull()?.pdirFid.orEmpty(), credential)
        true
    }.getOrDefault(false)

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? =
        api.createDir(parentId, name, credential)

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? = runCatching {
        // 115 的 share_duration 是 "-1"/"1"/"3"/"7"/"15" 字符串，必须用 pan115Duration 转换
        api.createShare(files.map { it.fid }, ShareExpire.pan115Duration(expiredType), credential)
    }.getOrNull()

    override suspend fun quota(credential: String): QuotaInfo? = api.getQuota(credential)

    override val supportsSearch = true
    override suspend fun search(keyword: String, credential: String): List<ShareFile>? =
        api.searchFiles(keyword, credential)
}

// ---------------------------------------------------------------- 123 云盘

class Pan123CloudApi(private val api: Pan123Api) : CloudApi {
    override val platform = SharePlatform.PAN123
    override val rootId: String get() = "0"
    override val passcodeMode = PasscodeMode.OPTIONAL

    override suspend fun list(dirId: String, credential: String): List<ShareFile> =
        api.listCloudFiles(dirId, credential)

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? =
        api.getDownloadLink(file, credential)

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean =
        runCatching { api.renameFile(file.fid, newName, credential); true }.getOrDefault(false)

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean =
        runCatching { api.moveFiles(files.map { it.fid }, toDirId, credential); true }.getOrDefault(false)

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean =
        runCatching { api.deleteFiles(files, credential); true }.getOrDefault(false)

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? =
        api.createDir(parentId, name, credential)

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? = runCatching {
        // 123 的 expiration 是绝对 ISO 时间串，内部会按中性码转天数；永久用 2099 哨兵
        api.createShare(
            fileIds = files.map { it.fid },
            shareName = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件",
            expiration = ormHubExpirationOf(expiredType),
            sharePwd = passcode.ifBlank { null },
            token = credential,
        )
    }.getOrNull()

    override suspend fun quota(credential: String): QuotaInfo? = api.getQuota(credential)

    override val supportsSearch = true
    override suspend fun search(keyword: String, credential: String): List<ShareFile>? =
        api.searchCloudFiles(keyword, credential)

    override val supportsUpload = true
    override suspend fun upload(
        dirId: String,
        fileName: String,
        size: Long,
        openStream: () -> java.io.InputStream,
        credential: String,
        onProgress: (Long, Long) -> Unit,
    ): Boolean = api.uploadFile(
        parentFileId = dirId,
        fileName = fileName,
        size = size,
        openStream = openStream,
        token = credential,
        onProgress = onProgress,
    )
}

/**
 * 中性码 → 123 的 `expiration`（绝对时间串，永久用 2099 哨兵）。
 * 与云析 `ShareExpire.daysOrNull` 的语义一致。
 */
private fun ormHubExpirationOf(expiredType: Int): String {
    val days = ShareExpire.daysOrNull(expiredType)
    val cal = java.util.Calendar.getInstance()
    if (days == null) {
        cal.set(2099, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
    } else {
        cal.add(java.util.Calendar.DAY_OF_YEAR, days)
    }
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
    return fmt.format(cal.time)
}

// ---------------------------------------------------------------- 移动云盘 139

class C139CloudApi(private val api: C139Api) : CloudApi {
    override val platform = SharePlatform.C139

    /**
     * ⚠️ 139 的根目录是 `"/"`，**不是空串**。
     *
     * 空串会被原样塞进请求体的 `parentFileId`，服务端直接拒：
     * 「父目录ID不允许为空（code=04000002）」（用户实测截图）。
     * 上游 YunX 的 `C139CloudViewModel.kt:122` 就是 `load("/", emptyList())`。
     */
    override val rootId: String get() = "/"

    override val passcodeMode = PasscodeMode.SERVER_GENERATED

    override suspend fun list(dirId: String, credential: String): List<ShareFile> =
        api.listCloudFiles(dirId, credential)

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? =
        api.getDownloadUrl(file.fid, credential)

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean =
        api.renameFile(file.fid, newName, credential)

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean =
        api.moveFiles(files.map { it.fid }, toDirId, credential) != null

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean =
        api.deleteFiles(files.map { it.fid }, credential) != null

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? =
        api.createDir(parentId, name, credential)

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? = runCatching {
        // 139 的提取码由服务端生成；period 用天数（永久则传 null，即不传该字段）
        val all = files.map { it.fid }
        api.createShare(
            coIDLst = all,
            caIDLst = all,
            period = ShareExpire.daysOrNull(expiredType),
            dedicatedName = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件",
            cookie = credential,
        )
    }.getOrNull()

    override fun downloadHeaders(credential: String) = DlHeaders.c139(credential)

    /**
     * 139 进不去云盘 99% 是 Cookie 里没有 `authorization`（这是它唯一认的凭证）。
     * 直接把「当前存档里到底有哪些字段」摊开给用户看，省得抓日志猜。
     */
    override fun credentialWarning(credential: String): String? =
        C139Constants.describeMissing(credential)

    override suspend fun quota(credential: String): QuotaInfo? = api.getQuota(credential)
}

// ---------------------------------------------------------------- 迅雷云盘

/**
 * 迅雷的鉴权不是单个 Cookie，而是 accessToken + deviceId + captchaToken 三件套。
 * 统一接口只有一个 [credential] 字符串，所以这里把它编码成 `token|deviceId|captchaToken`。
 */
class XunleiCloudApi(private val api: XunleiApi) : CloudApi {
    override val platform = SharePlatform.XUNLEI
    override val rootId: String get() = ""
    override val passcodeMode = PasscodeMode.REQUIRED_OR_AUTO

    /**
     * 凭据拆解：`accessToken|deviceId|captchaToken`。
     *
     * ⚠️ 三段缺一不可 —— 迅雷 pan 接口要「Bearer + X-Device-Id + X-Captcha-Token」，
     * 只给 token 会 400 invalid_argument。deviceId 为空时退回官方 fallback 指纹，
     * 保证至少不会因为缺头直接崩。
     */
    private fun parts(credential: String): Triple<String, String, String> {
        val p = credential.split('|')
        val device = p.getOrElse(1) { "" }.ifBlank { XunleiConstants.DEVICE_ID }
        return Triple(p.getOrElse(0) { "" }, device, p.getOrElse(2) { "" })
    }

    /**
     * 迅雷的 accessToken 需要先从刷新令牌换一次，这里直接用存好的 accessToken。
     *
     * captchaToken 过期时会抛 `captcha_invalid` —— [XunleiApi.panCallInternal] 里已经内置了
     * 「用正确 action + captcha_sign 重新 init 再重试一次」的逻辑，所以这里不用额外兜。
     */
    override suspend fun list(dirId: String, credential: String): List<ShareFile> {
        val (token, deviceId, captcha) = parts(credential)
        return api.getFiles(dirId, token, deviceId, captcha) ?: emptyList()
    }

    override suspend fun downloadLink(file: ShareFile, credential: String): DownloadLink? {
        val (token, deviceId, captcha) = parts(credential)
        return api.getFileDetail(file.fid, token, deviceId, captcha)
    }

    override suspend fun rename(file: ShareFile, newName: String, credential: String): Boolean {
        val (token, deviceId, captcha) = parts(credential)
        return api.renameFile(file.fid, newName, token, deviceId, captcha)
    }

    override suspend fun move(files: List<ShareFile>, toDirId: String, credential: String): Boolean {
        val (token, deviceId, captcha) = parts(credential)
        return api.moveFile(files.map { it.fid }, toDirId, token, deviceId, captcha) != null
    }

    override suspend fun delete(files: List<ShareFile>, credential: String): Boolean {
        val (token, deviceId, captcha) = parts(credential)
        return api.deleteFiles(files.map { it.fid }, token, deviceId, captcha)
    }

    override suspend fun createFolder(name: String, parentId: String, credential: String): String? {
        val (token, deviceId, captcha) = parts(credential)
        return api.createFolder(name, parentId, token, deviceId, captcha)
    }

    override suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        credential: String,
    ): ShareInfo? {
        val (token, deviceId, captcha) = parts(credential)
        // 迅雷的 expiration_days 是字符串 "-1"/"1"/"7"/"30"；提取码可留空由服务端生成
        return api.createShare(
            fileIds = files.map { it.fid },
            title = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件",
            expirationDays = ShareExpire.xunleiDays(expiredType),
            accessToken = token,
            deviceId = deviceId,
            captchaToken = captcha,
            passCode = passcode,
        )
    }

    override suspend fun quota(credential: String): QuotaInfo? {
        val (token, deviceId, captcha) = parts(credential)
        return api.getQuota(token, deviceId, captcha)
    }

    override val supportsSearch = true
    override suspend fun search(keyword: String, credential: String): List<ShareFile>? {
        val (token, deviceId, captcha) = parts(credential)
        return api.searchFiles(keyword, token, deviceId, captcha)
    }
}

/** URL 编码小工具（百度路径拼接会用到） */
fun encodePath(s: String): String = URLEncoder.encode(s, "UTF-8")
