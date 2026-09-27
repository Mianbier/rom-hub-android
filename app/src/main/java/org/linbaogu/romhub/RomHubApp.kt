package org.linbaogu.romhub

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.linbaogu.romhub.notify.Notifications
import org.linbaogu.romhub.notify.NotifyScheduler

class RomHubApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 通知渠道 + 后台轮询（订阅更新提醒）
        Notifications.ensureChannel(this)
        NotifyScheduler.schedule(this)

        // 机型图片走 OkHttp 加载
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }
}
