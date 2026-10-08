package org.linbaogu.romhub.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * App 图标（与桌面图标同一套比例）。
 */
@Composable
fun AppLogo(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    val g = Brush.linearGradient(listOf(Color(0xFF4F7CF6), Color(0xFF7C5CF6)))
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 底色
            drawRoundRect(
                brush = g,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.24f, w * 0.24f),
                size = this.size,
            )

            // 白色下载箭头 + 底座
            val cx = w / 2f
            val shaftW = w * 0.085f
            val shaftTop = h * 0.24f
            val shaftBottom = h * 0.50f

            drawRect(
                color = Color.White,
                topLeft = Offset(cx - shaftW / 2f, shaftTop),
                size = androidx.compose.ui.geometry.Size(shaftW, shaftBottom - shaftTop),
            )

            val head = Path().apply {
                moveTo(cx - w * 0.16f, h * 0.46f)
                lineTo(cx + w * 0.16f, h * 0.46f)
                lineTo(cx, h * 0.66f)
                close()
            }
            drawPath(head, Color.White)

            val barTop = h * 0.725f
            val barH = h * 0.075f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - w * 0.22f, barTop),
                size = androidx.compose.ui.geometry.Size(w * 0.44f, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2f, barH / 2f),
            )
        }
    }
}

private val DefaultLogoSize = 84.dp
