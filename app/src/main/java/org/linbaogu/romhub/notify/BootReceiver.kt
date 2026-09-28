package org.linbaogu.romhub.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用更新后重新排上后台轮询任务。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                NotifyScheduler.schedule(context)
                // 用户开过「后台实时提醒」的话，开机后把常驻服务也拉起来
                if (org.linbaogu.romhub.core.Prefs.notifyBackground(context)) {
                    NotifyScheduler.setBackgroundListen(context, true)
                }
            }
        }
    }
}
