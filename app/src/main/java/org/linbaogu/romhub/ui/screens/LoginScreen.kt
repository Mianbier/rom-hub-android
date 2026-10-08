package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.network.HelpLinks
import org.linbaogu.romhub.ui.AppViewModel
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import org.linbaogu.romhub.ui.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class Mode { CHOOSE, DEV_LOGIN, DEV_APPLY }

@Composable
fun LoginScreen(vm: AppViewModel) {
    var mode by remember { mutableStateOf(Mode.CHOOSE) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // 申请表单：站长要求的四样 —— 账号（邮箱）/ 名称 / 酷安名 / 密码
    var name by remember { mutableStateOf("") }
    var coolapk by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        isFullSize = true,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 22.dp)
                .padding(top = 84.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo()
            Spacer(Modifier.height(16.dp))
            Text(
                "ROM Hub",
                fontSize = MiuixTheme.textStyles.title1.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "小米 ROM 索引 · 官方包与社区移植包",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(Modifier.height(30.dp))

            when (mode) {
                Mode.CHOOSE -> {
                    PrimaryButton("游客登录") { vm.loginGuest() }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "可浏览全部机型、版本与下载链接",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("开发者登录") { mode = Mode.DEV_LOGIN; error = ""; info = "" }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "上传移植包需要使用开发者账号",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Mode.DEV_LOGIN -> {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            TextField(
                                value = username,
                                onValueChange = { username = it },
                                label = "账号",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "密码",
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (error.isNotBlank()) {
                        MsgText(error, true)
                    }
                    if (info.isNotBlank()) {
                        MsgText(info, false)
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        if (busy) "登录中…" else "登录",
                        onClick = {
                            if (busy) return@PrimaryButton
                            busy = true
                            error = ""
                            scope.launch {
                                val err = vm.loginDev(username, password)
                                busy = false
                                if (err != null) error = err
                            }
                        },
                        enabled = !busy,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        GhostButton("申请开发者账号") { mode = Mode.DEV_APPLY; error = ""; info = "" }
                    }
                    Spacer(Modifier.height(4.dp))
                    GhostButton("返回") { mode = Mode.CHOOSE; error = ""; info = "" }
                }

                Mode.DEV_APPLY -> {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "申请开发者账号",
                                fontWeight = FontWeight.Medium,
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "填写账号（邮箱）、名称、酷安名和密码；提交后由站长审核，" +
                                    "通过后即可用该账号登录并上传移植包。",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextField(
                                value = username,
                                onValueChange = { username = it },
                                label = "账号（填邮箱）",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextField(
                                value = name,
                                onValueChange = { name = it },
                                label = "名称",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextField(
                                value = coolapk,
                                onValueChange = { coolapk = it },
                                label = "酷安名",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "密码（≥6 位）",
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (error.isNotBlank()) MsgText(error, true)
                    if (info.isNotBlank()) MsgText(info, false)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        if (busy) "提交中…" else "提交申请",
                        onClick = {
                            if (busy) return@PrimaryButton
                            busy = true
                            error = ""
                            info = ""
                            scope.launch {
                                val err = vm.applyDev(username, password, name, coolapk)
                                busy = false
                                if (err == null) {
                                    info = "已提交，等站长审核通过后就能登录了。"
                                    mode = Mode.DEV_LOGIN
                                } else {
                                    error = err
                                }
                            }
                        },
                        enabled = !busy,
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton("返回") { mode = Mode.DEV_LOGIN; error = ""; info = "" }
                }

            }

            Spacer(Modifier.height(26.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.Center) {
                    GhostButton("开源许可与鸣谢") { openUrl(ctx, HelpLinks.NOTICE) }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "本应用以 AGPL-3.0 许可发布，界面参考 KernelSU 与 HyperCeiler。\n" +
                        "ROM 与移植包均为第三方资源，刷机有风险，请自行判断。",
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MsgText(text: String, isError: Boolean) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = text,
        color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
