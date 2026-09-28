package org.linbaogu.romhub.notify

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.linbaogu.romhub.core.Prefs
import java.util.concurrent.TimeUnit

object NotifyScheduler {

    private const val PERIODIC = "romhub_poll_updates"

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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
    }

    // ------------------------------------------------------------ 后台常驻监听

    /** 开启 / 关闭前台常驻监听服务。 */
    fun setBackgroundListen(ctx: Context, on: Boolean) {
        Prefs.setNotifyBackground(ctx, on)
        val i = Intent(ctx, NotifyForegroundService::class.java)
        if (on) {
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        } else {
            ctx.stopService(i)
        }
    }
}
