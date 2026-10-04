package org.linbaogu.romhub.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.Repo

/**
 * 兜底轮询：App 不在前台时由系统 15 分钟调度一次。
 *
 * 只收**已订阅机型**的新更新；通知的标题是「机型 · 更新类型」、
 * 下面一行小字是版本号，点击直接进对应版本 / 移植包页面。
 *
 * 它是第二道防线 —— 前台那层实时通道（UpdateStream）负责秒级到达，
 * 这层只负责「App 长时间没打开」时不错过。
 */
class NotifyWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        if (!Prefs.notifyEnabled(ctx)) return@withContext Result.success()

        // 顺手刷新动态快照，让未读数/底栏小红点也跟上
        runCatching { Repo.refreshFeed(ctx, limit = 80) }
        runCatching { Repo.initFeedBaselineIfNeeded(ctx) }

        val (official, ports) = runCatching { Repo.pollNewUpdates(ctx) }
            .getOrDefault(emptyList<org.linbaogu.romhub.data.RomUpdate>() to emptyList())

        val subs = Prefs.subscriptions(ctx)
        var maxId = Prefs.notifyLastId(ctx)

        official.asSequence()
            .filter { subs.contains(it.codename) }      // 再兜一次：只认订阅机型
            .filter { it.id > Prefs.notifyLastId(ctx) }
            .take(8)
            .forEach { u ->
                val ver = u.newVersion.ifBlank { u.versionId.toString() }
                val extra = listOfNotNull(
                    u.branchZh.takeIf { it.isNotBlank() },
                    u.stateZhSafe(),
                ).joinToString(" · ")
                Notifications.showUpdate(
                    ctx = ctx,
                    id = 10_000 + (u.id % 1_000_000L).toInt(),
                    device = u.deviceName,
                    codename = u.codename,
                    kind = u.kind,
                    version = ver,
                    summary = extra,
                    deepLink = Notifications.versionLink(
                        u.codename, u.region, u.branch, ver,
                    ),
                )
                if (u.id > maxId) maxId = u.id
            }

        if (official.isNotEmpty()) Prefs.setNotifyLastId(ctx, maxId)

        ports.asSequence()
            .filter { subs.contains(it.codename) }
            .filter { Repo.isNewPort(ctx, it.id) }
            .take(8)
            .forEach { p ->
                val title = p.title.ifBlank { p.fileName.ifBlank { "新的移植包" } }
                val extra = listOfNotNull(
                    p.version.takeIf { it.isNotBlank() },
                    p.author.takeIf { it.isNotBlank() }?.let { "作者：$it" },
                ).joinToString(" · ")
                Notifications.showUpdate(
                    ctx = ctx,
                    id = 20_000 + (p.id % 1_000_000L).toInt(),
                    device = p.deviceName,
                    codename = p.codename,
                    kind = "port",
                    version = title,
                    summary = extra,
                    deepLink = Notifications.portLink(p.id),
                )
            }
        Repo.markPortsSeen(ctx, ports.map { it.id })

        Result.success()
    }

    /** state 有时是英文 raw 值，中文优先用 stateText。 */
    private fun org.linbaogu.romhub.data.RomUpdate.stateZhSafe(): String? =
        (stateText.ifBlank { state }).ifBlank { kindZh }.takeIf { it.isNotBlank() }
}
