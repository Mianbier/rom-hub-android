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

        // 通知渠道（官方包 / 移植包两个，可分别关闭）+ 后台兜底轮询
        Notifications.ensureChannels(this)
        NotifyScheduler.schedule(this)

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
