package org.linbaogu.romhub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内置种子数据：装进 APK 的那份「最低可显示内容」。
 *
 * ## 为什么需要
 *
 * 动态和统计的实际取值顺序是：实时接口 → CDN 快照 → 本地缓存 → **内置种子**。
 * 前三级都可能同时为空 —— 刚装完第一次打开、还没联网、CDN 那个域名恰好也解析不了。
 * 这时候如果只回一个空列表，主页就是一片 0、动态页是「暂无动态」，
 * 用户会以为 App 坏了。种子保证**任何情况下都有一个能看的东西**，
 * 并且明确告诉用户这是内置数据、联网后会自动换成实时数据。
 */
internal object Seed {

    /** 首页统计的兜底数字。打包时的真值，联网后立刻被实时数据覆盖。 */
    suspend fun stats(ctx: Context): RomStats? = withContext(Dispatchers.IO) {
        readAsset(ctx, "seed_stats.json")?.let {
            runCatching { RomJson.decodeFromString<RomStats>(it) }.getOrNull()
        }
    }

    private fun readAsset(ctx: Context, name: String): String? = runCatching {
        ctx.applicationContext.assets.open(name).bufferedReader().use { it.readText() }
    }.getOrNull()
}