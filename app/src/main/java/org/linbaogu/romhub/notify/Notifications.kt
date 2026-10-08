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
 * 系统通知。
 *
 * 两个渠道，用户可以在系统设置里**分别关掉**其中一类：
 *   romhub_official · 官方包更新（含正式版 / 内测版 / 新机型上线）
 *   romhub_port    · 移植包更新
 *
 * 每条通知统一长这样：
 *
 *   REDMI K100 Pro · 官方包更新      ← 标题：机型 + 更新类型
 *   OS3.0.9.9.WPICNXM                ← 小字：版本号
 *
 * 点击后走 romhub:// 深链，直接打开对应的版本页 / 移植包详情页。
 */
object Notifications {

    const val CH_OFFICIAL = "romhub_official"
    const val CH_PORT = "romhub_port"

    /** 更新类型 → 标题里的中文标签。 */
    fun kindLabel(kind: String): String = when (kind) {
        "port" -> "移植包更新"
        "beta" -> "内测版更新"
        "device" -> "新机型上线"
        else -> "官方包更新"
    }

    /** 这条通知属于哪个渠道（决定用户能不能单独关掉它）。 */
    fun channelOf(kind: String): String =
        if (kind == "port") CH_PORT else CH_OFFICIAL

    fun ensureChannels(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CH_OFFICIAL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CH_OFFICIAL, "官方包更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "订阅的机型有新的官方包、内测版本时提醒"
                    enableLights(true)
                    enableVibration(true)
                }
            )
        }
        if (mgr.getNotificationChannel(CH_PORT) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CH_PORT, "移植包更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "订阅的机型有新移植包上传时提醒"
                    enableLights(true)
                    enableVibration(true)
                }
            )
        }
    }

    /**
     * 发一条更新通知。
     *
     * @param device  机型名（没有就用代号兜底）
     * @param kind    更新类型 kind 字段，决定标题标签与渠道
     * @param version 版本号，作为标题下的那行小字
     * @param summary 展开后的补充信息（更新说明、作者等），没有就留空
     * @param deepLink 点击要跳到的页面
     * @param id      通知 id（同一条更新反复推要复用同一个 id，避免刷屏）
     */
    fun showUpdate(
        ctx: Context,
        id: Int,
        device: String,
        codename: String,
        kind: String,
        version: String,
        summary: String = "",
        deepLink: String,
    ) {
        ensureChannels(ctx)

        val label = kindLabel(kind)
        val title = "${device.ifBlank { codename }} · $label"
        val body = version.ifBlank { "点击查看详情" }
        val big = listOf(version, summary).filter { it.isNotBlank() }.joinToString("\n")

        ensureChannels(ctx)
        show(
            ctx = ctx,
            id = id,
            channel = channelOf(kind),
            title = title,
            body = body,
            bigText = big,
            deepLink = deepLink,
            group = "dev_" + codename.ifBlank { "misc" },
        )
    }

    private fun show(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        bigText: String,
        deepLink: String,
        group: String,
    ) {
        ensureChannels(ctx)
        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText.ifBlank { body }))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent(ctx, id, deepLink))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(group)

        runCatching { NotificationManagerCompat.from(ctx).notify(id, builder.build()) }
    }

    /** 点击通知要跳的页面（普通通知、HyperOS 超级岛、ColorOS 胶囊三条路共用）。 */
    internal fun pendingIntent(ctx: Context, id: Int, deepLink: String): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 官方包/版本类的深链（跳到该机型的版本列表并高亮这个版本）。 */
    fun versionLink(codename: String, region: String, branch: String, version: String): String =
        "romhub://ver?code=$codename" +
                "&region=${enc(region.ifBlank { "all" })}" +
                "&branch=${enc(branch.ifBlank { "all" })}" +
                "&ver=${enc(version)}"

    /** 移植包深链（跳到移植包详情页）。 */
    fun portLink(portId: Long): String = "romhub://port?id=$portId"

    fun clearAll(ctx: Context) = NotificationManagerCompat.from(ctx).cancelAll()

    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
