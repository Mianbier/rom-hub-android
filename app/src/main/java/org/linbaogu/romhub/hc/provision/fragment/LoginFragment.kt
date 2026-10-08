/*
 * This file is part of ROM Hub, released under the GNU Affero General Public License v3.0.
 *
 * 本文件移植 / 改写自 HyperCeiler（AGPL-3.0，Copyright (C) 2023-2026 HyperCeiler
 * Contributions），或为其等价替身实现 —— 完整来源与鸣谢见项目根目录 NOTICE.md。
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.linbaogu.romhub.hc.provision.fragment

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.linbaogu.romhub.R
import org.linbaogu.romhub.hc.provision.ProvisionLogin
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.theme.RomHubTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 引导流程「登录」页的表单状态。
 * Fragment 和 Compose 内容共用同一份，底部的「继续」按钮（由外层 ProvisionBaseActivity
 * 的 Miuix 按钮组提供）才能读到用户填了什么。
 */
class LoginFormState {
    /** 登录 / 申请 两种形态，共用同一张卡片和底部那颗「继续」按钮。 */
    enum class Mode { LOGIN, APPLY }

    var mode by mutableStateOf(Mode.LOGIN)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    // 申请才用到的两项：名称、酷安名（账号那栏填邮箱）
    var name by mutableStateOf("")
    var coolapk by mutableStateOf("")
    var error by mutableStateOf("")
    var info by mutableStateOf("")
    var busy by mutableStateOf(false)
}

/**
 * 登录页的 Fragment —— 只负责中间那块表单（Compose）。
 * 外壳（返回箭头 / 预览图 / 标题 / 底部蓝色按钮组）由 ProvisionBaseActivity 统一提供，
 * 所以它和权限页、协议页、基础设置页长得一模一样。
 */
class LoginFragment : BaseFragment() {

    private val form = LoginFormState()

    override fun getLayoutId(): Int = R.layout.provision_login_layout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val composeView = view.findViewById<ComposeView>(R.id.login_compose)

        // ⚠ 关键：引导页的 Activity 是 `fan.appcompat.app.AppCompatActivity`，它自己重写了
        // `setContentView`，**没有走 androidx 的 initializeViewTreeOwners()** →
        // 视图树里没有 ViewTreeLifecycleOwner → ComposeView 一 attach 就抛
        // `IllegalStateException: ViewTreeLifecycleOwner not found`（点「继续」直接闪退）。
        // 这里手动把 Fragment 自己挂成 owner。
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            RomHubTheme {
                LoginForm(form) {
                    // 游客：直接写会话并推进到「开始使用」。
                    continueAsGuest()
                    (activity as? org.linbaogu.romhub.hc.provision.activity.LoginActivity)
                        ?.finishWithOk()
                }
            }
        }
    }

    /**
     * 点底部「继续」时调用（外层 Activity 转发过来）。
     * 申请形态下它提交申请，登录形态下它登录。
     * @param onResult 主线程回调；null = 成功。
     */
    fun submit(onResult: (String?) -> Unit) {
        if (form.busy) return
        val ctx = context ?: run { onResult("页面已关闭"); return }

        if (form.mode == LoginFormState.Mode.APPLY) {
            form.busy = true
            form.error = ""
            form.info = ""
            ProvisionLogin.apply(ctx, form.username, form.name, form.coolapk, form.password) { err ->
                form.busy = false
                if (err != null) {
                    form.error = err
                    // 申请失败**不要**推进流程（onResult(null) 会跳到下一页）
                } else {
                    // 成功就切回登录、把账号密码留着，并提示等站长审核
                    form.info = "已提交，等站长审核通过后就能用这个账号登录了。"
                    form.mode = LoginFormState.Mode.LOGIN
                }
            }
            return
        }

        if (form.username.isBlank() || form.password.isBlank()) {
            form.error = "请填写账号和密码，或点「跳过」用游客身份进入"
            return
        }
        form.busy = true
        form.error = ""
        ProvisionLogin.dev(ctx, form.username, form.password) { err ->
            form.busy = false
            if (err != null) form.error = err
            onResult(err)
        }
    }

    /** 点底部「跳过」时调用 —— 以游客身份继续。 */
    fun continueAsGuest() {
        val ctx = context ?: return
        ProvisionLogin.guest(ctx)
    }
}

/**
 * 表单本体：一张实底卡片 + 两个 Miuix 输入框。
 * 用的是 `top.yukonga.miuix.kmp.basic.TextField` —— 和 HyperCeiler 设置页里的输入框同一套。
 */
@Composable
private fun LoginForm(form: LoginFormState, onGuest: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (form.mode == LoginFormState.Mode.APPLY) "申请开发者账号" else "开发者账号",
                fontSize = MiuixTheme.textStyles.body1.fontSize,
            )
            Spacer(Modifier.height(12.dp))

            TextField(
                value = form.username,
                onValueChange = { form.username = it; form.error = "" },
                // 申请时账号就是邮箱（站长要求的），所以这里说清楚
                label = if (form.mode == LoginFormState.Mode.APPLY) "账号（填邮箱）" else "账号",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.mode == LoginFormState.Mode.APPLY) {
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = form.name,
                    onValueChange = { form.name = it; form.error = "" },
                    label = "名称",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = form.coolapk,
                    onValueChange = { form.coolapk = it; form.error = "" },
                    label = "酷安名",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            TextField(
                value = form.password,
                onValueChange = { form.password = it; form.error = "" },
                label = if (form.mode == LoginFormState.Mode.APPLY) "密码（≥6 位）" else "密码",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    form.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.error,
                )
            }
            if (form.info.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    form.info,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(6.dp))
            if (form.mode == LoginFormState.Mode.APPLY) {
                // 申请完直接切回登录，账号密码都留着，省得再输一遍
                GhostButton(
                    text = "返回登录",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        form.mode = LoginFormState.Mode.LOGIN
                        form.error = ""
                        form.info = ""
                    },
                )
            } else {
                // 没有开发者账号也能进：HyperCeiler 的底部按钮组默认只显示主按钮，
                // 「跳过」被隐藏了，所以游客入口直接放在表单里，不依赖底部面板。
                GhostButton(
                    text = "没有账号？以游客身份继续",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGuest,
                )
                Spacer(Modifier.height(6.dp))
                // 申请入口 —— 之前只有主界面那套登录页有，引导流程这套漏了，
                // 用户在引导页里根本找不到申请的地方。
                GhostButton(
                    text = "申请开发者账号",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        form.mode = LoginFormState.Mode.APPLY
                        form.error = ""
                        form.info = ""
                    },
                )
            }
        }
    }
}
