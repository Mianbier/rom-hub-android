package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.StoragePermission
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.ui.common.HcRow
import org.linbaogu.romhub.ui.common.HyphenRow
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import org.linbaogu.romhub.ui.login.MiuixDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载器设置页。
 *
 * 和云析那套对齐：通用线程数 + 按平台覆盖 + 并发任务数 + 免转存 + 锁屏保持 + 重试 + 限速，
 * 另加一块「网盘登录」（是否自动保存登录态）。
 *
 * 保存目录固定为公共 `Download/rom-hub/`（需「所有文件访问」权限，未授权时自动退回
 * App 私有目录，功能不中断），因此不做云析那种 SAF 目录选择器 ——
 * 自用场景下固定路径比让用户挑目录更省事。
 */
@Composable
fun DownloadSettingsScreen(bottomInnerPadding: Dp = 0.dp) {
    val ctx = LocalContext.current

    var threads by remember { mutableIntStateOf(Prefs.downloadThreads(ctx)) }
    var concurrent by remember { mutableIntStateOf(Prefs.downloadConcurrent(ctx)) }
    var noTransfer by remember { mutableStateOf(Prefs.downloadWithoutTransfer(ctx)) }
    var keepAwake by remember { mutableStateOf(Prefs.downloadKeepAwake(ctx)) }
    var speedLimit by remember { mutableIntStateOf(Prefs.downloadSpeedLimitKb(ctx)) }
    var retry by remember { mutableIntStateOf(Prefs.downloadRetryCount(ctx)) }
    var notifySpeed by remember { mutableStateOf(Prefs.downloadNotifySpeed(ctx)) }
    var autoSaveLogin by remember { mutableStateOf(Prefs.autoSaveLogin(ctx)) }
    var editingPlatform by remember { mutableStateOf<SharePlatform?>(null) }
    /**
     * 按网盘线程数的「版本号」。
     *
     * 列表里那行文字是组合期直接读 SharedPreferences 拿的（`downloadThreadsFor`），
     * 写完 prefs 不会通知 Compose → 存完还是显示旧值。
     * 存完手动 +1 让这块重组一次，读到新值。
     */
    var platformRev by remember { mutableIntStateOf(0) }

    val platforms = remember { PanAccounts_ALL }

    ListScreen(
        title = "下载设置",
        subtitle = "分片线程、并发、限速",
        bottomInnerPadding = bottomInnerPadding,
    ) {
        // ------------------------------------------------ 通用
        item { SectionLabel("通用") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    NumberRow(
                        label = "分片线程数",
                        subtitle = "每个任务切成几段并发下载，越高越快也越吃带宽",
                        value = threads,
                        options = Prefs.THREAD_OPTIONS,
                    ) { threads = it; Prefs.setDownloadThreads(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    NumberRow(
                        label = "同时下载任务数",
                        subtitle = "同时在跑的下载个数（后台并发，别设太高）",
                        value = concurrent,
                        options = listOf(1, 2, 3, 5),
                    ) { concurrent = it; Prefs.setDownloadConcurrent(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "免转存下载",
                        subtitle = if (noTransfer) {
                            "解析出直链后直接下载，不往你网盘里塞临时文件（推荐）"
                        } else {
                            "先转存到临时目录再取链，下载完自动清理"
                        },
                        checked = noTransfer,
                    ) { noTransfer = it; Prefs.setDownloadWithoutTransfer(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "锁屏后保持下载",
                        subtitle = "下载时持有唤醒锁，避免息屏后网络被挂起",
                        checked = keepAwake,
                    ) { keepAwake = it; Prefs.setDownloadKeepAwake(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "通知栏显示速度",
                        subtitle = if (notifySpeed) "完整通知：进度条 + 实时速度" else "精简通知：只显示进度条",
                        checked = notifySpeed,
                    ) { notifySpeed = it; Prefs.setDownloadNotifySpeed(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    // 存储权限入口。
                    // 为什么必须放在这里：首启那个引导弹窗是「一次性」的
                    //（Prefs.storagePromptShown），用户点过「以后再说」或升级过
                    // 版本，这个标记就变成 true，弹窗再也不会出现 ——
                    // 升级后很多用户反馈「要权限的提示不见了」。
                    // 这里常驻一个入口，任何时候都能重新去系统设置里开。
                    val storageGranted = remember(ctx) { StoragePermission.granted(ctx) }
                    HcRow(
                        title = "存储权限",
                        subtitle = if (storageGranted) {
                            "已授予，下载保存到公共 Download/rom-hub/"
                        } else {
                            "未授予，下载暂存在 App 私有目录。点这里去系统设置开启「所有文件访问」"
                        },
                        onClick = { StoragePermission.openAllFilesSettings(ctx) },
                        trailing = {
                            Text(
                                if (storageGranted) "已开启" else "去开启",
                                color = if (storageGranted) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        },
                    )
                }
            }
        }

    // ------------------------------------------------ 网盘登录
    item { SectionLabel("网盘登录") }
    item {
        Card {
            Column(Modifier.padding(vertical = 6.dp)) {
                SwitchRow(
                    label = "自动保存登录态",
                    subtitle = if (autoSaveLogin) {
                        "网页里登录完成就自动存下并关页（139 等平台可能在中间态误判）"
                    } else {
                        "登录后需手动点右上角「保存」（推荐，行为可预期）"
                    },
                    checked = autoSaveLogin,
                ) { autoSaveLogin = it; Prefs.setAutoSaveLogin(ctx, it) }
            }
        }
    }

    // ------------------------------------------------ 重试 / 限速
    item { SectionLabel("稳定性") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    NumberRow(
                        label = "失败自动重试",
                        subtitle = if (retry == 0) "失败后不自动重试" else "失败后自动重试 $retry 次（支持断点续传）",
                        value = retry,
                        options = listOf(0, 1, 3, 5, 10),
                    ) { retry = it; Prefs.setDownloadRetryCount(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    NumberRow(
                        label = "下载限速",
                        subtitle = if (speedLimit == 0) {
                            "不限速，跑满带宽"
                        } else {
                            "上限 ${formatSpeedLimit(speedLimit)}"
                        },
                        value = speedLimit,
                        options = listOf(0, 1024, 4096, 10240, 0),
                        display = { if (it == 0) "不限" else formatSpeedLimit(it) },
                        dedupe = true,
                    ) { speedLimit = it; Prefs.setDownloadSpeedLimitKb(ctx, it) }
                }
            }
        }

        // ------------------------------------------------ 按平台覆盖
        item { SectionLabel("按网盘单独设置线程") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "各家网盘对并发的容忍度不一样。百度容易限速，建议调低；" +
                            "夸克 / 115 可以开高。点一家进去改。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // 先读一遍放进局部变量，platformRev 变化时这块会重组并重读
                    val rev = platformRev
                    platforms.forEachIndexed { i, p ->
                        HyphenRow(
                            label = PanHub.platformName(p),
                            value = "${if (rev >= 0) Prefs.downloadThreadsFor(ctx, p.name) else 0} 线程",
                            onClick = { editingPlatform = p },
                        )
                        if (i != platforms.lastIndex) {
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (org.linbaogu.romhub.core.StoragePermission.granted(ctx)) {
                            "文件保存在 Download/rom-hub/，文件管理器里直接能找到。"
                        } else {
                            "当前保存在 App 私有目录（Android/data/…/files/Download）。" +
                                "在「网盘下载器 → 下载」页点「去开启存储权限」后会改存 Download/rom-hub/。"
                        },
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

    // ------------------------------------------------ 单平台线程数
    //
    // 之前这里用 MiuixDialog 只弹了「保存 / 取消」两个按钮，**里面没有任何可选项** ——
    // 点进去只能原样保存，表现就是「没有选项、一直显示 8」。改成带选项的选择弹窗。
    editingPlatform?.let { p ->
        val cur = Prefs.downloadThreadsFor(ctx, p.name)
        var value by remember(p) { mutableIntStateOf(cur) }
        ThreadPickDialog(
            platformName = PanHub.platformName(p),
            current = cur,
            value = value,
            onValueChange = { value = it },
            onDismiss = { editingPlatform = null },
            onConfirm = {
                Prefs.setDownloadThreadsFor(ctx, p.name, value)
                platformRev++          // ← 逼列表重读 prefs，否则仍显示旧值
                editingPlatform = null
            },
        )
    }
}

/**
 * 单平台线程数选择弹窗。
 *
 * 为什么自己画而不用 MiuixDialog：MiuixDialog 只有标题/正文/两个按钮，
 * 没有内容槽，塞不进选项列表（之前就是因此变成「点进去没选项」）。
 * 这里给「常用档位 + 按平台推荐值」两个维度，选完点保存即可。
 */
@Composable
private fun ThreadPickDialog(
    platformName: String,
    current: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val recommended = PanHub.recommendedThreads(platformName)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "$platformName · 线程数",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "当前 $current 线程 · 推荐 $recommended。" +
                        "设太高可能被平台限速甚至判定异常。",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))

                // 档位有 9 个（1~256），一行塞不下会挤成一条缝。
                // 拆成两行网格：每行最多 5 个，每个格子等宽。
                val options = Prefs.THREAD_OPTIONS
                options.chunked(5).forEach { rowOpts ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowOpts.forEach { n ->
                            val selected = n == value
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) cs.primary else cs.surfaceContainerHigh)
                                    .clickable { onValueChange(n) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    n.toString(),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = if (selected) Color.White else cs.onSurface,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                )
                            }
                        }
                        // 补齐最后一行缺失的格子，避免 weight 拉伸导致前面的格变宽
                        repeat(5 - rowOpts.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "推荐值：百度 4（易限速）· 夸克/115 16~64（能吃高并发）。" +
                        "档位越高越吃带宽，128 以上建议用 Wi-Fi。",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { GhostButton("取消") { onDismiss() } }
                    Box(Modifier.weight(1f)) { PrimaryButton("保存") { onConfirm() } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 列表行

/**
 * 数值选择行：一排选项，点一个就生效。
 *
 * 档位多于 6 个时自动改成多行网格 —— 线程档位有 9 个（1~256），
 * 横排会把卡片撑到屏幕外，用户根本看不到 256 那一档（这是真机反馈过的问题）。
 */
@Composable
private fun NumberRow(
    label: String,
    subtitle: String,
    value: Int,
    options: List<Int>,
    display: (Int) -> String = { it.toString() },
    dedupe: Boolean = false,
    onPick: (Int) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val shown = if (dedupe) options.distinct() else options
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontSize = MiuixTheme.textStyles.body1.fontSize, color = cs.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = cs.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(10.dp))
        if (shown.size <= 5) {
            // 档位少：保持原来的一行排版，视觉更紧凑
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { n ->
                    OptionChip(
                        text = display(n),
                        selected = n == value,
                        modifier = Modifier,
                    ) { onPick(n) }
                }
            }
        } else {
            // 档位多：每行 4 个的网格，最后一行补空位避免格子被拉伸变形
            shown.chunked(4).forEach { rowOpts ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowOpts.forEach { n ->
                        OptionChip(
                            text = display(n),
                            selected = n == value,
                            modifier = Modifier.weight(1f),
                        ) { onPick(n) }
                    }
                    repeat(4 - rowOpts.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 档位按钮：选中态填主色。 */
@Composable
private fun OptionChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) cs.primary else cs.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Color.White else cs.onSurface,
        )
    }
}

/** 开关行。 */
@Composable
private fun SwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = MiuixTheme.textStyles.body1.fontSize, color = cs.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(12.dp))
        // 自己画个 Miuix 风格的开关，省得踩 SuperSwitch 的 API 变动
        Box(
            Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) cs.primary else cs.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .width(22.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
            )
        }
    }
}

private fun formatSpeedLimit(kb: Int): String = when {
    kb <= 0 -> "不限速"
    kb >= 1024 -> "${kb / 1024} MB/s"
    else -> "$kb KB/s"
}

/** 设置页要列的平台（GitHub 不用登录，也从这里排除）。 */
private val PanAccounts_ALL = listOf(
    SharePlatform.QUARK,
    SharePlatform.UC,
    SharePlatform.BAIDU,
    SharePlatform.PAN115,
    SharePlatform.PAN123,
    SharePlatform.C139,
    SharePlatform.XUNLEI,
)
