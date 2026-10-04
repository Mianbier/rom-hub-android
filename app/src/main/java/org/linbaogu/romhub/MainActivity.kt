package org.linbaogu.romhub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.linbaogu.romhub.ui.AppRoot
import org.linbaogu.romhub.ui.AppViewModel
import org.linbaogu.romhub.ui.theme.RomHubTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 通知点进来时带的 romhub:// 深链
        vm.handleDeepLink(intent?.data?.toString())

        setContent {
            RomHubTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleDeepLink(intent.data?.toString())
    }

    override fun onResume() {
        super.onResume()
        vm.refreshSession()
        // 实时：切回前台立刻拉一次动态（含未读数，底栏小红点）
        vm.refreshFeedNow()
        vm.refreshStats()
        // 接上实时更新通道（服务端有新包会立刻推过来）
        vm.onForeground()
    }

    override fun onPause() {
        super.onPause()
        // 离开前台：断开实时通道，改由系统调度的兜底轮询接管
        vm.onBackground()
    }
}
