package org.linbaogu.romhub

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.notify.Notifications
import org.linbaogu.romhub.notify.NotifyScheduler

class RomHubApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 自愈：桌面图标开关默认开。万一之前被关掉（启动器组件被禁用 → 桌面找不到图标），
        // 只要进程还能被拉起来就立刻把组件恢复，避免用户彻底进不去。
        runCatching {
            if (org.linbaogu.romhub.hc.common.AppSettingsStore.isHideAppIconEnabled(this)) {
                packageManager.setComponentEnabledSetting(
                    android.content.ComponentName(this, MainActivity::class.java),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP,
                )
            }
        }

        // 通知渠道（官方包 / 移植包两个，可分别关闭）+ 后台兜底轮询
        Notifications.ensureChannels(this)
        NotifyScheduler.schedule(this)

        // 代理配置：必须在任何网络请求之前套上，否则首次请求会走直连
        // 开关关着时这里是直连，行为不变。
        org.linbaogu.romhub.core.Prefs.applyProxy(this)

        // 下载器：恢复上次没下完的任务（统一落成「已暂停」，等用户点继续）
        DownloadManager.init(this)

        // 机型图片走 OkHttp 加载
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }
}
