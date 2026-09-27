package org.linbaogu.romhub.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.linbaogu.romhub.MainActivity
import org.linbaogu.romhub.R

/**
 * 系统通知渠道。用 App 自己的轮询 + NotificationChannel 发本地通知
 * ——不需要任何第三方推送账号。
 */
object Notifications {

    const val CHANNEL_ID = "romhub_updates"

    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "订阅更新",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "订阅的机型有新的官方包或移植包时提醒"
            enableLights(true)
            enableVibration(true)
        }
        mgr.createNotificationChannel(ch)
    }

    /**
     * @param deepLink 形如 romhub://ver?code=..&region=..&branch=..&ver=..
     */
    fun show(
        ctx: Context,
        id: Int,
        title: String,
        body: String,
        deepLink: String,
        bigText: String? = null,
    ) {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(ctx, id, intent, flags)

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText ?: body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup("romhub")
            .setGroupSummary(false)

        runCatching {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        }
    }

    fun clearGroup(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancelAll()
    }
}
