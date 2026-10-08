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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.StoragePermission
import org.linbaogu.romhub.download.DomainPartLimit
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.ui.common.HcRow
import org.linbaogu.romhub.ui.common.Hint
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
 * ## 分组
 *
 * 下载相关的设置项很多，直接铺出来必然「乱」，所以按"用户此刻想干什么"分组收敛：
 *
 *   · **性能**   —— 线程数 / 并发数（用户最常调的俩，放最前）
 *   · **网络**   —— 限速 / 超时 / 仅 Wi-Fi / 自定义 UA / Referer / 代理 / M3U8 线程
 *   · **稳定性** —— 重试次数 / 重试间隔 / 锁屏保持 / 低电量暂停
 *   · **完整性校验** —— 下完校验 / 深度 CRC 校验 / 记住域名分片数 / 保留分片
 *   · **保存与通知** —— 存储权限 / 通知栏速度 / 完成后动作（打开/震动/响铃）
 *   · **网盘**   —— 免转存 / 自动保存登录态 / 按网盘分线程
 *
 * 保存目录固定为公共 `Download/rom-hub/`（需「所有文件访问」权限，未授权时自动退回
 * App 私有目录，功能不中断），因此不做 SAF 目录选择器。
 */
@Composable
fun DownloadSettingsScreen(bottomInnerPadding: Dp = 0.dp) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var threads by remember { mutableIntStateOf(Prefs.downloadThreads(ctx)) }
    var concurrent by remember { mutableIntStateOf(Prefs.downloadConcurrent(ctx)) }

    var speedLimit by remember { mutableIntStateOf(Prefs.downloadSpeedLimitKb(ctx)) }
    var timeout by remember { mutableIntStateOf(Prefs.downloadTimeout(ctx)) }
    var wifiOnly by remember { mutableStateOf(Prefs.downloadWifiOnly(ctx)) }
    var userAgent by remember { mutableStateOf(Prefs.downloadUserAgent(ctx)) }
    var referer by remember { mutableStateOf(Prefs.downloadReferer(ctx)) }
    var proxyOn by remember { mutableStateOf(Prefs.downloadProxyOn(ctx)) }
    var proxyHost by remember { mutableStateOf(Prefs.downloadProxyHost(ctx)) }
    var proxyPort by remember { mutableIntStateOf(Prefs.downloadProxyPort(ctx)) }
    var m3u8Threads by remember { mutableIntStateOf(Prefs.downloadM3u8Threads(ctx)) }
    var batteryLimit by remember { mutableStateOf(Prefs.downloadBatteryLimit(ctx)) }

    var retry by remember { mutableIntStateOf(Prefs.downloadRetryCount(ctx)) }
    var retryInterval by remember { mutableIntStateOf(Prefs.downloadRetryInterval(ctx)) }
    var keepAwake by remember { mutableStateOf(Prefs.downloadKeepAwake(ctx)) }

    var verify by remember { mutableStateOf(Prefs.downloadVerifyOnFinish(ctx)) }
    var deepVerify by remember { mutableStateOf(Prefs.downloadDeepVerify(ctx)) }
    var rememberDomain by remember { mutableStateOf(Prefs.downloadRememberDomainParts(ctx)) }

    var notifySpeed by remember { mutableStateOf(Prefs.downloadNotifySpeed(ctx)) }
    var vibrate by remember { mutableStateOf(Prefs.downloadVibrate(ctx)) }
    var sound by remember { mutableStateOf(Prefs.downloadSound(ctx)) }
    var autoOpen by remember { mutableStateOf(Prefs.downloadAutoOpen(ctx)) }
    var keepParts by remember { mutableStateOf(Prefs.downloadKeepParts(ctx)) }

    var noTransfer by remember { mutableStateOf(Prefs.downloadWithoutTransfer(ctx)) }
    var autoSaveLogin by remember { mutableStateOf(Prefs.autoSaveLogin(ctx)) }
    var editingPlatform by remember { mutableStateOf<SharePlatform?>(null) }
    var showUaEditor by remember { mutableStateOf(false) }
    var showRefererEditor by remember { mutableStateOf(false) }
    var showProxyEditor by remember { mutableStateOf(false) }
    var showDomainList by remember { mutableStateOf(false) }
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
        subtitle = "性能 · 网络 · 校验 · 通知",
        bottomInnerPadding = bottomInnerPadding,
    ) {
        // ------------------------------------------------ 性能
        item { SectionLabel("性能") }
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
                }
            }
        }

        // ------------------------------------------------ 网络
        item { SectionLabel("网络") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
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

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    NumberRow(
                        label = "连接超时",
                        subtitle = "建连超时 ${timeout}s（网络差可调高）",
                        value = timeout,
                        options = listOf(10, 20, 30, 60),
                        display = { "${it}s" },
                    ) { timeout = it; Prefs.setDownloadTimeout(ctx, it); DownloadManager.syncDownloadPolicies() }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "仅 Wi-Fi 下载",
                        subtitle = if (wifiOnly) "移动网络下不自动开始，避免偷跑流量" else "Wi-Fi 和移动网络都可以下",
                        checked = wifiOnly,
                    ) { wifiOnly = it; Prefs.setDownloadWifiOnly(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    // 自定义 UA
                    SettingRow(
                        label = "自定义 User-Agent",
                        subtitle = if (userAgent.isBlank()) "使用内置的浏览器 UA" else userAgent,
                        valueText = if (userAgent.isBlank()) "默认" else "已设置",
                        onClick = { showUaEditor = true },
                    )

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    // 自定义 Referer
                    SettingRow(
                        label = "自定义 Referer",
                        subtitle = if (referer.isBlank()) "不发送 Referer" else referer,
                        valueText = if (referer.isBlank()) "不发送" else "已设置",
                        onClick = { showRefererEditor = true },
                    )

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    // HTTP 代理
                    SwitchRow(
                        label = "使用 HTTP 代理",
                        subtitle = when {
                            !proxyOn -> "直连，不经过代理"
                            proxyHost.isBlank() || proxyPort !in 1..65535 ->
                                "已开启但没填完整 —— 当前仍按直连走"
                            else -> "经 $proxyHost:$proxyPort"
                        },
                        checked = proxyOn,
                    ) {
                        proxyOn = it
                        Prefs.setDownloadProxyOn(ctx, it)
                        Prefs.applyProxy(ctx)
                    }

                    if (proxyOn) {
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        SettingRow(
                            label = "代理主机与端口",
                            subtitle = if (proxyHost.isBlank()) {
                                "填 IP 或域名，例如 192.168.1.2"
                            } else {
                                "$proxyHost${if (proxyPort in 1..65535) ":$proxyPort" else "（端口未填）"}"
                            },
                            valueText = "编辑",
                            onClick = { showProxyEditor = true },
                        )
                    }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    // M3U8（HLS）分片线程数
                    NumberRow(
                        label = "M3U8 分片线程数",
                        subtitle = "HLS 流（m3u8）同时拉取几段。段很小，开太高容易被源站风控",
                        value = m3u8Threads,
                        options = listOf(1, 2, 4, 8, 16),
                        display = { if (it == 1) "顺序" else "$it" },
                    ) { m3u8Threads = it; Prefs.setDownloadM3u8Threads(ctx, it) }
                }
            }
        }

        // ------------------------------------------------ 稳定性
        item { SectionLabel("稳定性") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    NumberRow(
                        label = "失败自动重试",
                        subtitle = if (retry == 0) "失败后不自动重试" else "失败后自动重试 $retry 次（支持断点续传）",
                        value = retry,
                        options = listOf(0, 1, 3, 5, 10),
                    ) { retry = it; Prefs.setDownloadRetryCount(ctx, it); DownloadManager.syncDownloadPolicies() }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    NumberRow(
                        label = "重试间隔",
                        subtitle = "指数退避基数：第 1 次等 ${retryInterval}s，第 2 次 ${retryInterval * 2}s，第 3 次 ${retryInterval * 4}s",
                        value = retryInterval,
                        options = listOf(1, 2, 5, 10),
                        display = { "${it}s" },
                    ) { retryInterval = it; Prefs.setDownloadRetryInterval(ctx, it); DownloadManager.syncDownloadPolicies() }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "锁屏后保持下载",
                        subtitle = "下载时持有唤醒锁，避免息屏后网络被挂起",
                        checked = keepAwake,
                    ) { keepAwake = it; Prefs.setDownloadKeepAwake(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    // 低电量限制
                    SwitchRow(
                        label = "低电量时暂停下载",
                        subtitle = if (batteryLimit) {
                            "电量低于 15% 且未充电时，不自动开始新任务"
                        } else {
                            "无论电量多少都照常下载"
                        },
                        checked = batteryLimit,
                    ) { batteryLimit = it; Prefs.setDownloadBatteryLimit(ctx, it) }
                }
            }
        }

        // ------------------------------------------------ 完整性校验
        item { SectionLabel("完整性校验") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    SwitchRow(
                        label = "下载完成后校验",
                        subtitle = if (verify) {
                            "比对文件大小，APK 还会读签名指纹（回应「签名不对」的关键）"
                        } else {
                            "不校验。拿到损坏文件也不会有提示，不建议关"
                        },
                        checked = verify,
                    ) { verify = it; Prefs.setDownloadVerifyOnFinish(ctx, it) }

                    if (verify) {
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                        SwitchRow(
                            label = "深度校验（压缩包逐条 CRC）",
                            subtitle = if (deepVerify) {
                                "把 zip/APK/ROM 包完整读一遍校验每一条，能查出字节损坏；大文件会多花几秒"
                            } else {
                                "只比大小 + 读 APK 签名（快，但查不出内容错位）"
                            },
                            checked = deepVerify,
                        ) { deepVerify = it; Prefs.setDownloadDeepVerify(ctx, it) }
                    }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "按域名记住分片数",
                        subtitle = if (rememberDomain) {
                            "同一 CDN 上次开多少线程好用，下次自动套用"
                        } else {
                            "每次都只用上面的全局线程数"
                        },
                        checked = rememberDomain,
                    ) { rememberDomain = it; Prefs.setDownloadRememberDomainParts(ctx, it) }

                    if (rememberDomain) {
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        SettingRow(
                            label = "已记住的域名",
                            subtitle = "点进去可以查看或清掉单个域名的记录",
                            valueText = "${DomainPartLimit.list(ctx).size} 个",
                            onClick = { showDomainList = true },
                        )
                    }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "保留分片临时文件",
                        subtitle = if (keepParts) {
                            "合并后不删 .parts 目录（排查问题时用，会占双倍空间）"
                        } else {
                            "合并完成后删除分片临时文件（省空间）"
                        },
                        checked = keepParts,
                    ) { keepParts = it; Prefs.setDownloadKeepParts(ctx, it); DownloadManager.syncDownloadPolicies() }
                }
            }
        }

        // ------------------------------------------------ 保存与通知
        item { SectionLabel("保存与通知") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
                    // 存储权限入口。
                    // 为什么必须常驻：首启那个引导弹窗是「一次性」的
                    //（Prefs.storagePromptShown），用户点过「以后再说」或升级过
                    // 版本，这个标记就变成 true，弹窗再也不会出现 ——
                    // 升级后很多用户反馈「要权限的提示不见了」。
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

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "通知栏显示速度",
                        subtitle = if (notifySpeed) "完整通知：进度条 + 实时速度" else "精简通知：只显示进度条",
                        checked = notifySpeed,
                    ) { notifySpeed = it; Prefs.setDownloadNotifySpeed(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "完成后自动打开",
                        subtitle = if (autoOpen) "下载完成自动用系统应用打开文件" else "下载完成后不自动打开",
                        checked = autoOpen,
                    ) { autoOpen = it; Prefs.setDownloadAutoOpen(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "完成时震动",
                        subtitle = if (vibrate) "下载完成时震动一下" else "不震动",
                        checked = vibrate,
                    ) { vibrate = it; Prefs.setDownloadVibrate(ctx, it) }

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

                    SwitchRow(
                        label = "完成时提示音",
                        subtitle = if (sound) "下载完成时播放提示音" else "不播放声音",
                        checked = sound,
                    ) { sound = it; Prefs.setDownloadSound(ctx, it) }
                }
            }
        }

        // ------------------------------------------------ 网盘
        item { SectionLabel("网盘") }
        item {
            Card {
                Column(Modifier.padding(vertical = 6.dp)) {
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
            Hint(
                "小提示：线程数不是越高越好。多数 CDN 在 16~32 线程就吃满带宽了，" +
                    "再往上反而容易触发风控。百度建议 4，夸克/115 可到 64。"
            )
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

    // 自定义 UA 编辑
    if (showUaEditor) {
        TextEditorDialog(
            title = "自定义 User-Agent",
            initial = userAgent,
            placeholder = "留空 = 用内置浏览器 UA。例：Mozilla/5.0 (Linux; Android 14) …",
            onDismiss = { showUaEditor = false },
            onSave = { userAgent = it; Prefs.setDownloadUserAgent(ctx, it); showUaEditor = false },
            onPaste = { clipboard.getText()?.text.orEmpty() },
        )
    }

    // 自定义 Referer 编辑
    if (showRefererEditor) {
        TextEditorDialog(
            title = "自定义 Referer",
            initial = referer,
            placeholder = "留空 = 不发送。例：https://www.example.com/",
            onDismiss = { showRefererEditor = false },
            onSave = { referer = it; Prefs.setDownloadReferer(ctx, it); showRefererEditor = false },
            onPaste = { clipboard.getText()?.text.orEmpty() },
        )
    }

    // 已记住的域名分片数
    if (showDomainList) {
        DomainListDialog(onDismiss = { showDomainList = false }, onChanged = { })
    }

    // 代理主机 / 端口编辑
    if (showProxyEditor) {
        ProxyEditorDialog(
            initialHost = proxyHost,
            initialPort = proxyPort,
            onDismiss = { showProxyEditor = false },
            onSave = { h, p ->
                proxyHost = h
                proxyPort = p
                Prefs.setDownloadProxyHost(ctx, h)
                Prefs.setDownloadProxyPort(ctx, p)
                Prefs.applyProxy(ctx)      // 立即生效（重建 OkHttp 客户端）
                showProxyEditor = false
            },
        )
    }
}

/**
 * 代理主机 / 端口编辑弹窗。
 *
 * 主机和端口要一起填才有效 —— 分成两个入口用户容易只填一半就以为好了，
 * 所以放在同一个弹窗里，并给出「留空 = 直连」的明确提示。
 */
@Composable
private fun ProxyEditorDialog(
    initialHost: String,
    initialPort: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(if (initialPort > 0) initialPort.toString() else "") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "HTTP 代理",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "主机填 IP 或域名，端口填 1~65535。两个都填了才会生效。",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))

                Text("主机", fontSize = MiuixTheme.textStyles.footnote1.fontSize, color = cs.onSurface)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (host.isBlank()) {
                        Text(
                            "例如 192.168.1.2",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = cs.onSurfaceVariantSummary,
                        )
                    }
                    BasicTextField(
                        value = host,
                        onValueChange = { host = it },
                        singleLine = true,
                        textStyle = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("端口", fontSize = MiuixTheme.textStyles.footnote1.fontSize, color = cs.onSurface)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (portText.isBlank()) {
                        Text(
                            "例如 7890",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = cs.onSurfaceVariantSummary,
                        )
                    }
                    BasicTextField(
                        value = portText,
                        onValueChange = { v -> portText = v.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        textStyle = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { GhostButton("取消") { onDismiss() } }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("保存") {
                            onSave(host.trim(), portText.toIntOrNull() ?: 0)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 弹窗

/**
 * 单平台线程数选择弹窗。
 *
 * 为什么自己画而不用 MiuixDialog：MiuixDialog 只有标题/正文/两个按钮，
 * 没有内容槽，塞不进选项列表（之前就是因此变成「点进去没选项」）。
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

/** 通用文本编辑弹窗（UA / Referer 用）。 */
@Composable
private fun TextEditorDialog(
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onPaste: () -> String,
) {
    val cs = MiuixTheme.colorScheme
    var text by remember { mutableStateOf(initial) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    title,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (text.isBlank()) {
                        Text(
                            placeholder,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = cs.onSurfaceVariantSummary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("粘贴") { text = onPaste() }
                    GhostButton("清空") { text = "" }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { GhostButton("取消") { onDismiss() } }
                    Box(Modifier.weight(1f)) { PrimaryButton("保存") { onSave(text.trim()) } }
                }
            }
        }
    }
}

/** 已记住的域名分片数列表（可按条删除）。 */
@Composable
private fun DomainListDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    var rev by remember { mutableIntStateOf(0) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "已记住的域名",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                val list = remember(rev) { DomainPartLimit.list(ctx) }
                if (list.isEmpty()) {
                    Text(
                        "还没有记录 —— 下载成功几个文件后会自动攒出来。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = cs.onSurfaceVariantSummary,
                    )
                } else {
                    Text(
                        "同一域名再次下载时会自动套用这里的线程数。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = cs.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    ) {
                        LazyColumn {
                            items(list, key = { it.domain }) { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            e.domain,
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${e.partLimit} 线程" +
                                                if (e.useForSubdomain) " · 含子域名" else "",
                                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                            color = cs.onSurfaceVariantSummary,
                                        )
                                    }
                                    GhostButton("移除") {
                                        DomainPartLimit.remove(ctx, e.domain)
                                        rev++
                                        onChanged()
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton("全部清空") {
                        DomainPartLimit.clear(ctx)
                        rev++
                        onChanged()
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("完成") { onDismiss() }
            }
        }
    }
}

// ---------------------------------------------------------------- 列表行

/**
 * 数值选择行：一排选项，点一个就生效。
 *
 * 档位多于 5 个时自动改成多行网格 —— 线程档位有 9 个（1~256），
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

/** 带右箭头的设置行（点开二级弹窗那种）。 */
@Composable
private fun SettingRow(
    label: String,
    subtitle: String,
    valueText: String,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            valueText,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = cs.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text("›", fontSize = MiuixTheme.textStyles.body1.fontSize, color = cs.onSurfaceVariantSummary)
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
