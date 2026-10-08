package org.linbaogu.romhub.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.data.brand.Brand
import top.yukonga.miuix.kmp.basic.Text

/**
 * 品牌标块 —— 品牌页上每个品牌前面那块「品牌图」。
 *
 * 做法是「品牌主色渐变 + 品牌字标」，而不是去搬各家的商标图片：
 *  · **离线可用**：不依赖任何图床/外链，断网也是完整的；
 *  · 不涉及商标素材的二次分发；
 *  · 自动跟随深浅色主题，比写死一张 PNG 干净得多；
 *  · 字标长度自动调字号 —— `MI` / `1+` 能放大，`REDMAGIC` 会缩到刚好放得下。
 */
@Composable
fun BrandLogo(
    brand: Brand,
    size: Dp = 46.dp,
    dim: Boolean = false,
) {
    // 品牌主色是 0xAARRGGBB 的 Long，塞进 ULong 高 32 位才是 Compose 认的 ARGB
    val base = Color(brand.color.toULong() shl 32)
    val top = if (dim) base.copy(alpha = 0.34f) else base.copy(alpha = 0.92f)
    val bottom = if (dim) base.copy(alpha = 0.20f) else base

    // 字标越长字号越小
    val factor = when (brand.wordmark.length) {
        0, 1, 2 -> 0.40f
        3, 4 -> 0.27f
        else -> 0.185f
    }
    val fontSize = with(LocalDensity.current) { (size * factor).toSp() }

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = brand.wordmark,
            color = Color.White.copy(alpha = if (dim) 0.72f else 1f),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
