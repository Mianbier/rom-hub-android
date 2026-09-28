package org.linbaogu.romhub.notify

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.linbaogu.romhub.MainActivity
import org.linbaogu.romhub.R
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.Repo

/**
 * 后台常驻监听：App 退到后台后仍然定时拉动态，有新的就发系统通知。
 *
 * 为什么需要它：
 *   WorkManager 的周期任务**最短 15 分钟**，而且国产 ROM 上常被 Doze / 省电策略
 *   推迟到几十分钟甚至更久 —— 用户发了包，要等很久才收到提醒。
 *   改用前台服务常驻，间隔可以压到 60 秒，后台也能较快收到。
 *
 * 代价：通知栏会常驻一条「正在监听更新」的通知（这是 Android 对前台服务的
 *       硬性要求，也方便用户随时一键关掉）。默认关闭，用户在
 *       「关于 → 订阅更新提醒 → 后台实时提醒」里自己开启。
 */
class NotifyForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTI_ID, buildForegroundNotification())
        startPolling()
    }

    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            runCatching { Repo.initFeedBaselineIfNeeded(this@NotifyForegroundService) }
            while (isActive) {
                runCatching { pollOnce() }
                delay(POLL_MS)
            }
        }
    }

    /** 拉一次动态：有比「已通知位置」更新的条目就发通知。 */
    private suspend fun pollOnce() {
        val ctx = this@NotifyForegroundService
        if (!Prefs.notifyEnabled(ctx)) return

        val resp = runCatching { Repo.refreshFeed(ctx, limit = 60) }.getOrNull() ?: return
        val lastId = Prefs.notifyLastId(ctx)
        val fresh = resp.items.filter { it.id > lastId }
        if (fresh.isEmpty()) return

        // 最多一次发 5 条，避免刷屏
        fresh.sortedByDescending { it.id }.take(5).forEach { u ->
            val kind = if (u.kind == "port") "移植包更新" else "官方包更新"
            val ver = u.newVersion.ifBlank { u.versionId.toString() }
            val title = "${u.deviceName.ifBlank { u.codename }} · $kind"
            val body = buildString {
                append("版本 $ver")
                if (u.branchZh.isNotBlank()) append("（${u.branchZh}）")
                if (u.stateText.isNotBlank()) append("\n${u.stateText}")
            }
            val link = "romhub://ver?code=${u.codename}&region=${u.region}" +
                    "&branch=${u.branch}&ver=${java.net.URLEncoder.encode(ver, "UTF-8")}"
            Notifications.show(
                ctx = ctx,
                id = (30_000 + u.id).toInt(),
                title = title,
                body = body,
                bigText = u.desc.ifBlank { body },
                deepLink = link,
            )
        }
        Prefs.setNotifyLastId(ctx, fresh.maxOf { it.id })
    }

    private fun buildForegroundNotification(): Notification {
        Notifications.ensureChannel(this)
        val pi = android.app.PendingIntent.getActivity(
            this,
            NOTI_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("ROM Hub 正在监听更新")
            .setContentText("有新包会通知你 · 点这里打开应用")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        poller?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "org.linbaogu.romhub.action.STOP_LISTEN"
        private const val NOTI_ID = 4001
        private const val POLL_MS = 60_000L
    }
}
