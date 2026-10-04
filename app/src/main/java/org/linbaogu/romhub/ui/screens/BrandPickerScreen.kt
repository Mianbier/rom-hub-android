package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.data.brand.Brand
import org.linbaogu.romhub.data.brand.BrandCatalog
import org.linbaogu.romhub.data.brand.BrandFamily
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 品牌选择主页。
 *
 * 只放**已经接了数据源**的品牌：
 *  - 小米走原来的服务端接口（有独立页面，不在这里）
 *  - ColorOS 系（OPPO / 一加 / 真我）与 OriginOS 系（vivo / iQOO）走本地这套数据层
 *  - 魅族 / 红魔 / 联想暂未接数据源，列出来但标注「即将支持」，点了给提示而不是进空页
 */
@Composable
fun BrandPickerScreen(
    subtitle: String = "",
    bottomInnerPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onPick: (Brand) -> Unit,
    onNotReady: (String) -> Unit,
    onOpenXiaomi: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme

    val ready = BrandCatalog.all.filter { it.family != BrandFamily.XIAOMI && it.family != BrandFamily.OTHER }
    val soon = BrandCatalog.all.filter { it.family == BrandFamily.OTHER }

    ListScreen(
        title = "固件下载",
        subtitle = subtitle,
        largeTitle = "选择品牌",
        bottomInnerPadding = bottomInnerPadding,
    ) {
        item {
            SectionLabel("小米 · 走原版服务端")
        }
        item {
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
                    BrandBadge("小米", 0xFFFF6900)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 14.dp)
                    ) {
                        Text("小米 / Redmi", fontWeight = FontWeight.Medium)
                        Text(
                            "MIUI / HyperOS 官方包",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = cs.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        item { SectionLabel("已接入 · 全量 ROM 包") }

        items(ready.size, key = { ready[it].key }) { i ->
            val brand = ready[i]
            Card(
                onClick = { onPick(brand) },
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
                    BrandBadge(brand.nameZh.take(1), brand.color)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 14.dp)
                    ) {
                        Text(brand.nameZh, fontWeight = FontWeight.Medium)
                        Text(
                            familyLabel(brand.family),
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = cs.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        if (soon.isNotEmpty()) {
            item { SectionLabel("即将支持") }
            items(soon.size, key = { soon[it].key }) { i ->
                val brand = soon[i]
                Card(
                    onClick = { onNotReady(brand.nameZh) },
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
                        BrandBadge(brand.nameZh.take(1), brand.color, dim = true)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                        ) {
                            Text(brand.nameZh, fontWeight = FontWeight.Medium)
                            Text(
                                "数据源接入中",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = cs.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
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

@Composable
private fun BrandBadge(text: String, color: Long, dim: Boolean = false) {
    val base = Color(color.toULong() shl 32)
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (dim) base.copy(alpha = 0.4f) else base),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun familyLabel(f: BrandFamily): String = when (f) {
    BrandFamily.COLOR_OS -> "ColorOS 官方全量包"
    BrandFamily.ORIGIN_OS -> "OriginOS 官方全量包"
    BrandFamily.XIAOMI -> "MIUI / HyperOS"
    BrandFamily.OTHER -> "其他"
}
