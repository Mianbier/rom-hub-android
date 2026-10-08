package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.data.brand.Brand
import org.linbaogu.romhub.data.brand.BrandCatalog
import org.linbaogu.romhub.data.brand.BrandFamily
import org.linbaogu.romhub.ui.common.BrandLogo
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「固件下载」的第一层 —— **品牌页**。
 *
 * 结构（2026-10-07 按用户要求重排）：
 * ```
 * 固件下载
 *  ├─ 小米专区        ← 原来「固件下载」里那套机型库（服务端数据）
 *  ├─ 已接入品牌       ← OPPO / 一加 / 真我 / vivo / iQOO（本地数据层）
 *  └─ 即将支持         ← 魅族 / 红魔 / 联想（数据源接入中）
 * ```
 * 每个品牌前面都有 [BrandLogo]（品牌主色 + 字标），一眼能认出是谁。
 */
@Composable
fun BrandPickerScreen(
    subtitle: String = "",
    bottomInnerPadding: Dp = 0.dp,
    onPick: (Brand) -> Unit,
    onNotReady: (String) -> Unit,
    onOpenXiaomi: () -> Unit,
) {
    val ready = BrandCatalog.all.filter {
        it.family != BrandFamily.XIAOMI && it.family != BrandFamily.OTHER
    }
    val soon = BrandCatalog.all.filter { it.family == BrandFamily.OTHER }

    ListScreen(
        title = "固件下载",
        subtitle = subtitle,
        largeTitle = "选择品牌",
        bottomInnerPadding = bottomInnerPadding,
    ) {
        // ------------------------------------------------ 小米专区
        item { SectionLabel("小米专区") }
        item {
            val xiaomi = BrandCatalog.byKey("xiaomi") ?: BrandCatalog.all.first()
            Card(
                onClick = onOpenXiaomi,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandLogo(xiaomi, size = 50.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 14.dp)
                    ) {
                        Text(
                            "小米 / Redmi",
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "MIUI / HyperOS 官方包 · 含内测 / Beta / 移植包",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp),
                    )
                }
            }
        }

        // ------------------------------------------------ 已接入品牌
        item { SectionLabel("已接入 · 全量 ROM 包") }
        items(ready.size, key = { ready[it].key }) { i ->
            BrandRow(brand = ready[i], summary = familyLabel(ready[i].family)) {
                onPick(ready[i])
            }
        }

        // ------------------------------------------------ 即将支持
        if (soon.isNotEmpty()) {
            item { SectionLabel("即将支持") }
            items(soon.size, key = { soon[it].key }) { i ->
                BrandRow(brand = soon[i], summary = "数据源接入中", dim = true) {
                    onNotReady(soon[i].nameZh)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                Hint(
                    "固件包体积通常 5–12 GB，请确保存储空间充足。\n" +
                        "下载直链来自各品牌官方 OTA 接口，失效时会自动换源重试。"
                )
            }
        }
    }
}

/** 一行品牌：品牌标块 + 名字 + 说明 + 箭头。 */
@Composable
private fun BrandRow(
    brand: Brand,
    summary: String,
    dim: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(brand, size = 46.dp, dim = dim)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    brand.nameZh,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Icon(
                MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.width(18.dp),
            )
        }
    }
}

private fun familyLabel(f: BrandFamily): String = when (f) {
    BrandFamily.COLOR_OS -> "ColorOS 官方全量包"
    BrandFamily.ORIGIN_OS -> "OriginOS 官方全量包"
    BrandFamily.XIAOMI -> "MIUI / HyperOS"
    BrandFamily.OTHER -> "其他"
}
