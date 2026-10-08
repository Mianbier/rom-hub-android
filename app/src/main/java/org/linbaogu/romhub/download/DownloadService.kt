package org.linbaogu.romhub.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.linbaogu.romhub.MainActivity
import org.linbaogu.romhub.R

/**
 * 下载用的前台服务。
 *
 * 作用只有一个：**让系统别在后台把下载掐死**，顺带在通知栏显示进度。
 * 任务全部结束就自己停掉，不留常驻通知 —— 符合 ROM Hub 一贯的「不常驻后台」原则。
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        registerNetworkCallback()
    }

    /**
     * 网络回调要**存成成员**。
     *
     * `registerDefaultNetworkCallback(cb)` 内部只持有回调的弱引用，
     * 如果 cb 是个局部变量，方法一返回就没人引用它了 —— GC 随时可能收掉，
     * 之后系统再也不会回调 onAvailable，表现为「连回 Wi-Fi 后任务一直不自动开始」。
     * 而且这种情况是偶发的（要等 GC），最难排查。所以这里必须抓着不放。
     */
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /**
     * 监听网络变化：从蜂窝切回 Wi-Fi 时，把被「仅 Wi-Fi 下载」拦下的任务放出去。
     *
     * 用户勾了"仅 Wi-Fi"时，
     * 出门用流量时任务排队，回家连上 Wi-Fi 应该自动开始，而不是要用户手动点。
     */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        runCatching {
            val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    DownloadManager.onNetworkChanged()
                }
                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    caps: android.net.NetworkCapabilities,
                ) {
                    DownloadManager.onNetworkChanged()
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(android.net.ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(cb)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏按钮：先按 action 处理，再刷新通知
        when (intent?.action) {
            ACTION_PAUSE_ALL -> DownloadManager.pauseAll()
            ACTION_RESUME_ALL -> DownloadManager.resumeAll()
            ACTION_CANCEL_ALL -> {
                DownloadManager.tasks.value.filter { it.isActive || it.isPaused }
                    .forEach { DownloadManager.remove(it.id, deleteFile = true) }
            }
        }
        val tasks = DownloadManager.tasks.value
        runCatching {
            startForeground(NOTIFY_ID, build(this, tasks), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        if (tasks.none { it.isActive }) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    companion object {
        private const val CHANNEL_ID = "romhub_download"
        private const val NOTIFY_ID = 9001

        /** 通知栏按钮的 action（自己发给自己，走 onStartCommand） */
        private const val ACTION_PAUSE_ALL = "org.linbaogu.romhub.dl.PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "org.linbaogu.romhub.dl.RESUME_ALL"
        private const val ACTION_CANCEL_ALL = "org.linbaogu.romhub.dl.CANCEL_ALL"

        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) }
            runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFY_ID) }
        }

        /** 进度刷新（由 DownloadManager 的秒级 ticker 调）。 */
        fun refresh(ctx: Context, tasks: List<DownloadTask>) {
            if (tasks.none { it.isActive }) {
                runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFY_ID) }
                return
            }
            ensureChannel(ctx)
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFY_ID, build(ctx, tasks)) }
        }

        private fun ensureChannel(ctx: Context) {
            val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "正在下载文件时显示的进度"
                        setShowBadge(false)
                    }
                )
            }
        }

        private fun build(ctx: Context, tasks: List<DownloadTask>): Notification {
            val active = tasks.filter { it.isActive }
            // 用单调进度算总量：跟界面卡片保持一致的观感（不会往回缩）
            val doneBytes = tasks.sumOf { it.uiDoneBytes }
            val totalBytes = tasks.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
            val current = active.firstOrNull() ?: tasks.lastOrNull()

            val title = when {
                active.size > 1 -> "正在下载 ${active.size} 个文件"
                active.size == 1 -> current?.fileName.orEmpty().ifBlank { "正在下载" }
                else -> "下载已暂停"
            }

            val text = current?.let {
                val pct = (it.uiProgress * 100).toInt()
                if (it.totalBytes > 0) {
                    "$pct%  ${formatBytes(it.uiDoneBytes)} / ${formatBytes(it.totalBytes)}"
                } else {
                    formatBytes(it.uiDoneBytes)
                }
            } ?: ""

            val intent = Intent(ctx, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("romhub://download")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            // 通知栏直接可控：跑着就显示「暂停」，暂停了就显示「继续」。
            // 点一下就走 DownloadManager，不依赖 App 在前台 —— 息屏时也能操作。
            val anyRunning = active.isNotEmpty()
            builder.addAction(
                if (anyRunning) R.drawable.ic_pause else R.drawable.ic_resume,
                if (anyRunning) "暂停" else "继续",
                servicePendingIntent(
                    ctx,
                    if (anyRunning) ACTION_PAUSE_ALL else ACTION_RESUME_ALL,
                    requestCode = if (anyRunning) 1 else 2,
                ),
            )
            builder.addAction(
                R.drawable.ic_close,
                "取消",
                servicePendingIntent(ctx, ACTION_CANCEL_ALL, requestCode = 3),
            )

            if (totalBytes > 0L) {
                builder.setProgress(
                    1000,
                    ((doneBytes.toDouble() / totalBytes) * 1000).toInt().coerceIn(0, 1000),
                    false,
                )
            } else {
                builder.setProgress(0, 0, true)
            }
            return builder.build()
        }

        /** 指向本服务的 PendingIntent（通知栏按钮走这里）。 */
        private fun servicePendingIntent(
            ctx: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val i = Intent(ctx, DownloadService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                ctx, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
