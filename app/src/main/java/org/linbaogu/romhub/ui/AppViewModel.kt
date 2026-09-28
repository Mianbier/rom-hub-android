package org.linbaogu.romhub.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.linbaogu.romhub.BuildConfig
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.core.Session
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.AppUpdateInfo
import org.linbaogu.romhub.data.DevApplyReq
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.data.RomStats
import org.linbaogu.romhub.notify.NotifyScheduler
import org.linbaogu.romhub.ui.nav.AppNavState
import org.linbaogu.romhub.ui.nav.Screen
import org.linbaogu.romhub.ui.nav.parseDeepLink
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.UploadCloud

data class TabSpec(val title: String, val icon: ImageVector)

/** 前台动态轮询间隔（毫秒）。 */
private const val FEED_POLL_MS = 30_000L

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    var session by mutableStateOf(Prefs.session(ctx))
        private set

    var unread by mutableIntStateOf(Repo.unreadCount(ctx))
        private set

    var stats by mutableStateOf<RomStats?>(null)
        private set

    var globalMessage by mutableStateOf<String?>(null)

    /** 服务端上比当前更新的版本；没有就是 null。 */
    var appUpdate by mutableStateOf<AppUpdateInfo?>(null)
        private set

    /** 用户本次启动内点了「以后再说」，就不再打断他。 */
    var updateDismissed by mutableStateOf(false)
        private set

    val nav = AppNavState { Prefs.setNavState(ctx, it) }

    private var pendingDeepLink: Screen? = null

    val tabs: List<TabSpec>
        get() = if (session.role == Role.DEV) {
            listOf(
                TabSpec("主页", MiuixIcons.Home),
                TabSpec("动态", MiuixIcons.Update),
                TabSpec("包上传", MiuixIcons.UploadCloud),
                TabSpec("关于", MiuixIcons.Info),
            )
        } else {
            listOf(
                TabSpec("主页", MiuixIcons.Home),
                TabSpec("动态", MiuixIcons.Update),
                TabSpec("关于", MiuixIcons.Info),
            )
        }

    init {
        // 先按身份定 tab 数（开发者 4 个 / 游客 3 个），再恢复上次的页面栈。
        // 顺序不能反：tabCount 不对会导致恢复出来的 tab 索引被判非法。
        nav.tabCount = tabs.size
        nav.restore(Prefs.navState(ctx))

        // 每次启动问一次有没有新版本（失败静默，不影响使用）
        checkAppUpdate()

        NotifyScheduler.schedule(ctx)
        viewModelScope.launch { runCatching { stats = Repo.stats(ctx) } }
        // 实时动态：前台每 30 秒拉一次，新动态立刻反映到未读数（底栏小红点）
        startRealtimeFeed()
    }

    // ------------------------------------------------------------ 实时动态

    /**
     * 前台实时刷新动态。
     *
     * 以前只有启动时拉一次、且 onResume 只读本地缓存的旧值 —— 用户上传了包
     * 之后 App 里毫无反应，底栏小红点也永远是 0。现在改成：
     *  ① 先补建「已读基线」（首次安装或后台 Worker 没跑起来时永远没基线，
     *     导致未读数恒为 0、红点从不出现）；
     *  ② 之后每 30 秒拉一次服务端，有新条目就更新未读数并发通知。
     */
    private fun startRealtimeFeed() {
        viewModelScope.launch {
            // ① 基线必须先有，否则新条目永远不会算成未读
            runCatching { Repo.initFeedBaselineIfNeeded(ctx) }
            while (isActive) {
                runCatching {
                    Repo.refreshFeed(ctx)
                    unread = Repo.unreadCount(ctx)
                }
                delay(FEED_POLL_MS)
            }
        }
    }

    // ------------------------------------------------------------ 会话

    fun loginGuest() {
        Prefs.setSession(ctx, Session(role = Role.GUEST))
        applySession()
    }

    /** @return 错误文案，null 表示成功 */
    suspend fun loginDev(username: String, password: String): String? {
        if (username.isBlank() || password.isBlank()) return "账号和密码都不能为空"
        return try {
            val r = Api.devLogin(ctx, username.trim(), password)
            if (!r.ok || r.token.isBlank()) {
                r.error.ifBlank { "账号或密码错误，或账号还没通过审核" }
            } else {
                Prefs.setSession(
                    ctx,
                    Session(role = Role.DEV, username = r.username.ifBlank { username.trim() }, token = r.token),
                )
                applySession()
                null
            }
        } catch (e: Exception) {
            e.message ?: "网络异常"
        }
    }

    suspend fun applyDev(username: String, password: String, contact: String, note: String): String? {
        if (username.trim().length < 3) return "账号至少 3 位"
        if (password.length < 6) return "密码至少 6 位"
        return try {
            val r = Api.devApply(
                ctx,
                DevApplyReq(
                    username = username.trim(),
                    password = password,
                    contact = contact.trim(),
                    note = note.trim(),
                ),
            )
            if (r.ok) null else r.error.ifBlank { "申请失败" }
        } catch (e: Exception) {
            e.message ?: "网络异常"
        }
    }

    fun logout() {
        Prefs.logout(ctx)
        applySession()
    }

    fun refreshSession() {
        applySession()
    }

    private fun applySession() {
        val before = session.role
        session = Prefs.session(ctx)
        nav.tabCount = tabs.size

        // ⚠ 只有身份真的变了（或当前 tab 越界）才回首页。
        //   以前这里是**无条件** nav.switchTab(0)，而 MainActivity.onResume() 每次都会调
        //   refreshSession() —— 结果从浏览器（或任何外部 App）切回来就被踹回首页。
        if (session.role != before || nav.currentTab >= nav.tabCount) {
            nav.switchTab(0)
        }
    }

    // ------------------------------------------------------------ 深链

    fun handleDeepLink(uri: String?) {
        val s = uri?.let { parseDeepLink(it) } ?: return
        pendingDeepLink = s
        if (session.role != Role.NONE) applyPendingDeepLink()
    }

    private fun applyPendingDeepLink() {
        val s = pendingDeepLink ?: return
        pendingDeepLink = null
        when (s) {
            is Screen.Tab -> nav.switchTab(s.index)
            else -> {
                nav.switchTab(0)
                nav.push(s)
            }
        }
    }

    // ------------------------------------------------------------ 动态未读

    fun markFeedSeen() {
        Repo.markFeedSeen(ctx)
        unread = 0
    }

    fun refreshUnread() {
        unread = Repo.unreadCount(ctx)
    }

    /** 立刻拉一次动态（从后台切回前台时调，比等轮询更快感知新内容）。 */
    fun refreshFeedNow() {
        viewModelScope.launch {
            runCatching {
                Repo.refreshFeed(ctx)
                unread = Repo.unreadCount(ctx)
            }
        }
    }

    // ------------------------------------------------------------ App 自检更新

    /**
     * @param manual true 表示用户在「关于」页点的，这时即使没更新也要回一句话。
     */
    fun checkAppUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val info = runCatching { Api.appUpdate(ctx) }.getOrNull()
            if (info == null) {
                if (manual) globalMessage = "没查到更新信息，检查网络后再试"
                return@launch
            }
            val newer = info.hasUpdate &&
                    info.url.isNotBlank() &&
                    info.versionCode > BuildConfig.VERSION_CODE
            appUpdate = if (newer) info else null
            if (manual) {
                globalMessage = if (newer) {
                    "有新版本 v${info.versionName}"
                } else {
                    "已是最新版本（v${BuildConfig.VERSION_NAME}）"
                }
            }
        }
    }

    fun clearMessage() {
        globalMessage = null
    }

    fun dismissUpdate() {
        updateDismissed = true
        appUpdate = null
    }

    fun refreshStats() {
        viewModelScope.launch { runCatching { stats = Repo.stats(ctx, force = true) } }
    }
}
