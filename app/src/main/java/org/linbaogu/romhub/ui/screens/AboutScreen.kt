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
import org.linbaogu.romhub.hc.HcNavState
import org.linbaogu.romhub.hc.HcAboutBg
import org.linbaogu.romhub.hc.HcAboutCards
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

    // 关于页专属：HyperCeiler 那层会缓慢流动的彩色光效背景（AGSL 着色器）。
    // 其它页面保持纯色底 —— 只有这里保留光效，是用户明确要求的。
    Box(Modifier.fillMaxSize()) {
        HcAboutBg(Modifier.fillMaxSize())
        ListScreen(
            title = "关于",
            largeTitle = "关于",
            subtitle = "ROM Hub ${BuildConfig.VERSION_NAME}",
            bottomInnerPadding = bottomInnerPadding,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            // ------------------------------------------------ 头部
            // 版本卡：App 图标 + 名称 + 版本号（+ 有更新时的提示按钮）。
            // 原来的「设备信息」大卡片（机型 / Android 版本 / OS 版本 / 处理器 / 分辨率）
            // 按用户要求**整个去掉了** —— 关于页不展示本机信息。
            item { HcAboutCards() }

            // ------------------------------------------------ 关于本应用
            item { SectionLabel("关于本应用") }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        InfoRow("应用名称", "ROM Hub")
                        InfoRow("版本", "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                        InfoRow("作者", "Tian-Self")
                        InfoRow(
                            "数据来源",
                            "小米官方 OTA、小米社区、hyperos.fans、xiaomirom、RomCloud 等公开渠道",
                        )
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

            // 「服务器」区块已按用户要求移除：服务器地址属于部署配置，
            // 摆在「关于」里给普通用户看没意义。ServerAddressCard 组件保留，
            // 之后要挪到设置页/登录页时直接复用。

            // 通知 / 外观的所有开关**已按用户要求搬到「设置」页**（那边有完整的
            // 通知 / 外观 / 存储 / 数据 / 其他 五组）。关于页只留展示性内容：
            // 版本卡、应用信息、账号、已订阅机型、开源许可与鸣谢。

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

            // ------------------------------------------------ ROM 包来源站点与作者署名
            item { SectionLabel("ROM 包来源与作者") }
            OssCredits.RomSourceCredits.all.forEach { credit ->                item { SourceCreditCard(credit, ctx) }
            }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "以上站点是各自独立运营的第三方 ROM 索引站，署名摘自它们的公开页脚。" +
                                    "ROM Hub 只是把它们整理进统一的界面与下载器里，版权归各自权利人所有。",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
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
                            "本项目使用了以下开源项目与公开数据：\n" +
                                    "· KernelSU（weishu）与 HyperCeiler（ReChronoRain）：澎湃 OS 风格的界面与动效实现；\n" +
                                    "· compose-miuix-ui、Kyant0：Miuix 组件库与 AndroidLiquidGlass；\n" +
                                    "· hyperos.fans、xiaomirom：公开的机型与版本索引数据。\n" +
                                    "另外：Tech_Sky 编写并维护 RomCloud；可怜太可怜 提供 ROM 资源；" +
                                    "桜酱没有未来 完成了 ColorOS 的爬取与下载接口解析，" +
                                    "本应用的 OPPO / 一加 / Realme / 魅族 / 联想 品牌数据即来源于此。",
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

/**
 * 单个 ROM 包来源站点的署名卡片。
 *
 * 站点名 + 一句话说明在上，下面逐行列出「人名 —— 贡献」，
 * 整卡可点，跳到该站点首页。
 */
@Composable
private fun SourceCreditCard(credit: OssCredits.SourceCredit, ctx: android.content.Context) {
    Card(onClick = { openUrl(ctx, credit.siteUrl) }, showIndication = true) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    credit.site,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ROM 包来源",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                credit.summary,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            credit.people.forEachIndexed { index, (who, what) ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        who,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        what,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (index != credit.people.lastIndex) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                credit.siteUrl,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
