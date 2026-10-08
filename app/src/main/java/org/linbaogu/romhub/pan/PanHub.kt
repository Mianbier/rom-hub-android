package org.linbaogu.romhub.pan

import android.content.Context
import org.linbaogu.romhub.cloud.BaiduCloudApi
import org.linbaogu.romhub.cloud.C139CloudApi
import org.linbaogu.romhub.cloud.CloudApi
import org.linbaogu.romhub.cloud.Pan115CloudApi
import org.linbaogu.romhub.cloud.Pan123CloudApi
import org.linbaogu.romhub.cloud.QuarkCloudApi
import org.linbaogu.romhub.cloud.UCCloudApi
import org.linbaogu.romhub.cloud.XunleiCloudApi
import org.linbaogu.romhub.pan.model.DownloadLink
import org.linbaogu.romhub.pan.model.ShareFile
import org.linbaogu.romhub.pan.model.ShareSession
import org.linbaogu.romhub.pan.repo.BaiduAccountRepository
import org.linbaogu.romhub.pan.repo.BaiduResolveRepository
import org.linbaogu.romhub.pan.repo.C139AccountRepository
import org.linbaogu.romhub.pan.repo.C139ResolveRepository
import org.linbaogu.romhub.pan.repo.Pan115AccountRepository
import org.linbaogu.romhub.pan.repo.Pan115ResolveRepository
import org.linbaogu.romhub.pan.repo.Pan123AccountRepository
import org.linbaogu.romhub.pan.repo.Pan123ResolveRepository
import org.linbaogu.romhub.pan.repo.QuarkAccountRepository
import org.linbaogu.romhub.pan.repo.QuarkResolveRepository
import org.linbaogu.romhub.pan.repo.ShareResolveRepository
import org.linbaogu.romhub.pan.repo.UCAccountRepository
import org.linbaogu.romhub.pan.repo.UCResolveRepository
import org.linbaogu.romhub.pan.repo.XunleiAccountRepository
import org.linbaogu.romhub.pan.repo.XunleiResolveRepository
import org.linbaogu.romhub.pan.store.BaiduAccountDao
import org.linbaogu.romhub.pan.store.C139AccountDao
import org.linbaogu.romhub.pan.store.Pan115AccountDao
import org.linbaogu.romhub.pan.store.Pan123AccountDao
import org.linbaogu.romhub.pan.store.QuarkAccountDao
import org.linbaogu.romhub.pan.store.UCAccountDao
import org.linbaogu.romhub.pan.store.XunleiAccountDao
import java.util.concurrent.ConcurrentHashMap

/**
 * 网盘解析的统一入口。
 *
 * 这里是唯一需要「知道所有网盘」的地方 —— 上面那层 UI 只调 [resolve] 和
 * [pickDownloadLink]，不用关心是哪家网盘、要带什么 cookie。
 *
 * 代码来自云析（CYQawa/YunX，AGPL-3.0）：`pan/` 下的解析层和 `pan/repo/` 下的
 * 流程层都是搬过来的，只改了包名，并把 Room 换成 JSON 存储（见 store/AccountStore.kt）。
 */
object PanHub {

    // ---------------------------------------------------------------- Api 单例
    // 这些 Api 都是无状态的（每个方法自己收发请求），所以全局一份就够。
    private val baiduApi by lazy { BaiduApi() }
    private val quarkApi by lazy { QuarkApi() }
    private val ucApi by lazy { UCApi() }
    private val pan115Api by lazy { Pan115Api() }
    private val pan123Api by lazy { Pan123Api() }
    private val c139Api by lazy { C139Api() }
    private val xunleiApi by lazy { XunleiApi() }

    // ---------------------------------------------------------------- 账号仓库
    private val cache = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, create: () -> T): T =
        cache.getOrPut(key) { create() } as T

    fun baiduAccount(ctx: Context): BaiduAccountRepository =
        cached("baidu") { BaiduAccountRepository(BaiduAccountDao(ctx), baiduApi) }

    fun quarkAccount(ctx: Context): QuarkAccountRepository =
        cached("quark") { QuarkAccountRepository(QuarkAccountDao(ctx), quarkApi) }

    fun ucAccount(ctx: Context): UCAccountRepository =
        cached("uc") { UCAccountRepository(UCAccountDao(ctx), ucApi) }

    fun pan115Account(ctx: Context): Pan115AccountRepository =
        cached("pan115") { Pan115AccountRepository(Pan115AccountDao(ctx), pan115Api) }

    fun pan123Account(ctx: Context): Pan123AccountRepository =
        cached("pan123") { Pan123AccountRepository(Pan123AccountDao(ctx), pan123Api) }

    fun c139Account(ctx: Context): C139AccountRepository =
        cached("c139") { C139AccountRepository(C139AccountDao(ctx)) }

    /** 139 Api 入口（账号仓库要用它做登录态探针；也行内直取空间详情）。 */
    fun c139Api(): C139Api = c139Api

    fun xunleiAccount(ctx: Context): XunleiAccountRepository =
        cached("xunlei") { XunleiAccountRepository(XunleiAccountDao(ctx), xunleiApi) }

    // ---------------------------------------------------------------- 解析仓库

    fun resolveRepo(ctx: Context, platform: SharePlatform): ShareResolveRepository? = when (platform) {
        SharePlatform.QUARK -> QuarkResolveRepository(quarkApi)
        SharePlatform.UC -> UCResolveRepository(ucApi)
        SharePlatform.BAIDU -> BaiduResolveRepository(baiduApi)
        SharePlatform.PAN115 -> Pan115ResolveRepository(pan115Api)
        SharePlatform.PAN123 -> Pan123ResolveRepository(pan123Api) { credential(ctx, SharePlatform.PAN123) }
        SharePlatform.C139 -> C139ResolveRepository(c139Api)
        // ⚠️ 这里 accountProvider 要的是**裸 accessToken**（不是 credential 的三段式）——
        //    deviceId / captcha 由下面两个 provider 单独给。分享解析仓库是按「三件套分开传」设计的。
        SharePlatform.XUNLEI -> XunleiResolveRepository(
            api = xunleiApi,
            accountProvider = {
                runCatching { xunleiAccount(ctx).getAccount()?.accessToken }.getOrNull()
            },
            deviceIdProvider = { runCatching { xunleiAccount(ctx).getAccount()?.deviceId }.getOrNull() },
            captchaProvider = { runCatching { xunleiAccount(ctx).getAccount()?.captchaToken }.getOrNull() },
            refreshProvider = null,
        )
        // GitHub 不走这套：它直接用 GitHubApi 拿直链（见 resolve()）
        SharePlatform.GITHUB -> null
    }

    // ---------------------------------------------------------------- 云盘浏览
    // 云析给 7 家各写了一个 ~680 行的 CloudViewModel，这里收拢成「一个接口 + 7 个适配器」，
    // 上面的浏览 UI 只认 CloudApi，不关心是哪家网盘。

    fun cloudApi(platform: SharePlatform): CloudApi? = when (platform) {
        SharePlatform.QUARK -> QuarkCloudApi(quarkApi)
        SharePlatform.UC -> UCCloudApi(ucApi)
        SharePlatform.BAIDU -> BaiduCloudApi(baiduApi)
        SharePlatform.PAN115 -> Pan115CloudApi(pan115Api)
        SharePlatform.PAN123 -> Pan123CloudApi(pan123Api)
        SharePlatform.C139 -> C139CloudApi(c139Api)
        SharePlatform.XUNLEI -> XunleiCloudApi(xunleiApi)
        SharePlatform.GITHUB -> null
    }

    // ---------------------------------------------------------------- 凭据

    /**
     * 取这个平台当前可用的凭据（cookie 或 token）；没登录返回 null。
     *
     * ⚠️ 迅雷是**三段式**（`accessToken|deviceId|captchaToken`），不是裸 token ——
     * 见 [XunleiCloudApi] 的 `parts()`。deviceId 和 captchaToken **必须一起带上**：
     * 迅雷 pan 接口是「Bearer token + X-Device-Id + X-Captcha-Token」三件套，
     * 只给 token 会被判 `invalid_argument`（HTTP 400，用户实测截图）。
     * 而且 token、deviceId 是**同一次登录生成**的，不能拿旧的配新的。
     */
    suspend fun credential(ctx: Context, platform: SharePlatform): String? = runCatching {
        when (platform) {
            SharePlatform.QUARK ->
                quarkAccount(ctx).getFreshCookie()?.takeIf { it.isNotBlank() }
                    ?: quarkAccount(ctx).getAccount()?.cookie?.takeIf { it.isNotBlank() }

            SharePlatform.UC ->
                ucAccount(ctx).getFreshCookie()?.takeIf { it.isNotBlank() }
                    ?: ucAccount(ctx).getAccount()?.cookie?.takeIf { it.isNotBlank() }

            SharePlatform.BAIDU ->
                baiduAccount(ctx).getAccount()?.cookie?.takeIf { it.isNotBlank() }

            SharePlatform.PAN115 ->
                pan115Account(ctx).getAccount()?.cookie?.takeIf { it.isNotBlank() }

            SharePlatform.PAN123 ->
                pan123Account(ctx).getAccount()?.accessToken?.takeIf { it.isNotBlank() }

            // ⚠️⚠️ 必须给**完整 Cookie 串**，不能给裸 authorization！
            // [C139Api] 的每个方法内部都会自己跑一次 `extractAuthorization(cookie)`，
            // 也就是在这个字符串里找 `authorization=` 前缀再取值。
            // 早年这里写成了 `it.authorization.ifBlank { it.cookie }`（返回 "Basic xxx"），
            // 对着 "Basic xxx" 找 "authorization=" 永远找不到 → 一律抛
            // 「登录态缺少 authorization」表现为「能登录成功但进不去云盘」。
            // 上游 YunX 的 MainScreen.kt:466/499 就是 `{ c139Repository.getAccount()?.cookie }`。
            //
            // ⚠️⚠️ 但 139 的坑比上游深一层：它的接口只认 `authorization=`，而**老存档很可能没有**
            // （早年版本存下来的就是不完整的一串）。直接原样交出去 → 必抛「登录态缺少 authorization」。
            // 所以这里过一遍 ensureAuthorization：能用现存字段算出来就补上，算不出来也至少
            // 把 entity.authorization 拼回去（登录时单独留了一份，正是为了救这种场）。
            SharePlatform.C139 ->
                c139Account(ctx).getAccount()
                    ?.let { C139Constants.ensureAuthorization(it.cookie, it.authorization) }
                    ?.takeIf { it.isNotBlank() }

            // 迅雷不能只给 token：pan 接口要「token + deviceId + captchaToken」三件套，
            // 缺 deviceId / captchaToken 会 400 invalid_argument。拼成 `token|deviceId|captcha`。
            SharePlatform.XUNLEI ->
                xunleiAccount(ctx).getAccount()
                    ?.takeIf { it.accessToken.isNotBlank() }
                    ?.let { "${it.accessToken}|${it.deviceId}|${it.captchaToken}" }

            SharePlatform.GITHUB -> null
        }
    }.getOrNull()

    suspend fun isLoggedIn(ctx: Context, platform: SharePlatform): Boolean =
        platform == SharePlatform.GITHUB || !credential(ctx, platform).isNullOrBlank()

    // ---------------------------------------------------------------- 统一解析

    /** 解析结果。UI 按这个分支决定下一步。 */
    sealed interface PanResult {
        /** 不是网盘分享链接（或 GitHub）：当普通直链，直接丢给下载器 */
        data class Direct(val url: String) : PanResult

        /** 解析成功，拿到文件列表（含目录）。 */
        data class Files(
            val platform: SharePlatform,
            val session: ShareSession,
            val files: List<ShareFile>,
            val credential: String,
        ) : PanResult

        /** 这家网盘还没登录。 */
        data class NeedLogin(val platform: SharePlatform) : PanResult

        /** 这个分享需要提取码（或提取码填错了）—— 界面应显示提取码输入框。 */
        data class NeedPassword(val platform: SharePlatform, val hint: String) : PanResult

        data class Failed(val message: String) : PanResult
    }

    /**
     * 认链接 → 决定走哪条路。
     *
     * · 认不出是网盘分享链接 → [PanResult.Direct]（普通下载）
     * · 认得出但没登录      → [PanResult.NeedLogin]
     * · 认得出且已登录      → 建会话 + 列根目录 → [PanResult.Files]
     */
    suspend fun resolve(ctx: Context, text: String, pwd: String? = null): PanResult {
        val trimmed = text.trim()
        val parsed = ShareLinkParser.parse(trimmed)
            ?: return PanResult.Direct(trimmed)

        if (parsed.platform == SharePlatform.GITHUB) {
            // GitHub 链接：Api 直接能给出可下载地址，不用会话
            return PanResult.Direct(trimmed)
        }

        val cred = credential(ctx, parsed.platform)
        if (cred.isNullOrBlank()) return PanResult.NeedLogin(parsed.platform)

        val repo = resolveRepo(ctx, parsed.platform)
            ?: return PanResult.Failed("暂不支持 ${platformName(parsed.platform)}")

        val effectivePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd
        val session = repo.createSession(trimmed, effectivePwd, cred)
            .getOrElse { e ->
                val msg = e.message.orEmpty()
                // 提取码错/缺失时，让界面提示补码。
                // 除了中文关键词，还要认「没填过码 + 分享本身要求码」这种情形 ——
                // 各家返回的文案/错误码不统一，靠字符串匹配容易漏（用户反馈：带提取码的分享
                // 一解析就报个看不懂的错，根本没给填码的地方）。
                val looksLikePwdIssue = msg.contains("密码") || msg.contains("提取码") ||
                    msg.contains("4100012") || msg.contains("4100008") ||
                    msg.contains("passwd", ignoreCase = true) ||
                    msg.contains("password", ignoreCase = true) ||
                    msg.contains("pwd", ignoreCase = true)
                if (looksLikePwdIssue) {
                    return PanResult.NeedPassword(parsed.platform, msg.ifBlank { "需要提取码" })
                }
                // 分享没填码、且链接里也没带码 → 一律提示补码，让用户有机会填
                if (effectivePwd.isNullOrBlank() && parsed.pwd.isNullOrBlank() &&
                    !msg.contains("不存在") && !msg.contains("失效") && !msg.contains("取消")
                ) {
                    return PanResult.NeedPassword(parsed.platform, msg)
                }
                return PanResult.Failed(msg.ifBlank { "解析失败" })
            }

        val files = repo.listFiles(session, "", cred)
            .getOrElse { e -> return PanResult.Failed(e.message ?: "读取文件列表失败") }

        return PanResult.Files(parsed.platform, session, files, cred)
    }

    /**
     * 列出分享里某个子目录的文件（供「浏览文件」页逐层进入）。
     *
     * [dirFid] 传空串或 "0" 表示根目录 —— 各家仓库内部自己把空串规整成根。
     */
    suspend fun listShareFiles(
        ctx: Context,
        platform: SharePlatform,
        session: ShareSession,
        dirFid: String,
        credential: String,
    ): Result<List<ShareFile>> {
        val repo = resolveRepo(ctx, platform)
            ?: return Result.failure(IllegalArgumentException("暂不支持 ${platformName(platform)}"))
        return repo.listFiles(session, dirFid, credential)
    }

    /**
     * 取某个文件的下载直链。
     *
     * [preferNoSave] 为 true 时优先走「不转存直接下」，失败再回落到转存路径 ——
     * 转存会在用户网盘里留下临时文件，能不下就不下。
     */
    suspend fun pickDownloadLink(
        ctx: Context,
        platform: SharePlatform,
        session: ShareSession,
        file: ShareFile,
        credential: String,
        preferNoSave: Boolean = true,
    ): Result<DownloadLink> {
        val repo = resolveRepo(ctx, platform)
            ?: return Result.failure(IllegalArgumentException("暂不支持 ${platformName(platform)}"))

        if (preferNoSave) {
            val direct = repo.getShareDownloadLinkWithoutSave(session, file, credential)
            if (direct.isSuccess) return direct
        }
        return repo.getShareDownloadLink(session, file, credential)
    }

    // ---------------------------------------------------------------- 小工具

    /**
     * 把分享里的文件**转存到用户自己的网盘**（不一定下载）。
     *
     * 这就是云析的「保存到网盘」：建一个临时目录 → 把分享文件转存进去。
     * 和 [pickDownloadLink] 的区别是这里不取直链，转存完就结束 ——
     * 用户想稍后在网盘 App 里下、或者归档留存，都用这条路径。
     *
     * @return 转存到的目标目录 fid
     */
    suspend fun saveToMyDrive(
        ctx: Context,
        platform: SharePlatform,
        session: ShareSession,
        file: ShareFile,
        credential: String,
    ): Result<String> {
        val repo = resolveRepo(ctx, platform)
            ?: return Result.failure(IllegalArgumentException("暂不支持 ${platformName(platform)}"))

        val dir = repo.ensureTempDir(credential).getOrElse { e ->
            return Result.failure(e)
        }
        return repo.transferFile(session, file, dir, credential)
    }

    /**
     * 批量转存（同一次会话里的多个文件转进同一个目录，避免建一堆临时目录）。
     *
     * @return Pair(目标目录 fid, 每个文件的转存结果)
     */
    suspend fun saveManyToMyDrive(
        ctx: Context,
        platform: SharePlatform,
        session: ShareSession,
        files: List<ShareFile>,
        credential: String,
    ): Result<Pair<String, List<Result<String>>>> {
        val repo = resolveRepo(ctx, platform)
            ?: return Result.failure(IllegalArgumentException("暂不支持 ${platformName(platform)}"))

        val dir = repo.ensureTempDir(credential).getOrElse { e ->
            return Result.failure(e)
        }
        val results = files.map { repo.transferFile(session, it, dir, credential) }
        return Result.success(dir to results)
    }

    fun platformName(p: SharePlatform): String = when (p) {
        SharePlatform.QUARK -> "夸克网盘"
        SharePlatform.UC -> "UC 网盘"
        SharePlatform.BAIDU -> "百度网盘"
        SharePlatform.PAN115 -> "115 网盘"
        SharePlatform.PAN123 -> "123 云盘"
        SharePlatform.C139 -> "移动云盘"
        SharePlatform.XUNLEI -> "迅雷云盘"
        SharePlatform.GITHUB -> "GitHub"
    }

    /** 这个链接是不是网盘分享链接（给 UI 显示标签用）。 */
    fun platformOf(text: String): SharePlatform? = ShareLinkParser.parse(text)?.platform

    /**
     * 各家网盘的推荐线程数。
     *
     * 依据：百度风控最严，开高并发容易被限速 / 判定异常，给 4；
     * 夸克、115 对并发宽容，可以吃满 8~16；其余取折中 8。
     * 注意入参是**平台显示名**（`platformName` 的返回值），不是枚举名。
     */
    fun recommendedThreads(platformName: String): Int = when {
        platformName.contains("百度") -> 4
        platformName.contains("夸克") || platformName.contains("115") -> 8
        else -> 8
    }
}
