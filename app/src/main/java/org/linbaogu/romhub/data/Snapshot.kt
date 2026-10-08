package org.linbaogu.romhub.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.linbaogu.romhub.core.Prefs
import java.util.concurrent.TimeUnit

/**
 * 静态快照通道：从 CDN 边缘直接读 JSON，**不经过任何一台需要人看管的机器**。
 *
 * ## 它是兜底，不是替代
 *
 * 浏览类数据（主页统计、动态、机型表）占了使用量的绝大部分，但**实时性不能牺牲**，
 * 所以主路径仍然是实时接口：动态走 REST + SSE 推送（服务端有新动态 1~3 秒到），
 * 主页统计/机型表走 REST 拉取。
 *
 * 这条通道解决的是另一件事：**维护机器掉线时 App 不会白屏**。
 * 服务端早就把只读数据导出成静态快照（server/static_export 下的 json）推到 Cloudflare
 * Workers KV，边缘直接返回 —— 那条路上没有任何一台电脑/手机参与，
 * 所以维护机器关机、换网络、Termux 掉线时，用户仍然能看到最后一次的数据。
 *
 * 写操作、以及必须实时的能力（开发者投稿、上传直链、更新检查）永远只走 [Api]。
 *
 * ## 取值顺序
 *
 * 实时接口 → 快照 → 本地缓存。三级都空才报错，
 * 保证任何一环挂了主页和动态都还能打开，只是数据旧一点。
 */
internal object Snapshot {

    private const val TAG = "RomHubSnap"

    /** 边缘有 15 秒缓存，这里给足超时但仍然比源站短，坏了就赶紧换下一级。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 快照地址候选。
     *
     * 默认公网域名走 Cloudflare Worker（永远在）；如果用户把服务器地址改成局域网/本机，
     * 也顺手试一下那个地址 —— 服务端以后把 /data 挂出来就能直接命中，不用再改客户端。
     */
    private fun bases(ctx: Context): List<String> {
        val out = LinkedHashSet<String>()
        out += Prefs.DEFAULT_BASE
        val cur = Prefs.serverBase(ctx).trimEnd('/')
        if (cur.isNotBlank() && cur != Prefs.DEFAULT_BASE) out += cur
        return out.toList()
    }

    /**
     * 读一份快照文件。任一候选地址命中即返回，全部失败返回 null。
     *
     * 用 GET 而不是 HEAD：KV 上的内容就是文件体，一次请求拿到，省一次往返。
     */
    suspend fun text(ctx: Context, name: String): String? = withContext(Dispatchers.IO) {
        for (base in bases(ctx)) {
            val url = base.trimEnd('/') + "/data/" + name
            val body = runCatching {
                client.newCall(Request.Builder().url(url).get().build())
                    .execute()
                    .use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()
            if (!body.isNullOrBlank()) {
                Log.i(TAG, "快照命中 $url（${body!!.length} 字节）")
                return@withContext body
            }
        }
        Log.w(TAG, "快照全部候选都没命中：$name")
        null
    }

    suspend inline fun <reified T> get(ctx: Context, name: String): T? {
        val raw = text(ctx, name) ?: return null
        return runCatching { RomJson.decodeFromString<T>(raw) }
            .onFailure { Log.w(TAG, "快照 $name 解析失败：${it.message}") }
            .getOrNull()
    }
}