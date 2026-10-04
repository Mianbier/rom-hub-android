package org.linbaogu.romhub.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 更新提醒的调度。
 *
 * 整个通知机制分两层，**都不依赖 App 常驻后台**：
 *
 *  ① 实时层 —— UpdateStream：App 在前台时挂一条 SSE 长连接，服务端有新动态秒推过来。
 *                            这是「延迟不超过一分钟」的主力。
 *  ② 兜底层 —— NotifyWorker ：App 不在前台（甚至被杀掉）时由系统调度，15 分钟一轮。
 *                            国产 ROM 上会被 Doze/省电策略推迟，所以只当兜底。
 *
 * 退到后台时额外排一次「1 分钟后再看一眼」的一次性任务：覆盖「刚发完包就锁屏」这种
 * 最常见的情况，让绝大多数时候实际感受到的延迟都在一分钟以内。
 */
object NotifyScheduler {

    private const val PERIODIC = "romhub_poll_updates"
    private const val TAIL_ONCE = "romhub_poll_tail"

    fun schedule(ctx: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    /**
     * 用户刚退到后台：排一个 1 分钟后的一次性任务，把「刚发出去的包」补上。
     * 同一时间只保留一个，避免反复进出前台堆一堆任务。
     */
    fun scheduleTail(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            TAIL_ONCE,
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(ctx).cancelUniqueWork(TAIL_ONCE)
    }
}
