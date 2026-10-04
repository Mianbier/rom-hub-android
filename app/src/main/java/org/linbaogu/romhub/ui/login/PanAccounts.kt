package org.linbaogu.romhub.ui.login

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform

/**
 * 网盘账号的界面侧门面：登录页只管调 [save]，账号管理页只管读 [state] / 调 [logout]。
 *
 * 为什么不用 ViewModel：ROM Hub 没引完整的 lifecycle-viewmodel 生态，而云析那 7 个
 * `*AccountViewModel` 干的事就是把 repository 包一层 —— 这里收拢成一个单例门面，
 * 少 7 个文件、少 7 个 Factory，行为完全一样。
 */
object PanAccounts {

    /** 页面能看到的账号摘要。 */
    data class AccountUiState(
        val platform: SharePlatform,
        val name: String,
        val loggedIn: Boolean,
        val nickname: String = "",
        /** 已用空间（字节）；0 表示未知（只有 139 会填） */
        val quotaUsed: Long = 0L,
        /** 总空间（字节）；0 表示未知 */
        val quotaTotal: Long = 0L,
    )

    /** 账号管理页的展示顺序（也是登录页的入口顺序）。GitHub 不需要登录，不列。 */
    val ALL: List<SharePlatform> = listOf(
        SharePlatform.QUARK,
        SharePlatform.UC,
        SharePlatform.BAIDU,
        SharePlatform.PAN115,
        SharePlatform.PAN123,
        SharePlatform.C139,
        SharePlatform.XUNLEI,
    )

    /** 哪些平台走「WebView 网页登录」，哪些走「自建表单」（迅雷）。 */
    fun isWebLogin(platform: SharePlatform): Boolean = platform != SharePlatform.XUNLEI

    /** 读全部账号状态 + 昵称。 */
    suspend fun state(ctx: Context): List<AccountUiState> = withContext(Dispatchers.IO) {
        ALL.map { p ->
            val credential = runCatching { PanHub.credential(ctx, p) }.getOrNull()
            val nickname = runCatching {
                when (p) {
                    SharePlatform.QUARK -> PanHub.quarkAccount(ctx).getAccount()?.nickname
                    SharePlatform.UC -> PanHub.ucAccount(ctx).getAccount()?.nickname
                    SharePlatform.BAIDU -> PanHub.baiduAccount(ctx).getAccount()?.nickname
                    SharePlatform.PAN115 -> PanHub.pan115Account(ctx).getAccount()?.nickname
                    SharePlatform.PAN123 -> PanHub.pan123Account(ctx).getAccount()?.nickname
                    SharePlatform.C139 -> PanHub.c139Account(ctx).getAccount()?.nickname
                    SharePlatform.XUNLEI -> PanHub.xunleiAccount(ctx).getAccount()?.nickname
                    SharePlatform.GITHUB -> null
                }
            }.getOrNull().orEmpty()

            // 空间信息：登录时探针抓到的缓存（目前只有 139 有）
            val quota = runCatching {
                if (p == SharePlatform.C139) PanHub.c139Account(ctx).getAccount()
                else null
            }.getOrNull()

            AccountUiState(
                platform = p,
                name = PanHub.platformName(p),
                loggedIn = !credential.isNullOrBlank(),
                nickname = nickname,
                quotaUsed = quota?.quotaUsed ?: 0L,
                quotaTotal = quota?.quotaTotal ?: 0L,
            )
        }
    }

    /**
     * 登录页抓到的凭证 → 走对应网盘的校验 + 落库。成功返回 true。
     *
     * 校验逻辑全在各家 repository 里（会真的打一次接口确认凭证有效），这里只做分派。
     */
    suspend fun save(ctx: Context, platform: SharePlatform, credential: String): Boolean =
        withContext(Dispatchers.IO) {
            if (credential.isBlank()) return@withContext false
            runCatching {
                when (platform) {
                    SharePlatform.QUARK -> PanHub.quarkAccount(ctx).saveQuarkAccount(credential)
                    SharePlatform.UC -> PanHub.ucAccount(ctx).saveUCAccount(credential)
                    SharePlatform.BAIDU -> PanHub.baiduAccount(ctx).saveBaiduAccount(credential)
                    SharePlatform.PAN115 -> PanHub.pan115Account(ctx).saveCookie(credential)
                    SharePlatform.PAN123 -> PanHub.pan123Account(ctx).saveToken(credential)
                    SharePlatform.C139 -> PanHub.c139Account(ctx).saveC139Account(credential)
                    // 迅雷不走 Cookie，必须走账号密码 + 短信流程（见 XunleiLoginScreen）
                    SharePlatform.XUNLEI, SharePlatform.GITHUB -> false
                }
            }.getOrDefault(false)
        }

    /** 退出登录。 */
    suspend fun logout(ctx: Context, platform: SharePlatform) = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                SharePlatform.QUARK -> PanHub.quarkAccount(ctx).logoutQuark()
                SharePlatform.UC -> PanHub.ucAccount(ctx).logoutUC()
                SharePlatform.BAIDU -> PanHub.baiduAccount(ctx).logoutBaidu()
                SharePlatform.PAN115 -> PanHub.pan115Account(ctx).logout()
                SharePlatform.PAN123 -> PanHub.pan123Account(ctx).logout()
                SharePlatform.C139 -> PanHub.c139Account(ctx).logoutC139()
                SharePlatform.XUNLEI -> PanHub.xunleiAccount(ctx).logout()
                SharePlatform.GITHUB -> Unit
            }
        }
    }
}
