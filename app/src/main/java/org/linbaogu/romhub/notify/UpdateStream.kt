package org.linbaogu.romhub.notify

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.data.RomJson
import org.linbaogu.romhub.ui.notify.canPostNotifications
import java.util.concurrent.TimeUnit

/** 服务端实时通道推来的一条新动态（字段对应 server/app/events.py 广播的内容）。 */
@Serializable
data class StreamUpdateEvent(
    @SerialName("seq") val seq: Long = 0,
    @SerialName("id") val id: Long = 0,
    @SerialName("codename") val codename: String = "",
    @SerialName("region") val region: String = "",
    @SerialName("branch") val branch: String = "",
    @SerialName("old_version") val oldVersion: String = "",
    @SerialName("new_version") val newVersion: String = "",
    @SerialName("version_id") val versionId: Long? = null,
    @SerialName("port_id") val portId: Long? = null,
    @SerialName("kind") val kind: String = "",
    @SerialName("device_name") val deviceName: String = "",
)

/**
 * 前台实时更新通道。
 *
 * 只在 App 位于前台时挂这一条连接：服务端一有新动态立刻推过来（通常 1~3 秒内到通知栏），
 * **不需要前台常驻服务、不留常驻通知、App 杀掉后不占任何资源**。
 *
 * 退后台就断开（App 自己的 onPause 会调 stop），剩下的事交给 WorkManager 兜底轮询。
 * 断线会自动重连，重连时带上次收到的 seq，把断线期间漏掉的事件补回来。
 */
class UpdateStream(private val ctx: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var call: Call? = null

    /** 最近一次「连上了」的时间戳；0 表示当前没连着（给「关于」页显示状态用）。 */
    @Volatile
    var connectedAt: Long = 0
        private set

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var retryMs = RETRY_MIN_MS
            while (isActive) {
                val ok = runCatching { connectOnce() }.getOrDefault(false)
                if (ok) retryMs = RETRY_MIN_MS else delay(retryMs)
                if (retryMs < RETRY_MAX_MS) retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { call?.cancel() }
        call = null
        connectedAt = 0
    }

    /** @return true 表示这条连接是正常结束（说明服务端活着），false 表示异常需要重连。 */
    private suspend fun connectOnce(): Boolean {
        // 一个机型都没订 -> 一条通知都不该来，连都没必要连
        if (!Prefs.notifyEnabled(ctx)) {
            delay(CHECK_IDLE_MS)
            return true
        }
        val codes = Prefs.subscriptions(ctx)
        if (codes.isEmpty()) {
            delay(CHECK_IDLE_MS)
            return true
        }
        if (!canPostNotifications(ctx)) {
            delay(CHECK_IDLE_MS)
            return true
        }

        val since = Prefs.streamLastSeq(ctx)
        val url = Api.currentBase(ctx).trimEnd('/') +
                "/api/updates/stream?since=$since&codes=" +
                codes.joinToString(",") { enc(it) }

        val cli: OkHttpClient = Api.client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)   // 长连接：不能设读超时
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val c = cli.newCall(req)
        call = c
        return try {
            c.execute().use { resp ->
                if (!resp.isSuccessful) return false
                connectedAt = System.currentTimeMillis()
                val reader = resp.body.charStream()
                reader.useLines { lines ->
                    lines.forEach { raw ->
                        if (raw.startsWith("data:")) {
                            runCatching { handle(raw.removePrefix("data:").trim()) }
                        }
                    }
                }
            }
            true
        } finally {
            call = null
            connectedAt = 0
        }
    }

    /** 处理一条推来的动态：过滤 -> 去重 -> 发系统通知。 */
    private fun handle(raw: String) {
        val e = runCatching { RomJson.decodeFromString<StreamUpdateEvent>(raw) }
            .getOrNull() ?: return
        if (e.id <= 0) return
        if (e.seq > Prefs.streamLastSeq(ctx)) Prefs.setStreamLastSeq(ctx, e.seq)

        // ① 严格按订阅机型过滤：没订阅的一律不通知
        val subs = Prefs.subscriptions(ctx)
        if (subs.isEmpty() || !subs.contains(e.codename)) return
        // ② 分类开关（官方包 / 移植包可以在「关于」页分别关）
        val isPort = e.kind == "port"
        if (!Prefs.notifyEnabled(ctx)) return
        if (isPort && !Prefs.notifyPorts(ctx)) return
        if (!isPort && !Prefs.notifyOfficial(ctx)) return
        if (!canPostNotifications(ctx)) return

        // ③ 去重：同一条动态只通知一次（实时通道和后台轮询共用记录）
        if (isPort) {
            val pid = e.portId ?: return
            if (!Repo.isNewPort(ctx, pid)) return
            Repo.markPortsSeen(ctx, listOf(pid))
        } else {
            if (e.id <= Prefs.notifyLastId(ctx)) return
            Prefs.setNotifyLastId(ctx, e.id)
        }

        val version = if (isPort) {
            e.newVersion.ifBlank { "新的移植包" }
        } else {
            e.newVersion.ifBlank { e.versionId?.toString().orEmpty() }
        }
        val deepLink = if (isPort && e.portId != null) {
            Notifications.portLink(e.portId)
        } else {
            Notifications.versionLink(e.codename, e.region, e.branch, version)
        }

        Notifications.showUpdate(
            ctx = ctx,
            id = NOTI_ID_BASE + (e.id % 1_000_000L).toInt() + if (isPort) 20_000 else 10_000,
            device = e.deviceName,
            codename = e.codename,
            kind = e.kind,
            version = version,
            deepLink = deepLink,
        )
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val RETRY_MIN_MS = 3_000L
        private const val RETRY_MAX_MS = 60_000L
        /** 暂时不需要连接时（没订阅 / 关了通知）空转的复查间隔。 */
        private const val CHECK_IDLE_MS = 30_000L
        private const val NOTI_ID_BASE = 40_000
    }
}
