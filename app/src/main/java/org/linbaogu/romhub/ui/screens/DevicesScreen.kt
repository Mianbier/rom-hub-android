package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.linbaogu.romhub.data.DeviceItem
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.ui.common.Chip
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.HcSearchBar
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.StateChip
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val HF_IMG = "https://data.hyperos.fans/assets/images/"

fun deviceImgUrl(d: DeviceItem): String {
    val raw = d.image
    if (raw.startsWith("http")) return raw
    if (raw.isNotBlank()) return HF_IMG + raw
    if (d.code.isNotBlank()) return HF_IMG + d.code + ".png"
    return ""
}

@Composable
fun DeviceThumb(d: DeviceItem, size: Dp = 48.dp) {
    val url = deviceImgUrl(d)
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = d.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = d.displayName.take(1),
                color = cs.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ---------------------------------------------------------------- 品牌 / 系列

/**
 * 品牌归一。
 *
 * ⚠ 数据库里 `brand` 有 **mi / xiaomi / redmi / poco 四种**，而且那 65 台 `brand=xiaomi`
 * 的 `brand_zh` 就是裸的英文 "xiaomi" 没翻译 —— 直接拿 `brand_zh` 当标签就会多出
 * 一个「xiaomi」分类。照网站 site_page.py 的注释：**小米要合并 mi + xiaomi**。
 */
private fun brandKey(d: DeviceItem): String = when (d.brand.trim().lowercase()) {
    "mi", "xiaomi" -> "小米"
    "redmi" -> "红米"
    "poco" -> "POCO"
    else -> d.brandZh.ifBlank { d.brand }.ifBlank { "其他" }
}

/** 固定四个品牌，不按数据推（否则又会冒出第五个）。与网站一致。 */
private val BRANDS = listOf("全部", "小米", "红米", "POCO")

/** 系列归一，逐条照搬网站 site_page.py 的 groupOf()。 */
private fun groupKey(d: DeviceItem): String {
    val s = (d.series + " " + d.nameZh).lowercase()
    return when {
        "平板" in s || "pad" in s -> "平板"
        "mix" in s -> "MIX"
        "civi" in s -> "Civi"
        "note" in s -> "Note 系列"
        "turbo" in s -> "Turbo"
        Regex("k\\d|k\\s?系列").containsMatchIn(s) -> "K 系列"
        Regex("a\\d|a\\s?系列").containsMatchIn(s) -> "其他"
        Regex("\\d").containsMatchIn(s) -> "数字系列"
        else -> "其他"
    }
}

/** 栏目顺序，照网站 GROUPS。 */
private val GROUP_ORDER = listOf("数字系列", "K 系列", "Note 系列", "Turbo", "MIX", "Civi", "平板", "其他")

/**
 * 型号数字，用于「最新机型排最上面」：小米18 > 17 > 16，Redmi K90 > K80。
 * 照网站 modelNum()。取不到数字就 -1（排到最后）。
 */
private fun modelNum(d: DeviceItem): Int =
    Regex("\\d{1,4}").find(d.nameZh.ifBlank { d.displayName })?.value?.toIntOrNull() ?: -1

@Composable
fun DevicesScreen(
    subtitle: String,
    bottomInnerPadding: Dp,
    onOpenDevice: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf(Repo.cachedDevices()) }
    var loading by remember { mutableStateOf(all.isEmpty()) }
    var error by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("全部") }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = all.isEmpty()
        error = ""
        runCatching { Repo.devices(ctx, force = reload > 0) }
            .onSuccess { all = it; loading = false }
            .onFailure {
                error = it.message ?: "加载失败"
                loading = false
            }
    }

    // 输入防抖
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(typed) {
        delay(220)
        keyword = typed
    }

    /** 过滤 → 按「最新在前」排序 → 按系列分栏。 */
    val sections = remember(all, keyword, brand) {
        val k = keyword.trim().lowercase()
        val list = all.filter { d ->
            val okBrand = brand == "全部" || brandKey(d) == brand
            val okKey = k.isBlank() ||
                    d.displayName.lowercase().contains(k) ||
                    d.code.lowercase().contains(k) ||
                    d.nameEn.lowercase().contains(k) ||
                    d.latestVersion.lowercase().contains(k)
            okBrand && okKey
        }.sortedWith(
            // 型号数字降序（最新在前），同号按最近 ROM 更新日期兜底 —— 照网站
            compareByDescending<DeviceItem> { modelNum(it) }
                .thenByDescending { it.lastDate },
        )

        list.groupBy(::groupKey)
            .entries
            .sortedBy { (g, _) ->
                val i = GROUP_ORDER.indexOf(g)
                if (i < 0) GROUP_ORDER.size else i
            }
            .map { (g, items) -> g to items }
            .filter { (_, items) -> items.isNotEmpty() }
    }

    val total = sections.sumOf { it.second.size }

    ListScreen(
        title = "机型",
        largeTitle = "机型库",
        subtitle = subtitle,
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        item {
            HcSearchBar(
                value = typed,
                onValueChange = { typed = it },
                hint = "搜索机型名、代号或版本号",
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BRANDS) { b ->
                    Chip(text = b, selected = b == brand, onClick = { brand = b })
                }
            }
        }

        if (loading) {
            item { Hint("正在拉取机型库…") }
        } else if (error.isNotBlank()) {
            item { ErrorHint(error) { reload++ } }
        } else if (total == 0) {
            item { Hint("没有匹配的机型") }
        }

        item {
            Text(
                text = buildString {
                    append("共 ${total} 款机型")
                    if (brand != "全部") append(" · $brand")
                    if (sections.size > 1) append(" · ${sections.size} 个系列")
                },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp),
            )
        }

        // HyperCeiler 结构：每个系列一张实底大卡片，设备是卡内的行
        sections.forEach { (group, list) ->
            item(key = "sec_$group") { SectionLabel(group) }
            item(key = "grp_$group") {
                HcGroup {
                    list.forEachIndexed { i, d ->
                        if (i > 0) HcDivider(startIndent = 16.dp + 52.dp + 14.dp)
                        DeviceRow(d) { onOpenDevice(d.code) }
                    }
                }
            }
        }
    }
}

/** HyperCeiler 式设备行：圆角方形图标 + 名称/代号 + 细箭头，装在系列大卡片里。 */
@Composable
private fun DeviceRow(d: DeviceItem, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceThumb(d, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = d.displayName,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                // 品牌标签走归一后的名字，避免出现裸的 "xiaomi"
                StateChip(state = "", textOverride = brandKey(d))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(d.code)
                    if (d.latestVersion.isNotBlank()) append(" · ${d.latestVersion}")
                },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (d.romCount > 0) "${d.romCount}" else "",
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = cs.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = cs.onSurfaceVariantSummary.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )
    }
}
