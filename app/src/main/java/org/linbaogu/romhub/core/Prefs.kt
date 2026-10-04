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
    private const val K_STORAGE_PROMPTED = "storage_prompt_shown"
    private const val K_NOTIFY_PORTS = "notify_ports"
    private const val K_NOTIFY_OFFICIAL = "notify_official"
    private const val K_STREAM_SEQ = "stream_last_seq"
    private const val K_NAV = "nav_state"
    private const val K_SERVER_BASE = "server_base"
    private const val K_DL_THREADS = "dl_threads"
    private const val K_DL_CONCURRENT = "dl_concurrent"
    private const val K_AUTO_SAVE_LOGIN = "auto_save_login"
    private const val K_DL_NO_TRANSFER = "dl_no_transfer"
    private const val K_DL_KEEP_AWAKE = "dl_keep_awake"
    private const val K_DL_RETRY = "dl_retry"
    private const val K_DL_SPEED_LIMIT = "dl_speed_limit"
    private const val K_DL_NOTIFY_SPEED = "dl_notify_speed"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 服务器地址
    //
    // 服务端可能跑在三个地方，网络环境也一直在变，所以地址不能写死：
    //   · 手机本机      → http://127.0.0.1:8787      （服务端跑在手机上，任何网络都能用）
    //   · 同一局域网的电脑 → http://192.168.x.x:8787   （用户自己填）
    //   · 公网          → https://rom.linbaogu.dpdns.org（默认兜底）
    // 请求失败时 [org.linbaogu.romhub.data.Api] 会自动换下一个候选，成功后记住。

    fun serverBase(context: Context): String =
        sp(context).getString(K_SERVER_BASE, "").orEmpty().ifBlank { DEFAULT_BASE }

    fun setServerBase(context: Context, url: String) =
        sp(context).edit { putString(K_SERVER_BASE, url.trim().trimEnd('/')) }

    fun clearServerBase(context: Context) =
        sp(context).edit { remove(K_SERVER_BASE) }

    /** 本机自建服务的地址（手机跑服务端时的第一选择）。 */
    fun localBases(): List<String> = listOf("http://127.0.0.1:8787", "http://localhost:8787")

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

    /**
     * 实时通道收到到第几号事件。
     *
     * 断线重连时把它交给服务端（since=），服务端会把这段被漏掉的事件补发回来，
     * 所以「切后台一小时再回来」不会丢更新。
     */
    fun streamLastSeq(context: Context): Long = sp(context).getLong(K_STREAM_SEQ, 0L)

    fun setStreamLastSeq(context: Context, seq: Long) {
        if (seq > 0) sp(context).edit { putLong(K_STREAM_SEQ, seq) }
    }

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

    // ---------------------------------------------------------------- 存储权限

    /**
     * 存储权限引导弹窗只主动弹一次。
     * 之后没权限也没关系 —— 下载会自动退回应用私有目录，功能不中断。
     */
    fun storagePromptShown(context: Context): Boolean =
        sp(context).getBoolean(K_STORAGE_PROMPTED, false)

    fun setStoragePromptShown(context: Context, shown: Boolean) =
        sp(context).edit { putBoolean(K_STORAGE_PROMPTED, shown) }

    fun setNotifyEnabled(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_ON, on) }

    fun notifyPorts(context: Context): Boolean = sp(context).getBoolean(K_NOTIFY_PORTS, true)

    fun setNotifyPorts(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_PORTS, on) }

    fun notifyOfficial(context: Context): Boolean = sp(context).getBoolean(K_NOTIFY_OFFICIAL, true)

    fun setNotifyOfficial(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_NOTIFY_OFFICIAL, on) }


    // ---------------------------------------------------------------- 网盘登录

    /**
     * 登录成功后是否自动保存并关闭登录页。
     *
     * **默认关** —— 原因是踩过坑：139（移动云盘）等平台在登录**中间态**就会写入
     * `authorization=` 之类的字段，自动检测会误判成「已登录」直接关页，
     * 用户还没看清就退出了（用户反馈原话：「登录之后网页就直接退出了我还没点保存呢」）。
     *
     * 关掉后由用户点右上角「保存」确认，行为可预期；想省事的可以在这里打开。
     */
    fun autoSaveLogin(context: Context): Boolean =
        sp(context).getBoolean(K_AUTO_SAVE_LOGIN, false)

    fun setAutoSaveLogin(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_AUTO_SAVE_LOGIN, on) }

    // ---------------------------------------------------------------- 下载器设置

    /** 每个任务开几个分片线程（1~[MAX_THREADS]）。 */
    fun downloadThreads(context: Context): Int =
        sp(context).getInt(K_DL_THREADS, 8).coerceIn(1, MAX_THREADS)

    fun setDownloadThreads(context: Context, n: Int) =
        sp(context).edit { putInt(K_DL_THREADS, n.coerceIn(1, MAX_THREADS)) }

    /** 同时下载几个任务（1~5）。 */
    fun downloadConcurrent(context: Context): Int = sp(context).getInt(K_DL_CONCURRENT, 2)

    fun setDownloadConcurrent(context: Context, n: Int) =
        sp(context).edit { putInt(K_DL_CONCURRENT, n.coerceIn(1, 5)) }

    /**
     * 按平台单独设线程数。
     *
     * 为什么按平台分：各家网盘对并发的容忍度差得远 —— 百度开太多会被限速到怀疑人生，
     * 夸克 / 115 则能吃到高并发。所以把「通用设置」和「单平台覆盖」分开。
     */
    fun downloadThreadsFor(context: Context, platform: String): Int = sp(context)
        .getInt("dl_threads_$platform", downloadThreads(context))
        .coerceIn(1, MAX_THREADS)

    fun setDownloadThreadsFor(context: Context, platform: String, n: Int) =
        sp(context).edit { putInt("dl_threads_$platform", n.coerceIn(1, MAX_THREADS)) }

    /** 免转存下载：优先走「不转存直接取链」，避免往用户网盘里塞临时文件。 */
    fun downloadWithoutTransfer(context: Context): Boolean =
        sp(context).getBoolean(K_DL_NO_TRANSFER, true)

    fun setDownloadWithoutTransfer(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_NO_TRANSFER, on) }

    /** 锁屏后保持下载（WakeLock）。 */
    fun downloadKeepAwake(context: Context): Boolean =
        sp(context).getBoolean(K_DL_KEEP_AWAKE, true)

    fun setDownloadKeepAwake(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_KEEP_AWAKE, on) }

    /** 失败自动重试次数（0 = 不重试）。 */
    fun downloadRetryCount(context: Context): Int = sp(context).getInt(K_DL_RETRY, 3)

    fun setDownloadRetryCount(context: Context, n: Int) =
        sp(context).edit { putInt(K_DL_RETRY, n.coerceIn(0, 10)) }

    /** 全局限速（KB/s，0 = 不限速）。 */
    fun downloadSpeedLimitKb(context: Context): Int = sp(context).getInt(K_DL_SPEED_LIMIT, 0)

    fun setDownloadSpeedLimitKb(context: Context, kb: Int) =
        sp(context).edit { putInt(K_DL_SPEED_LIMIT, kb.coerceIn(0, 1024 * 100)) }

    /** 通知栏显示下载速度（关掉就只有进度条）。 */
    fun downloadNotifySpeed(context: Context): Boolean =
        sp(context).getBoolean(K_DL_NOTIFY_SPEED, true)

    fun setDownloadNotifySpeed(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_NOTIFY_SPEED, on) }

    /**
     * 线程数硬上限。
     *
     * 与 [org.linbaogu.romhub.download.ChunkDownloader.MAX_INFLIGHT] 区分：
     * 那个是**同时在飞的请求数**（网络层限流），
     * 这个是**用户能选的档位上限**（切分多少片）。两者不是一回事。
     */
    const val MAX_THREADS = 256

    /**
     * 界面上的线程数档位。
     *
     * 为什么不是连续 1~256：档位太密既难选又没意义（用户判断不出 37 和 41 的差别），
     * 实际有效的是几个数量级 —— 每翻一倍才会明显吃满带宽。
     */
    val THREAD_OPTIONS = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
}
