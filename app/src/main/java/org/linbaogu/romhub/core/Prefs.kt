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
    private const val K_STATS_CACHE = "stats_cache_json"
    private const val K_DEVICES_CACHE = "devices_cache_json"
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
    private const val K_HC_NAV_ENABLED = "hc_nav_enabled"
    private const val K_HC_NAV_STYLE = "hc_nav_style"
    private const val K_HC_NAV_GLASS = "hc_nav_glass"
    private const val K_SERVER_BASE = "server_base"
    private const val K_DL_THREADS = "dl_threads"
    private const val K_DL_CONCURRENT = "dl_concurrent"
    private const val K_AUTO_SAVE_LOGIN = "auto_save_login"
    private const val K_DL_NO_TRANSFER = "dl_no_transfer"
    private const val K_DL_KEEP_AWAKE = "dl_keep_awake"
    private const val K_DL_RETRY = "dl_retry"
    private const val K_DL_SPEED_LIMIT = "dl_speed_limit"
    private const val K_DL_NOTIFY_SPEED = "dl_notify_speed"
    private const val K_DL_VERIFY = "dl_verify"
    private const val K_DL_DEEP_VERIFY = "dl_deep_verify"
    private const val K_DL_REMEMBER_DOMAIN = "dl_remember_domain"
    private const val K_DL_UA = "dl_user_agent"
    private const val K_DL_REFERER = "dl_referer"
    private const val K_DL_RETRY_INTERVAL = "dl_retry_interval"
    private const val K_DL_TIMEOUT = "dl_timeout"
    private const val K_DL_WIFI_ONLY = "dl_wifi_only"
    private const val K_DL_VIBRATE = "dl_vibrate"
    private const val K_DL_SOUND = "dl_sound"
    private const val K_DL_AUTO_OPEN = "dl_auto_open"
    private const val K_DL_KEEP_PARTS = "dl_keep_parts"
    private const val K_DL_PROXY_ON = "dl_proxy_on"
    private const val K_DL_PROXY_HOST = "dl_proxy_host"
    private const val K_DL_PROXY_PORT = "dl_proxy_port"
    private const val K_DL_M3U8_THREADS = "dl_m3u8_threads"
    private const val K_DL_BATTERY_LIMIT = "dl_battery_limit"
    // ---- 用户协议 / 隐私政策（内容在云端，本地只缓存 + 记「已同意到哪一版」）----
    private const val K_LEGAL_VERSION = "legal_version"
    private const val K_LEGAL_ACKED = "legal_acked_version"
    private const val K_LEGAL_TERMS = "legal_terms_cache"
    private const val K_LEGAL_PRIVACY = "legal_privacy_cache"
    private const val K_LEGAL_UPDATED = "legal_updated_at"

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

    // ---------------------------------------------------------------- 主页统计缓存

    /**
     * 主页统计的本地快照。
     *
     * 原来只有进程内的内存缓存，杀掉 App 再开就没了 —— 那一下整屏要空着等网络。
     * 落盘之后冷启动立刻能画出数字，再在后台刷新。
     */
    fun statsCache(context: Context): String =
        sp(context).getString(K_STATS_CACHE, "").orEmpty()

    fun setStatsCache(context: Context, json: String) =
        sp(context).edit { putString(K_STATS_CACHE, json) }

    // ---------------------------------------------------------------- 机型表缓存

    /** 机型表快照：设备页冷启动先画本地这份，再静默刷新。 */
    fun devicesCache(context: Context): String =
        sp(context).getString(K_DEVICES_CACHE, "").orEmpty()

    fun setDevicesCache(context: Context, json: String) =
        sp(context).edit { putString(K_DEVICES_CACHE, json) }

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
     * 下载完成后是否做完整性校验（大小 / zip 结构 / APK 签名）。
     *
     * **默认开**：这是「下载后签名不同」这类问题的第一道防线。
     * 关掉只是省掉下完那几秒的读盘，代价是用户拿到损坏文件也不知道。
     */
    fun downloadVerifyOnFinish(context: Context): Boolean =
        sp(context).getBoolean(K_DL_VERIFY, true)

    fun setDownloadVerifyOnFinish(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_VERIFY, on) }

    /**
     * 深度校验：是否对 zip/APK/ROM 包做逐条 CRC32 校验。
     *
     * 开：能真正查出"字节被写坏"（分片错位、下载中途损坏），但要把整个文件读一遍，
     * 几个 G 的 ROM 包会多花十几秒。
     * 关：只比大小 + 读 APK 签名（很快），查不出内容错位。
     *
     * **默认开** —— 用户的核心痛点是"下下来的包不对"，宁可多等几秒。
     */
    fun downloadDeepVerify(context: Context): Boolean =
        sp(context).getBoolean(K_DL_DEEP_VERIFY, true)

    fun setDownloadDeepVerify(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_DEEP_VERIFY, on) }

    /**
     * 是否按域名记住最优分片数（同一域名复用上次实测好用的值）。
     *
     * 开：下次从同一个 CDN 下载自动套用上次实测好用的分片数。
     * 关：每次都只用全局/任务自己的设置。
     */
    fun downloadRememberDomainParts(context: Context): Boolean =
        sp(context).getBoolean(K_DL_REMEMBER_DOMAIN, true)

    fun setDownloadRememberDomainParts(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_REMEMBER_DOMAIN, on) }

    /**
     * 自定义 User-Agent（空 = 用内置的浏览器 UA）。
     *
     * 有些源站对特定 UA 才给直链/不限速，故做成可配置。
     */
    fun downloadUserAgent(context: Context): String =
        sp(context).getString(K_DL_UA, "").orEmpty()

    fun setDownloadUserAgent(context: Context, ua: String) =
        sp(context).edit { putString(K_DL_UA, ua.trim()) }

    /**
     * 自定义 Referer（空 = 不带）。
     *
     * 国内不少 ROM 站/CDN 会校验 Referer，
     * 不带就直接 403。
     */
    fun downloadReferer(context: Context): String =
        sp(context).getString(K_DL_REFERER, "").orEmpty()

    fun setDownloadReferer(context: Context, r: String) =
        sp(context).edit { putString(K_DL_REFERER, r.trim()) }

    /**
     * 失败自动重试的间隔（秒，指数退避的基数）。
     *
     * 指数退避的重试间隔基数：实际等待 = interval * 2^(n-1)。
     */
    fun downloadRetryInterval(context: Context): Int =
        sp(context).getInt(K_DL_RETRY_INTERVAL, 1).coerceIn(1, 60)

    fun setDownloadRetryInterval(context: Context, s: Int) =
        sp(context).edit { putInt(K_DL_RETRY_INTERVAL, s.coerceIn(1, 60)) }

    /**
     * 连接超时（秒）。建连慢的网络可适当调大。
     */
    fun downloadTimeout(context: Context): Int =
        sp(context).getInt(K_DL_TIMEOUT, 20).coerceIn(5, 120)

    fun setDownloadTimeout(context: Context, s: Int) =
        sp(context).edit { putInt(K_DL_TIMEOUT, s.coerceIn(5, 120)) }

    /**
     * 仅在 Wi-Fi 下下载（蜂窝网时新任务不启动）。
     * 避免在移动网络下偷跑流量。
     */
    fun downloadWifiOnly(context: Context): Boolean =
        sp(context).getBoolean(K_DL_WIFI_ONLY, false)

    fun setDownloadWifiOnly(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_WIFI_ONLY, on) }

    /** 下载开始/完成/失败时震动提示。 */
    fun downloadVibrate(context: Context): Boolean =
        sp(context).getBoolean(K_DL_VIBRATE, false)

    fun setDownloadVibrate(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_VIBRATE, on) }

    /** 完成后提示音。 */
    fun downloadSound(context: Context): Boolean =
        sp(context).getBoolean(K_DL_SOUND, false)

    fun setDownloadSound(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_SOUND, on) }

    /** 完成后自动打开文件。 */
    fun downloadAutoOpen(context: Context): Boolean =
        sp(context).getBoolean(K_DL_AUTO_OPEN, false)

    fun setDownloadAutoOpen(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_AUTO_OPEN, on) }

    /**
     * 下载完成后是否保留原始分片临时文件（默认不保留）。
     * 关 = 合并后删掉 .parts 目录（省空间）；开 = 留着方便排查。
     */
    fun downloadKeepParts(context: Context): Boolean =
        sp(context).getBoolean(K_DL_KEEP_PARTS, false)

    fun setDownloadKeepParts(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_KEEP_PARTS, on) }

    // ---------------------------------------------------------------- 代理

    /** 是否启用 HTTP 代理（默认关）。 */
    fun downloadProxyOn(context: Context): Boolean =
        sp(context).getBoolean(K_DL_PROXY_ON, false)

    fun setDownloadProxyOn(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_PROXY_ON, on) }

    /** 代理主机（IP 或域名）。 */
    fun downloadProxyHost(context: Context): String =
        sp(context).getString(K_DL_PROXY_HOST, "").orEmpty()

    fun setDownloadProxyHost(context: Context, host: String) =
        sp(context).edit { putString(K_DL_PROXY_HOST, host.trim()) }

    /** 代理端口，0 = 未设。 */
    fun downloadProxyPort(context: Context): Int =
        sp(context).getInt(K_DL_PROXY_PORT, 0)

    fun setDownloadProxyPort(context: Context, port: Int) =
        sp(context).edit { putInt(K_DL_PROXY_PORT, port.coerceIn(0, 65535)) }

    /**
     * 把 prefs 里的代理配置套到网络层。
     *
     * 开关开着但主机为空 / 端口非法时视为直连 —— 避免用户只填了一半就整网不通。
     * 由 Application 启动时和设置页改动后调用。
     */
    fun applyProxy(context: Context) {
        val on = downloadProxyOn(context)
        val host = downloadProxyHost(context)
        val port = downloadProxyPort(context)
        if (on && host.isNotBlank() && port in 1..65535) {
            org.linbaogu.romhub.pan.HttpClients.setProxy(host, port)
        } else {
            org.linbaogu.romhub.pan.HttpClients.setProxy(null, 0)
        }
    }

    // ---------------------------------------------------------------- M3U8 / HLS

    /**
     * M3U8（HLS）分片下载线程数。
     *
     * 普通文件按 [downloadThreads] 切分片，但 HLS 的「片」是播放列表里已经切好的
     * ts/fmp4 段，粒度由源站决定 —— 单独给一个线程数控制并发拉取多少段。
     * 默认 4：HLS 段普遍较小，开太高反而容易被源站风控。
     */
    fun downloadM3u8Threads(context: Context): Int =
        sp(context).getInt(K_DL_M3U8_THREADS, 4).coerceIn(1, 32)

    fun setDownloadM3u8Threads(context: Context, n: Int) =
        sp(context).edit { putInt(K_DL_M3U8_THREADS, n.coerceIn(1, 32)) }

    // ---------------------------------------------------------------- 电池限制

    /**
     * 低电量时是否暂停下载。
     *
     * 开 = 电量低于 15% 且未在充电时不自动开始下载。
     */
    fun downloadBatteryLimit(context: Context): Boolean =
        sp(context).getBoolean(K_DL_BATTERY_LIMIT, false)

    fun setDownloadBatteryLimit(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_DL_BATTERY_LIMIT, on) }

    /**
     * 底栏是否使用 HyperCeiler 的悬浮底栏（View 版，照抄自 HyperCeiler 的 SwitchView）。
     * 关掉则回落到本 App 原来的 Compose 底栏。
     */
    fun hcNavEnabled(context: Context): Boolean = sp(context).getBoolean(K_HC_NAV_ENABLED, true)

    fun setHcNavEnabled(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_HC_NAV_ENABLED, on) }

    /**
     * 悬浮底栏样式，对应 HyperCeiler 的 `NavigationStyle`：
     *  · `CAPSULE_ICON` —— 悬浮胶囊（默认）
     *  · `BOTTOM_LABEL` —— 传统贴地底部标签
     */
    fun hcNavStyle(context: Context): String =
        sp(context).getString(K_HC_NAV_STYLE, "CAPSULE_ICON").orEmpty()

    fun setHcNavStyle(context: Context, value: String) =
        sp(context).edit { putString(K_HC_NAV_STYLE, value) }

    /**
     * 悬浮胶囊要不要「玻璃」（折射 + 内阴影 + 滑动指示器）。
     *
     * 开关逻辑（2026-10-07 理顺）：
     *  · 悬浮底栏 开 + 胶囊玻璃 开 → 液体玻璃悬浮胶囊（默认，能折射底下的内容）
     *  · 悬浮底栏 开 + 胶囊玻璃 关 → 纯色悬浮胶囊
     *  · 悬浮底栏 关              → 贴地标签栏（不悬浮，避开系统手势条）
     */
    // ---------------------------------------------------------- 协议 / 隐私

    /** 云端那份协议的最新版本号（本地缓存，没同步过就是 0）。 */
    fun legalVersion(context: Context): Int = sp(context).getInt(K_LEGAL_VERSION, 0)

    /** 用户**已经同意过**的版本号。云端版本比它大才需要弹窗。 */
    fun legalAckedVersion(context: Context): Int = sp(context).getInt(K_LEGAL_ACKED, 0)

    fun setLegalVersion(context: Context, v: Int) =
        sp(context).edit { putInt(K_LEGAL_VERSION, v) }

    fun setLegalAckedVersion(context: Context, v: Int) =
        sp(context).edit { putInt(K_LEGAL_ACKED, v) }

    /** 云端协议正文的本地缓存（读不到云端时先用它，再不行才退回 APK 内置那份）。 */
    fun legalTerms(context: Context): String = sp(context).getString(K_LEGAL_TERMS, "").orEmpty()

    fun legalPrivacy(context: Context): String = sp(context).getString(K_LEGAL_PRIVACY, "").orEmpty()

    fun legalUpdatedAt(context: Context): String =
        sp(context).getString(K_LEGAL_UPDATED, "").orEmpty()

    fun setLegalUpdatedAt(context: Context, at: String) =
        sp(context).edit { putString(K_LEGAL_UPDATED, at) }

    fun setLegalText(context: Context, terms: String, privacy: String) =
        sp(context).edit {
            putString(K_LEGAL_TERMS, terms)
            putString(K_LEGAL_PRIVACY, privacy)
        }

    fun hcNavGlass(context: Context): Boolean = sp(context).getBoolean(K_HC_NAV_GLASS, true)

    fun setHcNavGlass(context: Context, on: Boolean) =
        sp(context).edit { putBoolean(K_HC_NAV_GLASS, on) }

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
