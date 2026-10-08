package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.BuildConfig
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.hc.HcNavState
import org.linbaogu.romhub.notify.NotifyScheduler
import org.linbaogu.romhub.ui.AppViewModel
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「设置」tab —— 完全按 HyperCeiler 的设置页结构：**分组标题 + 一张实底卡片装若干行**，
 * 行与行之间一条细分割线，右侧是开关 / 箭头 / 值。
 *
 * ⚠️ 这里**不再使用** `OverlayDialog`：它属于 Miuix 的 popup 体系，需要挂在外层
 * `Scaffold` 的 `popupHost` 里；之前直接当普通 Composable 放在列表同级，
 * 点击「重置设置」时宿主找不到 → 直接闪退。现在改成**行内二次确认**
 * （点一下展开「确定 / 取消」），零依赖、不会再崩。
 */
@Composable
fun HcSettingsPage(
    vm: AppViewModel,
    bottomInnerPadding: Dp,
) {
    val ctx = LocalContext.current
    var notifyOn by remember { mutableStateOf(Prefs.notifyEnabled(ctx)) }
    var notifyOfficial by remember { mutableStateOf(Prefs.notifyOfficial(ctx)) }
    var notifyPorts by remember { mutableStateOf(Prefs.notifyPorts(ctx)) }
    // ⚠ 必须用 mutableStateOf 存一份：直接写 `checked = Prefs.downloadBatteryLimit(ctx)`
    //   读的是普通 SharedPreferences，**不是 Compose 状态** → 切换后不会重组，
    //   开关立刻弹回原状（用户报「省流模式的开关无法打开」）。
    var batteryLimit by remember { mutableStateOf(Prefs.downloadBatteryLimit(ctx)) }
    var confirmReset by remember { mutableStateOf(false) }

    ListScreen(
        title = "设置",
        largeTitle = "设置",
        subtitle = "HyperRomhub ${BuildConfig.VERSION_NAME}",
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        // ── 通知 ──
        item { SectionLabel("通知") }
        item {
            HcGroup {
                HcSwitchRow(
                    title = "开启更新提醒",
                    summary = "关闭后不再推送任何通知",
                    checked = notifyOn,
                ) {
                    notifyOn = it
                    Prefs.setNotifyEnabled(ctx, it)
                    if (it) NotifyScheduler.schedule(ctx) else NotifyScheduler.cancel(ctx)
                }
                HcDivider()
                HcSwitchRow(
                    title = "官方包更新",
                    summary = "稳定版 / 开发版 / 内测 / Beta 的新版本",
                    checked = notifyOfficial,
                    enabled = notifyOn,
                ) { notifyOfficial = it; Prefs.setNotifyOfficial(ctx, it) }
                HcDivider()
                HcSwitchRow(
                    title = "移植包更新",
                    summary = "订阅机型有新提交的社区移植包",
                    checked = notifyPorts,
                    enabled = notifyOn,
                ) { notifyPorts = it; Prefs.setNotifyPorts(ctx, it) }
            }
        }

        // ── 外观 ──
        item { SectionLabel("外观") }
        item {
            HcGroup {
                HcSwitchRow(
                    title = "悬浮底栏",
                    summary = "开 = 悬浮胶囊；关 = 贴地标签栏",
                    checked = HcNavState.enabled,
                ) { HcNavState.setEnabled(ctx, it) }
                HcDivider()
                HcSwitchRow(
                    title = "胶囊玻璃",
                    summary = "开 = 玻璃折射效果；关 = 纯色胶囊（仅悬浮时生效）",
                    checked = HcNavState.glass,
                    enabled = HcNavState.enabled,
                ) { HcNavState.setGlass(ctx, it) }
            }
        }

        // ── 存储 ──
        item { SectionLabel("存储") }
        item {
            HcGroup {
                HcSwitchRow(
                    title = "省流模式",
                    summary = "只保留最近的数据缓存，减少占用",
                    checked = batteryLimit,
                ) {
                    batteryLimit = it
                    Prefs.setDownloadBatteryLimit(ctx, it)
                }
            }
        }

        // ── 数据（备份 / 恢复 / 重置，HyperCeiler 的结构性设置）──
        item { SectionLabel("数据") }
        item {
            HcGroup {
                HcClickRow("备份设置", "导出到 Download/rom-hub/settings.json") {
                    toast(ctx, SettingsBackup.backup(ctx))
                }
                HcDivider()
                HcClickRow("恢复设置", "从备份文件读回设置") {
                    toast(ctx, SettingsBackup.restore(ctx))
                }
                HcDivider()
                HcClickRow("重置设置", "清空所有设置与订阅（不可恢复）") {
                    confirmReset = !confirmReset
                }
                if (confirmReset) {
                    HcDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "确定要重置吗？",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton("取消", onClick = { confirmReset = false })
                        TextButton(
                            "确定",
                            onClick = {
                                SettingsBackup.reset(ctx)
                                notifyOn = Prefs.notifyEnabled(ctx)
                                notifyOfficial = Prefs.notifyOfficial(ctx)
                                notifyPorts = Prefs.notifyPorts(ctx)
                                batteryLimit = Prefs.downloadBatteryLimit(ctx)
                                confirmReset = false
                                toast(ctx, "已重置")
                            },
                        )
                    }
                }
            }
        }

        // ── 其他 ──
        item { SectionLabel("其他") }
        item {
            HcGroup {
                HcClickRow("检查更新", "当前版本 ${BuildConfig.VERSION_NAME}") {
                    // ⚠ 必须传 manual = true：
                    //   不传的话，没查到更新时既不弹窗也不提示，用户按了等于没反应。
                    //   manual = true 才会把结果写进 globalMessage（有新版本 / 已是最新 / 服务端没配置）。
                    vm.checkAppUpdate(manual = true)
                }
                HcDivider()
                HcClickRow("清除缓存", "清掉机型 / 动态 / 统计的本地缓存") {
                    Prefs.setFeedCache(ctx, "")
                    Prefs.setStatsCache(ctx, "")
                    toast(ctx, "缓存已清除")
                }
                HcDivider()
                HcClickRow(
                    "当前身份",
                    if (vm.session.role == Role.DEV) "开发者" else "普通用户",
                ) { }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun toast(ctx: android.content.Context, msg: String) {
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
}

/** 带开关的行（HyperCeiler 设置项样式）。 */
@Composable
private fun HcSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
        )
    }
}

/** 可点行（右侧留箭头位）。 */
@Composable
private fun HcClickRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
    }
}
