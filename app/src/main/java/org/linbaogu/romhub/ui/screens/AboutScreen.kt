package org.linbaogu.romhub.ui.screens

import org.linbaogu.romhub.ui.notify.openNotificationSettings
import org.linbaogu.romhub.ui.notify.rememberNotifyPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linbaogu.romhub.BuildConfig
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.data.DeviceItem
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.network.HelpLinks
import org.linbaogu.romhub.network.OssCredits
import org.linbaogu.romhub.network.OssItem
import org.linbaogu.romhub.notify.NotifyScheduler
import org.linbaogu.romhub.ui.AppViewModel
import org.linbaogu.romhub.ui.common.Chip
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    vm: AppViewModel,
    bottomInnerPadding: Dp,
) {
    val ctx = LocalContext.current
    var devices by remember { mutableStateOf(Repo.cachedDevices()) }
    var subs by remember { mutableStateOf(Prefs.subscriptions(ctx)) }
    var notifyOn by remember { mutableStateOf(Prefs.notifyEnabled(ctx)) }
    var notifyOfficial by remember { mutableStateOf(Prefs.notifyOfficial(ctx)) }
    var notifyPorts by remember { mutableStateOf(Prefs.notifyPorts(ctx)) }
    var showAllOss by remember { mutableStateOf(false) }
    val notifyPerm = rememberNotifyPermission()

    LaunchedEffect(Unit) {
        if (devices.isEmpty()) runCatching { Repo.devices(ctx) }.onSuccess { devices = it }
    }

    val nameOf: (String) -> DeviceItem? = { code -> devices.firstOrNull { it.code == code } }

    // 极光背景由 App 外壳统一提供（铺满所有界面），这里只放内容
    Box(Modifier.fillMaxSize()) {
        ListScreen(
            title = "关于",
            largeTitle = "关于",
            subtitle = "ROM Hub ${BuildConfig.VERSION_NAME}",
            bottomInnerPadding = bottomInnerPadding,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            // ------------------------------------------------ 头部（HyperCeiler 式渐变横幅）
            item {
                val grad = Brush.linearGradient(
                    listOf(
                        Color(0xFFF7C8DC),
                        Color(0xFFC9C4F4),
                        Color(0xFFB9D4F2),
                    ),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(grad)
                        .padding(vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppLogo(96.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "ROM Hub",
                        fontSize = MiuixTheme.textStyles.title1.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B4BC4),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${BuildConfig.VERSION_NAME} | ${BuildConfig.VERSION_CODE}",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = Color(0xFF6B5E9E),
                    )
                }
            }

            // ------------------------------------------------ 本机信息（HyperCeiler 式大卡片）
            item { DeviceInfoCard() }

            // ------------------------------------------------ 关于本应用
            item { SectionLabel("关于本应用") }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        InfoRow("应用名称", "ROM Hub")
                        InfoRow("版本", "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                        InfoRow("作者", "Tian-Self")
                        InfoRow("数据来源", "小米官方 OTA、小米社区、hyperos.fans、xiaomirom 等公开渠道")
                        InfoRow("许可", "GNU Affero General Public License v3.0")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("检查更新") { vm.checkAppUpdate(manual = true) }
                        }
                        if (vm.appUpdate != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "有新版本 v${vm.appUpdate?.versionName} 可用",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "本应用是第三方非官方工具，与小米公司无隶属关系。" +
                                    "ROM 与移植包版权归各自权利人所有，刷机有风险，请自行判断。",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            // ------------------------------------------------ 账号
            item { SectionLabel("账号") }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        val s = vm.session
                        InfoRow(
                            "当前身份",
                            when (s.role) {
                                Role.DEV -> "开发者 · ${s.username}"
                                Role.GUEST -> "游客"
                                Role.NONE -> "未登录"
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (s.role == Role.DEV) {
                                "你是开发者，可以上传移植包。账号由站长审核发放。"
                            } else {
                                "游客可以浏览全部机型、版本与下载链接。想上传移植包？" +
                                    "退出登录后，在启动页选「开发者登录 → 申请开发者账号」。"
                            },
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("退出登录") { vm.logout() }
                        }
                    }
                }
            }

            // ------------------------------------------------ 订阅
            item { SectionLabel("订阅更新提醒") }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        if (!notifyPerm.granted) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "系统通知权限未开启",
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        color = MiuixTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "没有这个权限，订阅提醒发不出来。",
                                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                GhostButton("去开启") { notifyPerm.launch() }
                            }
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                GhostButton("打开系统通知设置") { openNotificationSettings(ctx) }
                            }
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                        SwitchRow(
                            title = "开启更新提醒",
                            summary = "关闭后不再推送任何通知",
                            checked = notifyOn,
                        ) {
                            notifyOn = it
                            Prefs.setNotifyEnabled(ctx, it)
                            if (it) NotifyScheduler.schedule(ctx) else NotifyScheduler.cancel(ctx)
                        }
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        SwitchRow(
                            title = "官方包更新",
                            summary = "稳定版 / 开发版 / 内测 / Beta 的新版本",
                            checked = notifyOfficial,
                            enabled = notifyOn,
                        ) { notifyOfficial = it; Prefs.setNotifyOfficial(ctx, it) }
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        SwitchRow(
                            title = "移植包更新",
                            summary = "订阅机型有新提交的社区移植包",
                            checked = notifyPorts,
                            enabled = notifyOn,
                        ) { notifyPorts = it; Prefs.setNotifyPorts(ctx, it) }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已订阅机型（${subs.size}）",
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (subs.isNotEmpty()) {
                                GhostButton("全部清空") {
                                    subs.forEach { Prefs.toggleSubscription(ctx, it) }
                                    subs = emptySet()
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (subs.isEmpty()) {
                            Text(
                                "还没有订阅。进任意机型详情页，打开「订阅更新提醒」即可。",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            subs.sorted().forEach { code ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            nameOf(code)?.displayName ?: code,
                                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            code,
                                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    GhostButton("取消") {
                                        Prefs.toggleSubscription(ctx, code)
                                        subs = Prefs.subscriptions(ctx)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ 开源许可
            item { SectionLabel("开源许可与鸣谢") }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "本应用以 AGPL-3.0 许可发布，完整源码公开。界面与动效来自下列开源项目，" +
                                    "在此逐一致谢。",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("查看源码") { openUrl(ctx, HelpLinks.SOURCE_REPO) }
                            GhostButton("许可全文") { openUrl(ctx, HelpLinks.LICENSE_URL) }
                        }
                    }
                }
            }

            val shown = if (showAllOss) OssCredits.all else OssCredits.all.take(6)
            items(shown, key = { it.name }) { item -> OssCard(item, ctx) }

            if (!showAllOss) {
                item {
                    GhostButton("展开全部 ${OssCredits.all.size} 项") { showAllOss = true }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "特别鸣谢",
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "感谢 KernelSU 的 weishu 与 HyperCeiler 的 ReChronoRain，" +
                                    "他们开源了整套澎湃 OS 风格的界面与动效实现；" +
                                    "感谢 compose-miuix-ui 与 Kyant0 提供的 Miuix、AndroidLiquidGlass；" +
                                    "也感谢 hyperos.fans 等站点长期维护的公开数据。",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 本机信息

/** 读取系统属性（HyperOS / SoC 型号等不在 Build 里的信息）。 */
private fun sysProp(key: String): String = runCatching {
    @Suppress("DiscouragedPrivateApi", "PrivateApi")
    val sp = Class.forName("android.os.SystemProperties")
    val get = sp.getMethod("get", String::class.java)
    (get.invoke(null, key) as? String).orEmpty().trim()
}.getOrDefault("")

/** HyperOS / MIUI 版本，都没有就退回 Android 版本。 */
private fun osVersionText(): String {
    sysProp("ro.mi.os.version.name").takeIf { it.isNotBlank() }?.let { return "HyperOS $it" }
    sysProp("ro.miui.ui.version.name").takeIf { it.isNotBlank() }?.let { return "MIUI $it" }
    return "Android ${android.os.Build.VERSION.RELEASE}"
}

private fun kernelVersion(): String = runCatching {
    java.io.File("/proc/version").readText().trim()
        .substringAfter("version ").substringBefore(" (")
        .ifBlank { "—" }
}.getOrDefault("—")

/** HyperCeiler 式「本机信息」大卡片：设备名大标题 + 三行「值 + 小灰标签」。 */
@Composable
private fun DeviceInfoCard() {
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    data class Line(val value: String, val label: String)
    val lines = remember {
        val tm = ctx.resources.displayMetrics
        val osName = osVersionText()
        buildList {
            add(Line("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(), "设备型号"))
            add(Line("Android ${android.os.Build.VERSION.RELEASE}", "Android 版本"))
            add(Line(osName, "OS 版本"))
            add(
                Line(
                    sysProp("ro.soc.model").ifBlank {
                        sysProp("ro.board.platform").ifBlank { android.os.Build.HARDWARE }
                    },
                    "处理器",
                ),
            )
            add(
                Line(
                    "${tm.widthPixels} × ${tm.heightPixels}",
                    "屏幕分辨率",
                ),
            )
        }
    }
    Card {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = lines.firstOrNull()?.value ?: "—",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            lines.forEach { line ->
                Text(
                    text = line.value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Text(
                    text = line.label,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun OssCard(item: OssItem, ctx: android.content.Context) {    Card(onClick = { openUrl(ctx, item.url) }, showIndication = true) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    item.license,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            if (item.author.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    item.author,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.note,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.url,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = MiuixTheme.textStyles.body1.fontSize)
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
    }
}
