package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.data.brand.Brand
import org.linbaogu.romhub.data.brand.BrandCatalog
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.brand.DeviceEntry
import org.linbaogu.romhub.data.brand.RomSource
import org.linbaogu.romhub.data.brand.Source
import org.linbaogu.romhub.data.brand.VersionEntry
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.ui.common.Chip
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.HcRow
import org.linbaogu.romhub.ui.common.HcSearchBar
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.common.VSpace
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.linbaogu.romhub.ui.common.HyperLoading

// ============================================================== 机型列表

@Composable
fun BrandDevicesScreen(
    brandKey: String,
    bottomInnerPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onOpenDevice: (DeviceEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val brand = remember(brandKey) { BrandCatalog.byKey(brandKey) ?: BrandCatalog.all.first() }

    val source = remember { RomSource(ctx.cacheDir) }

    var devices by remember { mutableStateOf<List<DeviceEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

    // 手动刷新：强制跳过 6 小时缓存重新拉源站（新增机型只能靠这个进来）
    suspend fun doRefresh() {
        if (refreshing) return
        refreshing = true
        error = null
        runCatching { source.refreshDevices(brand) }
            .onSuccess { devices = it; SnackbarController.show("已更新到最新机型列表") }
            .onFailure { error = it.message ?: "刷新失败" }
        refreshing = false
    }

    LaunchedEffect(brandKey) {
        devices = null
        error = null
        runCatching { source.devicesOf(brand) }
            .onSuccess { devices = it }
            .onFailure { error = it.message ?: "机型列表加载失败" }
    }

    val all = devices.orEmpty()
    val seriesList = remember(all) { all.map { it.series }.filter { it.isNotBlank() }.distinct() }

    // 搜索 + 系列筛选
    val filtered = remember(all, keyword, series) {
        all.filter { d ->
            (series.isBlank() || d.series == series) &&
                (keyword.isBlank() || d.name.contains(keyword, true) ||
                    d.codename.contains(keyword, true))
        }
    }

    // 按系列分组，组内按机型名排
    val grouped = remember(filtered) {
        filtered.groupBy { it.series.ifBlank { "其他" } }
            .toList()
            .sortedBy { it.first }
    }

    ListScreen(
        title = brand.nameZh,
        subtitle = devices?.let { "${it.size} 款机型" } ?: "",
        largeTitle = brand.nameZh,
        onBack = onBack,
        bottomInnerPadding = bottomInnerPadding,
        actions = {
            IconButton(onClick = { scope.launch { doRefresh() } }, enabled = !refreshing) {
                if (refreshing) {
                    HyperLoading(size = 18.dp)
                } else {
                    Icon(MiuixIcons.Refresh, contentDescription = "刷新机型列表")
                }
            }
        },
    ) {
        item {
            HcSearchBar(
                value = keyword,
                onValueChange = { keyword = it },
                hint = "搜索机型或代号",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (all.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    item {
                        Chip(
                            text = "全部 ${all.size}",
                            selected = series.isBlank(),
                            onClick = { series = "" },
                        )
                    }
                    items(seriesList) { s ->
                        val n = all.count { it.series == s }
                        Chip(
                            text = "$s $n",
                            selected = series == s,
                            onClick = { series = if (series == s) "" else s },
                        )
                    }
                }
            }
        }

        // 内置机型要明确标出来，不能让用户以为那是在线全量历史版本。
        // 注意不是「在线清单不可用」—— 现在在线清单和内置表是**合并**展示的，
        // 只要有任何一台是内置的就提示，文案要说清区别在哪。
        if (all.any { it.source == Source.FALLBACK }) {
            item {
                val n = all.count { it.source == Source.FALLBACK }
                Hint(
                    "列表里有 $n 款是内置机型（标「内置」）。" +
                        "在线数据源更新有滞后，新机常常晚一两个月才收录，" +
                        "内置表用来补上这段空窗。内置机型暂时没有直链，" +
                        "等数据源收录后就能下载了。",
                )
            }
        }

        when {
            error != null -> item { ErrorHint(error!!) { devices = null; error = null } }

            devices == null -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) { HyperLoading() }
            }

            all.isEmpty() -> item { Hint("这个品牌暂时没有机型数据") }

            else -> {
                grouped.forEach { (name, items) ->
                    item(key = "s$name") { SectionLabel(name) }
                    item(key = "g$name") {
                        HcGroup {
                            items.forEach { d ->
                                HcRow(
                                    title = d.displayName,
                                    subtitle = buildString {
                                        // 内置机型的 versionCount 恒为 1（只有一个占位版本），
                                        // 照抄会显示成「1 个版本」，用户以为只有一版可下。
                                        // 这种情况要说清是实时查最新。
                                        if (d.source == Source.FALLBACK) {
                                            append("待数据源收录")
                                        } else {
                                            append("${d.versionCount} 个版本")
                                        }
                                        if (d.region.isNotBlank()) append(" · ${d.region}")
                                        if (d.fileType.isNotBlank()) append(" · ${d.fileType}")
                                    },
                                    showArrow = true,
                                    onClick = { onOpenDevice(d) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================== 版本列表

@Composable
fun BrandVersionsScreen(
    brandKey: String,
    deviceName: String,
    series: String,
    bottomInnerPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onGoDownloader: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val brand = remember(brandKey) { BrandCatalog.byKey(brandKey) ?: BrandCatalog.all.first() }
    val source = remember { RomSource(ctx.cacheDir) }

    var device by remember { mutableStateOf<DeviceEntry?>(null) }
    var versions by remember { mutableStateOf<List<VersionEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var flashFilter by remember { mutableStateOf("") }

    // 正在取直链的那一行。取直链是网络往返，没有加载态用户会以为按钮坏了
    var busyVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(brandKey, deviceName) {
        versions = null
        error = null
        runCatching {
            val list = source.devicesOf(brand)
            val d = list.firstOrNull { it.name == deviceName && it.series == series }
                ?: list.firstOrNull { it.name == deviceName }
                ?: list.first()
            device = d
            source.versionsOf(brand, d)
        }.onSuccess { versions = it }
            .onFailure { error = it.message ?: "版本列表加载失败" }
    }

    val all = versions.orEmpty()
    val flashList = remember(all) {
        all.map { it.flashType }.filter { it.isNotBlank() }.distinct()
    }
    val filtered = remember(all, keyword, flashFilter) {
        all.filter { v ->
            (flashFilter.isBlank() || v.flashType == flashFilter) &&
                (keyword.isBlank() || v.displayName.contains(keyword, true))
        }
    }

    ListScreen(
        title = deviceName,
        subtitle = device?.let {
            if (it.source == Source.FALLBACK) "待数据源收录" else "${it.versionCount} 个版本"
        } ?: "",
        largeTitle = deviceName,
        onBack = onBack,
        bottomInnerPadding = bottomInnerPadding,
    ) {
        device?.let { d ->
            if (d.description.isNotBlank()) {
                item { Hint(d.description) }
            }
            if (d.source == Source.FALLBACK) {
                item {
                    Hint(
                        "这是内置机型：在线数据源还没收录它，所以暂时没有下载直链。" +
                            "等数据源更新后，这里会显示它的全部历史版本 —— " +
                            "下拉刷新可重新拉取。",
                    )
                }
            }
        }

        item {
            HcSearchBar(
                value = keyword,
                onValueChange = { keyword = it },
                hint = "搜索版本号",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // 下载线程速选：ROM 包大、源站各异，机型页直接给一排档位，
        // 免得每次都去「网盘下载器 → 设置」里翻。
        item {
            val cur = Prefs.downloadThreads(ctx)
            Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "下载线程",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$cur 线程",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Prefs.THREAD_OPTIONS) { n ->
                        Chip(text = "$n", selected = n == cur) { Prefs.setDownloadThreads(ctx, n) }
                    }
                }
                // 高并发是真有代价的：可能直接把源站 IP 打成风控黑名单，
                // 那样反而一条都下不了。必须把风险摆在选择旁边。
                if (cur >= 32) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠ $cur 线程并发很高，源站可能判定为异常流量并临时限制该网络。" +
                            "遇到取直链失败请调回 16 或更低。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }

        if (flashList.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    item {
                        Chip(
                            text = "全部",
                            selected = flashFilter.isBlank(),
                            onClick = { flashFilter = "" },
                        )
                    }
                    items(flashList) { f ->
                        Chip(
                            text = f,
                            selected = flashFilter == f,
                            onClick = { flashFilter = if (flashFilter == f) "" else f },
                        )
                    }
                }
            }
        }

        when {
            error != null -> item { ErrorHint(error!!) { versions = null; error = null } }

            versions == null -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) { HyperLoading() }
            }

            filtered.isEmpty() -> item { Hint("没有符合条件的版本") }

            else -> item {
                HcGroup {
                    filtered.forEach { v ->
                        VersionRow(
                            version = v,
                            busy = busyVersion == v.displayName,
                            onDownload = {
                                busyVersion = v.displayName
                                scope.launch {
                                    downloadRom(source, brand, v, onGoDownloader)
                                    busyVersion = null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: VersionEntry,
    busy: Boolean,
    onDownload: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    HcRow(
        title = version.displayName,
        subtitle = buildString {
            append(version.flashType.ifBlank { "全量包" })
            if (version.sizeText.isNotBlank()) append(" · ${version.sizeText}")
            if (version.releaseDate.isNotBlank()) append(" · ${version.releaseDate}")
        },
        onClick = if (busy) null else onDownload,
        trailing = {
            if (busy) {
                HyperLoading(size = 18.dp)
            } else {
                Text(
                    "下载",
                    color = cs.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}

/**
 * 取直链并交给下载器。
 *
 * 三条取链路径由 [RomSource] 内部编排（静态直链 → opusrom 解析 → 官方接口），
 * 这里只负责把结果入队并如实告诉用户「拿到的是不是他选的那一版」。
 */
private suspend fun downloadRom(
    source: RomSource,
    brand: Brand,
    version: VersionEntry,
    onGoDownloader: () -> Unit,
) {
    // 取直链可能要十几秒（签名 challenge + PoW + 官方接口往返），
    // 不先说一声的话界面毫无反应，用户只会以为按钮坏了
    SnackbarController.show("正在获取下载直链…")

    val link = runCatching { source.resolveLink(brand, version) }
    link.exceptionOrNull()?.let {
        // 「取直链失败」前缀只在这里加一次。
        // 之前 OpusResolver / RomSource / 这里各加一层，用户看到的是
        // 「取直链失败：取直链失败：取直链失败：HTTP 502 …」。
        SnackbarController.show("取直链失败：${it.message ?: "未知原因"}")
        return
    }

    val got = link.getOrThrow()
    val task = runCatching {
        DownloadManager.add(
            url = got.url,
            fileName = guessRomFileName(version),
            subDir = brand.nameZh,
        )
    }.getOrNull()

    if (task == null) {
        SnackbarController.show("加入下载队列失败")
        return
    }

    // 拿到的是最新版而不是用户选的那版，必须说清楚，不能假装一致
    SnackbarController.show(
        if (got.isVersionExact) "已开始下载：${task.fileName}"
        else "注意：服务端只给到最新版（${got.note}），已按最新版开始下载"
    )
    onGoDownloader()
}

/** 从版本号里猜一个像样的文件名。 */
private fun guessRomFileName(v: VersionEntry): String {
    val safe = v.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val ext = if (v.flashType.contains("卡刷", true) || v.displayName.endsWith(".zip", true)) {
        "zip"
    } else {
        "bin"
    }
    return "${v.opus.oplusModel.ifBlank { "rom" }}_$safe.$ext"
}
