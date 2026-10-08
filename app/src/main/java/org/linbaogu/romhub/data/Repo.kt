package org.linbaogu.romhub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.core.Prefs

/**
 * 数据仓库：在 API 之上加一层本地缓存。
 *
 * 动态页的要求是「第一次要保存在用户本地」——所以 [refreshFeed] 会把结果合并进本地快照，
 * 并算出未读数；[cachedFeed] 让 App 冷启动时立刻有内容可画。
 */
object Repo {

    private const val FEED_KEEP = 300

    // 内存缓存（进程内），避免来回切页重复请求
    @Volatile private var devicesMem: List<DeviceItem>? = null
    @Volatile private var statsMem: RomStats? = null

    /**
     * 主页统计。
     *
     * 三级取值：内存 → 实时接口 → 本地快照。
     * 关键是**拿不到也要有数**：接口和快照都挂掉时返回上次落盘的那份，
     * 主页数字照常显示（由 [lastStatsOffline] 标注是旧数据），而不是整屏空着转圈。
     */
    suspend fun stats(ctx: Context, force: Boolean = false): RomStats {
        statsMem?.takeIf { !force }?.let { return it }
        val fetched = runCatching { Api.stats(ctx) }.getOrNull()
        if (fetched != null) {
            statsMem = fetched
            lastStatsOffline = false
            Prefs.setStatsCache(ctx, RomJson.encodeToString(fetched))
            return fetched
        }
        lastStatsOffline = true
        return cachedStats(ctx)
    }

    /**
     * 最近一次 [stats] 是不是走了兜底来源。
     *
     * 界面用它决定要不要标「离线数据」—— 兜底来的数字可能过时，
     * 不标的话用户会当成实时的，点进设备页发现没有新机型就会当成 bug。
     */
    @Volatile var lastStatsOffline: Boolean = false
        private set

    /** 上次成功拿到的统计（冷启动 / 全网不通时用）。 */
    suspend fun cachedStats(ctx: Context): RomStats {
        statsMem?.let { return it }
        val raw = Prefs.statsCache(ctx)
        if (raw.isNotBlank()) {
            runCatching { RomJson.decodeFromString<RomStats>(raw) }.getOrNull()?.let {
                statsMem = it
                return it
            }
        }
        // 连本地缓存都没有（刚装完第一次打开）→ 用打包进来的种子，至少不是一片 0
        return Seed.stats(ctx) ?: RomStats()
    }

    suspend fun devices(ctx: Context, keyword: String = "", force: Boolean = false): List<DeviceItem> {
        if (keyword.isBlank() && !force) {
            devicesMem?.let { return it }
        }
        val fetched = runCatching { Api.devices(ctx, keyword) }.getOrNull()
        if (fetched != null) {
            if (keyword.isBlank()) {
                devicesMem = fetched.items
                Prefs.setDevicesCache(ctx, RomJson.encodeToString(fetched))
            }
            return fetched.items
        }
        // 拿不到就用本地那份再筛一次，别让搜索框整个空掉
        if (keyword.isBlank()) {
            devicesMem?.let { return it }
            loadDevicesCache(ctx)?.let { return it }
        } else {
            loadDevicesCache(ctx)?.let { all ->
                return all.filter { it.matches(keyword) }
            }
        }
        return emptyList()
    }

    private fun loadDevicesCache(ctx: Context): List<DeviceItem>? {
        devicesMem?.let { return it }
        val raw = Prefs.devicesCache(ctx)
        if (raw.isBlank()) return null
        return runCatching { RomJson.decodeFromString<DeviceListResp>(raw).items }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.also { devicesMem = it }
    }

    fun cachedDevices(): List<DeviceItem> = devicesMem.orEmpty()

    // ------------------------------------------------------------- 动态

    fun cachedFeed(ctx: Context): RomUpdateListResp {
        val raw = Prefs.feedCache(ctx)
        if (raw.isBlank()) return RomUpdateListResp()
        return runCatching { RomJson.decodeFromString<RomUpdateListResp>(raw) }
            .getOrDefault(RomUpdateListResp())
    }

    /**
     * 拉取最新动态，与本地快照合并（按 id 去重，新的在前），
     * 并按「比上次已读位置更新的条数」算出未读数。
     */
    suspend fun refreshFeed(ctx: Context, limit: Int = 80): RomUpdateListResp {
        val fresh = Api.romUpdates(ctx, limit = limit)
        val old = cachedFeed(ctx).items
        val merged = LinkedHashMap<Long, RomUpdate>()
        fresh.items.forEach { merged[it.id] = it }
        old.forEach { if (!merged.containsKey(it.id)) merged[it.id] = it }
        val sorted = merged.values.sortedByDescending { it.id }.take(FEED_KEEP)

        // ⚠ 首次拿到数据时必须先建「已读基线」：原来 maxSeen 为 0 时未读恒为 0，
        //   于是之后的新动态永远不算未读、底栏小红点永远不出现。
        if (Prefs.feedMaxSeenId(ctx) <= 0L) {
            val maxNow = sorted.maxOfOrNull { it.id } ?: 0L
            if (maxNow > 0L) Prefs.setFeedMaxSeenId(ctx, maxNow)
        }
        val unread = sorted.count { it.id > Prefs.feedMaxSeenId(ctx) }

        val result = RomUpdateListResp(
            total = sorted.size,
            items = sorted,
            counts = fresh.counts,
            serverTime = fresh.serverTime,
        )
        Prefs.setFeedCache(ctx, RomJson.encodeToString(result))
        Prefs.setFeedUnread(ctx, unread)
        return result
    }

    /** 用户看完动态：把已读位置推到最前，清掉小红点。 */
    fun markFeedSeen(ctx: Context) {
        val max = cachedFeed(ctx).items.maxOfOrNull { it.id } ?: 0L
        val now = maxOf(max, Prefs.feedMaxSeenId(ctx))
        Prefs.setFeedMaxSeenId(ctx, now)
        Prefs.setFeedUnread(ctx, 0)
    }

    /** 首次使用：把当前内容当作已读基线（避免一安装就顶着红点）。 */
    fun initFeedBaselineIfNeeded(ctx: Context) {
        if (Prefs.feedMaxSeenId(ctx) == 0L) {
            val max = cachedFeed(ctx).items.maxOfOrNull { it.id } ?: 0L
            if (max > 0L) Prefs.setFeedMaxSeenId(ctx, max)
        }
    }

    fun unreadCount(ctx: Context): Int = Prefs.feedUnread(ctx)

    // ------------------------------------------------------------- 后台轮询（通知用）

    /**
     * 返回「订阅机型里、上次通知之后新出现的更新」。官方包与移植包分开看。
     */
    suspend fun pollNewUpdates(ctx: Context): Pair<List<RomUpdate>, List<PortPackage>> =
        withContext(Dispatchers.IO) {
            val subs = Prefs.subscriptions(ctx)
            if (subs.isEmpty()) return@withContext emptyList<RomUpdate>() to emptyList()
            val lastId = Prefs.notifyLastId(ctx)
            val official = mutableListOf<RomUpdate>()
            val ports = mutableListOf<PortPackage>()

            if (Prefs.notifyOfficial(ctx)) {
                runCatching {
                    val resp = Api.romUpdates(ctx, limit = 120)
                    resp.items.filter { it.id > lastId && subs.contains(it.codename) }
                        .let { official += it }
                }
            }
            if (Prefs.notifyPorts(ctx)) {
                runCatching {
                    val resp = Api.ports(ctx, limit = 120)
                    resp.items.filter { subs.contains(it.codename) }
                        .filter { isNewPort(ctx, it) }
                        .let { ports += it }
                }
            }
            official to ports
        }

    private const val K_SEEN_PORTS = "seen_port_ids"

    /** 这个移植包有没有推过通知（实时通道和后台轮询共用同一份记录，避免重复打扰）。 */
    fun isNewPort(ctx: Context, portId: Long): Boolean {
        val seen = ctx.getSharedPreferences("romhub_prefs", Context.MODE_PRIVATE)
            .getStringSet(K_SEEN_PORTS, emptySet())?.toSet() ?: emptySet()
        return !seen.contains(portId.toString())
    }

    private fun isNewPort(ctx: Context, p: PortPackage): Boolean = isNewPort(ctx, p.id)

    fun markPortsSeen(ctx: Context, ids: List<Long>) {
        val sp = ctx.getSharedPreferences("romhub_prefs", Context.MODE_PRIVATE)
        val seen = (sp.getStringSet(K_SEEN_PORTS, emptySet())?.toSet() ?: emptySet()).toMutableSet()
        ids.forEach { seen.add(it.toString()) }
        // 只保留最近 2000 个，防止无限增长
        val trimmed = seen.toList().takeLast(2000).toSet()
        sp.edit().putStringSet(K_SEEN_PORTS, trimmed).apply()
    }
}
