package org.linbaogu.romhub.download

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 「按域名记住最优分片数」：同一个域名下次沿用上次实测好用的分片数。
 *
 * ## 为什么需要它
 *
 * 各家 CDN 对并发的容忍度差得远：
 *   · 有些源站开 64 线程飞快，开 8 线程慢十倍；
 *   · 有些源站超过 16 并发直接被限速/返回 503（百度网盘就是典型）。
 *
 * 思路是**按域名记住试出来的合适分片数**，下次从同一个域名
 * 下载时自动套用，不用每次手动调。它的实现是 SQLite 表：
 * `domain_part_limit(_id, domain, part_limit, use_for_subdomain)`
 *
 * 这里用 JSON 落盘（任务量小，没必要上 Room），逻辑保持一致：
 *   · [partLimitFor] 查域名对应的分片数，没有就返回 0（表示"用全局默认"）
 *   · [remember] 下载成功后把实际生效的片数记下来，下次复用
 *   · `useForSubdomain`：比如 `cdn.a.com` 的设置是否套用到 `a.com` 的子域
 */
object DomainPartLimit {

    private const val FILE_NAME = "domain_parts.json"

    /** 每个域名的记录数上限 —— 防止无限增长。 */
    private const val MAX_ENTRIES = 300

    @Serializable
    data class Entry(
        val domain: String,
        /** 分片数；0 = 未记录 */
        var partLimit: Int = 0,
        /** 是否套用到子域名 */
        var useForSubdomain: Boolean = true,
        var updatedAt: Long = System.currentTimeMillis(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile private var cache: MutableMap<String, Entry>? = null

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    @Synchronized
    private fun load(ctx: Context): MutableMap<String, Entry> {
        cache?.let { return it }
        val map = runCatching {
            val f = file(ctx)
            if (!f.exists() || f.readText().isBlank()) {
                mutableMapOf()
            } else {
                json.decodeFromString<List<Entry>>(f.readText())
                    .associateByTo(mutableMapOf()) { it.domain.lowercase() }
            }
        }.getOrElse { mutableMapOf() }
        cache = map
        return map
    }

    @Synchronized
    private fun save(ctx: Context) {
        runCatching {
            val map = load(ctx)
            // 超出上限就砍掉最老的
            val list = if (map.size > MAX_ENTRIES) {
                map.values.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
            } else {
                map.values.toList()
            }
            file(ctx).writeText(json.encodeToString(list))
        }
    }

    /** 从 URL 里抠出主机名（小写、去端口）。 */
    fun domainOf(url: String): String = runCatching {
        val u = okhttp3.HttpUrl.Companion.run { url.toHttpUrlOrNull() }
            ?: return@runCatching ""
        u.host.lowercase()
    }.getOrDefault("")

    /**
     * 查这个域名该用几个分片。
     *
     * 查不到精确匹配时，往上一级域名找（如果那条记录开了 [Entry.useForSubdomain]）。
     * 返回 0 = 没记录，调用方用自己的默认值。
     */
    fun partLimitFor(ctx: Context, url: String): Int {
        val host = domainOf(url)
        if (host.isBlank()) return 0
        val map = load(ctx)

        map[host]?.takeIf { it.partLimit > 0 }?.let { return it.partLimit }

        // 逐级往上找父域：cdn.dl.example.com → dl.example.com → example.com
        val parts = host.split('.')
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            map[parent]?.let { e ->
                if (e.useForSubdomain && e.partLimit > 0) return e.partLimit
            }
        }
        return 0
    }

    /** 记下某个域名实测好用的分片数。 */
    fun remember(ctx: Context, url: String, partLimit: Int, useForSubdomain: Boolean = true) {
        val host = domainOf(url)
        if (host.isBlank() || partLimit <= 0) return
        val map = load(ctx)
        map[host] = Entry(
            domain = host,
            partLimit = partLimit,
            useForSubdomain = useForSubdomain,
            updatedAt = System.currentTimeMillis(),
        )
        save(ctx)
    }

    /** 列出所有已记录域名（给设置页展示/编辑用）。 */
    fun list(ctx: Context): List<Entry> =
        load(ctx).values.sortedBy { it.domain }

    fun remove(ctx: Context, domain: String) {
        load(ctx).remove(domain.lowercase())
        save(ctx)
    }

    fun clear(ctx: Context) {
        load(ctx).clear()
        save(ctx)
    }
}
