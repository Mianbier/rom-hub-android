package org.linbaogu.romhub.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.Repo

/**
 * 后台轮询：定时看一遍订阅机型有没有新的官方包 / 移植包，有就发系统通知。
 *
 * 通知内容会写清「官方包还是移植包」以及版本号；
 * 点击后通过 romhub:// 深链直接跳到对应页面。
 */
class NotifyWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Prefs.notifyEnabled(ctx)) return Result.success()

        // 顺手把动态快照刷新，这样小红点/未读数在后台也是准的
        runCatching { Repo.refreshFeed(ctx, limit = 80) }
        runCatching { Repo.initFeedBaselineIfNeeded(ctx) }

        val (official, ports) = runCatching { Repo.pollNewUpdates(ctx) }
            .getOrDefault(emptyList<org.linbaogu.romhub.data.RomUpdate>() to emptyList())

        if (official.isEmpty() && ports.isEmpty()) return Result.success()

        var n = 0
        var maxUpdateId = Prefs.notifyLastId(ctx)

        official.take(8).forEach { u ->
            val isOfficial = u.kind != "port"
            val kindLabel = if (isOfficial) "官方包更新" else "移植包更新"
            val ver = u.newVersion.ifBlank { u.versionId.toString() }
            val state = u.state.ifBlank { u.kindZh }.ifBlank { "" }
            val title = "${u.deviceName.ifBlank { u.codename }} · $kindLabel"
            val body = buildString {
                append("版本 $ver")
                if (u.branchZh.isNotBlank()) append("（${u.branchZh}）")
                if (state.isNotBlank()) append(" · $state")
                if (u.stateText.isNotBlank()) append("\n${u.stateText}")
            }
            val link = "romhub://ver?code=${u.codename}&region=${u.region}" +
                    "&branch=${u.branch}&ver=${enc(ver)}"
            Notifications.show(
                ctx = ctx,
                id = (10_000 + u.id).toInt(),
                title = title,
                body = body,
                bigText = u.desc.ifBlank { body },
                deepLink = link,
            )
            if (u.id > maxUpdateId) maxUpdateId = u.id
            n++
        }

        ports.take(8).forEach { p ->
            val title = "${p.deviceName.ifBlank { p.codename }} · 移植包更新"
            val body = buildString {
                append(p.title.ifBlank { p.fileName.ifBlank { "新的移植包" } })
                if (p.version.isNotBlank()) append(" · ${p.version}")
                if (p.author.isNotBlank()) append("\n作者：${p.author}")
            }
            Notifications.show(
                ctx = ctx,
                id = (20_000 + p.id).toInt(),
                title = title,
                body = body,
                bigText = (p.notice.ifBlank { body }),
                deepLink = "romhub://port?id=${p.id}",
            )
            n++
        }

        Prefs.setNotifyLastId(ctx, maxUpdateId)
        Repo.markPortsSeen(ctx, ports.map { it.id })
        return Result.success()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
