/*
 * 迅雷网盘登录页（账号密码 + 风控短信验证码）
 *
 * 流程搬运自 YunX (云析) - Copyright (C) 2026 CYQawa
 * 原文件：app/src/main/kotlin/com/yunx/app/ui/login/XunleiLoginScreen.kt
 * 许可：GNU Affero General Public License v3.0（见项目根 LICENSE）
 *
 * 改动说明：
 *   · 包名改写；改用 Miuix 风格输入框与按钮
 *   · 原来依赖 XunleiAccountViewModel，这里直接持 repository（本项目不引 viewmodel 生态）
 *   · 「应用内验证」改跳 [XunleiVerifyScreen]（同目录），需要时可以再单独调
 */

package org.linbaogu.romhub.ui.login

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.XunleiApi
import org.linbaogu.romhub.pan.XunleiLoginStep
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 迅雷网盘登录页。
 *
 * 迅雷不走 Cookie —— 它是 OAuth 那套（账号密码 → 可能触发风控 → 短信验证码 → 换 accessToken），
 * 所以单独一个页面，其余六家都走 [WebPanLoginScreen]。
 *
 * 步骤：账号密码 →（风控时）发短信 → 填验证码 → 换 token 落库。
 */
@Composable
fun XunleiLoginScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<XunleiLoginStep?>(null) }
    var busy by remember { mutableStateOf(false) }
    var smsSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun note(msg: String) {
        error = msg
    }

    // 已经登录过就直接退出登录页
    LaunchedEffect(Unit) {
        val existing = runCatching { PanHub.xunleiAccount(ctx).getAccount() }.getOrNull()
        if (existing != null && existing.accessToken.isNotBlank()) onSaved()
    }

    BackHandler { onBack() }

    val needSms = step?.needSms == true

    // 不透明底：挡住下层 / 全局流动极光（否则整页亮度会随光效波动 = 闪烁）
    Column(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 1f)),
    ) {
        // ------------------------------------------------ 标题栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("返回") { if (!busy) onBack() }
            Spacer(Modifier.width(10.dp))
            Text(
                "迅雷网盘登录",
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (needSms) "短信验证" else "登录迅雷网盘",
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                if (needSms) {
                    if (smsSent) "账号密码登录触发了安全验证，验证码已发送至 $username"
                    else "账号密码登录触发了安全验证，请点下面的「发送验证码」"
                } else {
                    "用迅雷账号登录，之后就能解析和下载迅雷分享链接"
                },
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (!needSms) {
                PanField(
                    label = "手机号 / 邮箱",
                    value = username,
                    onValueChange = { username = it },
                )
                PanField(
                    label = "密码",
                    value = password,
                    onValueChange = { password = it },
                    password = true,
                )
                GhostButton(if (busy) "登录中…" else "登录") {
                    if (busy || username.isBlank() || password.isBlank()) return@GhostButton
                    busy = true
                    note("")
                    scope.launch {
                        val result = runCatching {
                            PanHub.xunleiAccount(ctx).loginWithPassword(username.trim(), password)
                        }
                        busy = false
                        result.fold(
                            onSuccess = { s ->
                                when {
                                    s.needSms -> {
                                        // 风控响应里可能自带 creditkey，那就直接进短信步骤
                                        val review = XunleiApi.parseReviewUrl(s.reviewUrl)
                                        val credit = review["creditkey"].orEmpty()
                                        step = if (credit.isNotBlank()) {
                                            s.copy(
                                                smsCreditKey = credit,
                                                smsToken = review["token"].orEmpty(),
                                            )
                                        } else {
                                            s.copy(message = "短信发送失败")
                                        }
                                        smsSent = false
                                        if (credit.isBlank()) note("需要短信验证，请点「发送验证码」")
                                    }

                                    s.sessionId.isNotBlank() -> {
                                        val ok = PanHub.xunleiAccount(ctx).finishLogin(s, username.trim())
                                        if (ok) {
                                            SnackbarController.show("登录成功")
                                            onSaved()
                                        } else {
                                            note("登录失败，无法换取凭证")
                                        }
                                    }

                                    else -> note(s.message.ifBlank { "登录失败，请检查账号密码" })
                                }
                            },
                            onFailure = { e -> note(e.message ?: "登录失败") },
                        )
                    }
                }
            } else {
                PanField(
                    label = "短信验证码",
                    value = smsCode,
                    onValueChange = { smsCode = it },
                    number = true,
                )
                GhostButton(if (busy) "验证中…" else "验证并登录") {
                    if (busy || smsCode.isBlank()) return@GhostButton
                    val s = step ?: return@GhostButton
                    busy = true
                    note("")
                    scope.launch {
                        val ok = runCatching {
                            PanHub.xunleiAccount(ctx).loginWithSms(
                                username.trim(), smsCode.trim(), s.smsCreditKey, s.smsToken,
                            )
                        }.getOrDefault(false)
                        busy = false
                        if (ok) {
                            SnackbarController.show("登录成功")
                            onSaved()
                        } else {
                            note("验证码校验失败，请确认后重试")
                        }
                    }
                }
                GhostButton(if (smsSent) "重新发送验证码" else "发送验证码") {
                    if (busy) return@GhostButton
                    busy = true
                    note("")
                    scope.launch {
                        val r = runCatching { PanHub.xunleiAccount(ctx).sendSms(username.trim()) }
                        busy = false
                        r.fold(
                            onSuccess = { s ->
                                if (s.smsCreditKey.isNotBlank()) {
                                    smsSent = true
                                    step = step?.copy(
                                        smsCreditKey = s.smsCreditKey,
                                        smsToken = s.smsToken,
                                    ) ?: s
                                    SnackbarController.show("验证码已发送")
                                } else {
                                    note(s.message.ifBlank { "短信发送失败，请稍后重试" })
                                }
                            },
                            onFailure = { e -> note(e.message ?: "短信发送失败") },
                        )
                    }
                }
                Text(
                    "如果一直收不到短信，确认手机号是否正确，或者稍后重试 / 换个网络",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            if (error.isNotBlank()) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            error,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "没设置过密码？点这里去设置",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://i.xunlei.com/xluser/validate/findpwd_acc.html"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
    }
}

/** 迅雷登录页用的输入框（Miuix 风格：浅底圆角 + 标签）。 */
@Composable
private fun PanField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    number: Boolean = false,
) {
    Column {
        Text(
            label,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    "请输入$label",
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                visualTransformation = if (password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        number -> KeyboardType.Number
                        password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
