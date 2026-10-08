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

        // 照抄过来的 provision 代码里 PrefsBridge.putByApp 是无 Context 的静态接口，先喂它 app context
        org.linbaogu.romhub.hc.common.PrefsBridge.initForApp(this)

        // ── 首次启动门禁：没走过 HyperCeiler 的引导流程就先进引导 ──
        // 流程：用户协议 → 基础设置 → 权限申请 → 完成（照抄 library/provision）
        if (!fan.provision.OobeUtils.isProvisioned(this)) {
            runCatching {
                startActivity(
                    Intent(
                        this,
                        org.linbaogu.romhub.hc.provision.activity.DefaultActivity::class.java,
                    )
                )
            }
            finish()
            return
        }

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
