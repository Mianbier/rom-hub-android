package org.linbaogu.romhub.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.login.BaiduLoginScreen
import org.linbaogu.romhub.ui.login.C139LoginScreen
import org.linbaogu.romhub.ui.login.Pan115LoginScreen
import org.linbaogu.romhub.ui.login.Pan123LoginScreen
import org.linbaogu.romhub.ui.login.PanAccounts
import org.linbaogu.romhub.ui.login.QuarkLoginScreen
import org.linbaogu.romhub.ui.login.UCLoginScreen
import org.linbaogu.romhub.ui.login.XunleiLoginScreen
import org.linbaogu.romhub.ui.login.MiuixDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网盘下载器 —— 账号管理页（底栏「网盘下载器」的落地页）。
 *
 * 这里解决一个硬前提：七家网盘**不登录就拿不到 cookie/token**，解析分享链接会直接失败。
 * 所以下载之前先在这儿把账号登上，各个平台的登录页从这张列表进去。
 *
 * 登录页按平台分派：
 *   · 夸克 / UC / 百度 / 115 / 123 / 移动 → WebView 打开官网，登录后自动抓凭证
 *   · 迅雷 → 账号密码 + 风控短信验证码（它不走 Cookie，是 OAuth token）
 */
@Composable
fun PanAccountScreen(
    bottomInnerPadding: Dp = 0.dp,
    /** 从下载页发现「未登录」时可以直接跳进来 */
    highlight: SharePlatform? = null,
    /** 点「登录」→ 上抛给 PanHubScreen 做**全屏覆盖**（盖住分段控件，避免两套顶栏叠在一起） */
    onOpenLogin: (SharePlatform) -> Unit = {},
    /** 点已登录的网盘那行 → 上抛给 PanHubScreen 做全屏覆盖 */
    onOpenCloud: (SharePlatform) -> Unit = {},
    /**
     * 刷新触发键：值一变就重读账号状态。
     *
     * 登录页挂在 pager 之外，关掉后本页不会重新组合（一直活着），
     * `LaunchedEffect(Unit)` 不会重跑 —— 必须靠这个 key 主动催一次，
     * 否则登录成功了列表还写「未登录」。
     */
    refreshKey: Int = 0,
    /** 退出登录成功后回调（外层据此 +1 版本号并刷新） */
    onLoggedOut: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<PanAccounts.AccountUiState>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirmLogout by remember { mutableStateOf<PanAccounts.AccountUiState?>(null) }

    suspend fun refresh() {
        val s = withContext(Dispatchers.IO) { PanAccounts.state(ctx) }
        accounts = s
        loading = false
    }

    // 首次进入读一次；之后每次 refreshKey 变化（登录成功 / 退出）都重读
    LaunchedEffect(refreshKey) { refresh() }

    val loggedCount = accounts.count { it.loggedIn }

    ListScreen(
        title = "网盘下载器",
        subtitle = if (loading) "读取账号状态…" else "已登录 $loggedCount / ${accounts.size} 家",
        bottomInnerPadding = bottomInnerPadding,
    ) {
        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "支持七家网盘",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "夸克 · UC · 百度 · 115 · 123 云盘 · 移动云盘 · 迅雷\n" +
                            "登录一次即可解析分享链接，并交给多线程下载器直接下载。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item { SectionLabel("账号") }

        if (loading) {
            item { Hint("正在读取…") }
        } else {
            accounts.forEachIndexed { index, acc ->
                item(key = acc.platform.name) {
                    AccountRow(
                        acc = acc,
                        highlighted = acc.platform == highlight,
                        onLogin = { onOpenLogin(acc.platform) },
                        onLogout = { confirmLogout = acc },
                        onOpen = { onOpenCloud(acc.platform) },
                    )
                    if (index != accounts.lastIndex) {
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    }
                }
            }
        }
    }

    // ------------------------------------------------ 退出确认
    confirmLogout?.let { acc ->
        MiuixDialog(
            title = "退出登录",
            message = "确定要退出「${acc.name}」吗？退出后需要重新登录才能解析它的分享链接。",
            confirmText = "退出",
            dismissText = "取消",
            onDismiss = { confirmLogout = null },
            onConfirm = {
                scope.launch {
                    PanAccounts.logout(ctx, acc.platform)
                    confirmLogout = null
                    refresh()
                    onLoggedOut()
                }
            },
        )
    }
}

// ---------------------------------------------------------------- 单个账号行

@Composable
private fun AccountRow(
    acc: PanAccounts.AccountUiState,
    highlighted: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpen: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(
        Modifier
            .padding(vertical = 2.dp)
            // 已登录：整行可点，进去就是这家网盘的文件浏览
            .then(if (acc.loggedIn) Modifier.clickable { onOpen() } else Modifier),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 平台首字圆形色块（不引各家 logo 资源，省体积）
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (acc.loggedIn) cs.primary.copy(alpha = 0.14f)
                        else cs.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    acc.name.take(1),
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (acc.loggedIn) cs.primary else cs.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        acc.name,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        color = cs.onSurface,
                    )
                    if (highlighted && !acc.loggedIn) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(cs.error.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "需要登录",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = cs.error,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        acc.loggedIn && acc.nickname.isNotBlank() -> "已登录 · ${acc.nickname} · 点这行看文件"
                        acc.loggedIn -> "已登录 · 点这行看文件"
                        else -> "未登录"
                    },
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = if (acc.loggedIn) cs.primary else cs.onSurfaceVariantSummary,
                )
                // 空间信息：登录时探针顺手抓到的（目前只有移动云盘提供）
                if (acc.loggedIn && acc.quotaTotal > 0L) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "已用 ${formatBytes(acc.quotaUsed)} / 共 ${formatBytes(acc.quotaTotal)}",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = cs.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            if (acc.loggedIn) {
                GhostButton("退出") { onLogout() }
            } else {
                GhostButton("登录") { onLogin() }
            }
        }
    }
}
