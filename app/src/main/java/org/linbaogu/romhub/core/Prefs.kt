package org.linbaogu.romhub.core

import android.content.Context
import androidx.core.content.edit

/** 会话身份 */
enum class Role { NONE, GUEST, DEV }

data class Session(
    val role: Role = Role.NONE,
    val username: String = "",
    val token: String = "",
)

/**
 * 轻量本地存储。敏感字段（开发者令牌）走 [SecureStore]，其余走普通 SharedPreferences。
 */
object Prefs {

    private const val PREF = "romhub_prefs"
    const val DEFAULT_BASE = "https://rom.linbaogu.dpdns.org"

    private const val K_ROLE = "role"
    private const val K_USER = "username"
    private const val K_TOKEN = "dev_token"          // 加密存储
    private const val K_SUBS = "sub_device_codes"
    private const val K_FEED_CACHE = "feed_cache_json"
    private const val K_FEED_MAX_SEEN = "feed_max_seen_id"
    private const val K_FEED_UNREAD = "feed_unread"
    private const val K_NOTIFY_LAST_ID = "notify_last_id"
    private const val K_NOTIFY_ON = "notify_enabled"
    private const val K_NOTIFY_PROMPTED = "notify_prompt_shown"
    private const val K_WELCOME_DONE = "welcome_done"
    private const val K_NOTIFY_PORTS = "notify_ports"
    private const val K_NOTIFY_OFFICIAL = "notify_official"
    private const val K_NAV = "nav_state"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 导航存档
    // 跳浏览器（或系统回收进程）之后回来，靠它恢复原来那一页。
    fun navState(context: Context): String = sp(context).getString(K_NAV, "").orEmpty()

    fun setNavState(context: Context, value: String) {
        sp(context).edit { putString(K_NAV, value) }
    }

    // ---------------------------------------------------------------- 会话
    fun session(context: Context): Session {
        val role = runCatching {
            Role.valueOf(sp(context).getString(K_ROLE, Role.NONE.name) ?: Role.NONE.name)
        }.getOrDefault(Role.NONE)
        return Session(
            role = role,
            username = sp(context).getString(K_USER, "").orEmpty(),
            token = SecureStore.get(context, K_TOKEN).orEmpty(),
        )
    }

    fun setSession(context: Context, session: Session) {
        sp(context).edit { putString(K_ROLE, session.role.name); putString(K_USER, session.username) }
        if (session.token.isBlank()) SecureStore.remove(context, K_TOKEN)
        else SecureStore.put(context, K_TOKEN, session.token)
    }

    fun logout(context: Context) = setSession(context, Session())

    // ---------------------------------------------------------------- 订阅
    fun subscriptions(context: Context): Set<String> =
        sp(context).getStringSet(K_SUBS, emptySet())?.toSet() ?: emptySet()

    fun toggleSubscription(context: Context, code: String): Boolean {
        val cur = subscriptions(context).toMutableSet()
        val added = if (cur.contains(code)) {
            cur.remove(code); false
        } else {
            cur.add(code); true
        }
        sp(context).edit { putStringSet(K_SUBS, cur) }
        return added
    }

    fun isSubscribed(context: Context, code: String): Boolean =
        subscriptions(context).contains(code)

    // ---------------------------------------------------------------- 动态缓存
    fun feedCache(context: Context): String = sp(context).getString(K_FEED_CACHE, "").orEmpty()

    fun setFeedCache(context: Context, json: String) =
        sp(context).edit { putString(K_FEED_CACHE, json) }

    fun feedMaxSeenId(context: Context): Long = sp(context).getLong(K_FEED_MAX_SEEN, 0L)

    fun setFeedMaxSeenId(context: Context, id: Long) =
        sp(context).edit { putLong(K_FEED_MAX_SEEN, id) }

    fun feedUnread(context: Context): Int = sp(context).getInt(K_FEED_UNREAD, 0)

    fun setFeedUnread(context: Context, n: Int) =
        sp(context).edit { putInt(K_FEED_UNREAD, maxOf(0, n)) }

    // ---------------------------------------------------------------- 通知
    fun notifyLastId(context: Context): Long = sp(context).getLong(K_NOTIFY_LAST_ID, 0L)

    fun setNotifyLastId(context: Context, id: Long) =
        sp(context).edit { putLong(K_NOTIFY_LAST_ID, id) }

    fun notifyEnabled(context: Context): Boolean = sp(context).getBoolean(K_NOTIFY_ON, true)

    /** 启动时的权限说明弹窗只主动弹一次，之后从「关于」页再进。 */
    fun notifyPromptShown(context: Context): Boolean =
        sp(context).getBoolean(K_NOTIFY_PROMPTED, false)

    fun setNotifyPromptShown(context: Context, shown: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_PROMPTED, shown) }

    // ---------------------------------------------------------------- 首次启动引导

    /** HyperCeiler 式欢迎页：看完并同意条款后置 true，之后不再出现。 */
    fun welcomeDone(context: Context): Boolean =
        sp(context).getBoolean(K_WELCOME_DONE, false)

    fun setWelcomeDone(context: Context, done: Boolean) =
        sp(context).edit { putBoolean(K_WELCOME_DONE, done) }

    fun setNotifyEnabled(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_ON, on) }

    fun notifyPorts(context: Context): Boolean = sp(context).getBoolean(K_NOTIFY_PORTS, true)

    fun setNotifyPorts(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_PORTS, on) }

    fun notifyOfficial(context: Context): Boolean = sp(context).getBoolean(K_NOTIFY_OFFICIAL, true)

    fun setNotifyOfficial(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_OFFICIAL, on) }
}
