package org.linbaogu.romhub.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

/**
 * 加载指示器 —— 照 HyperCeiler 那个样子画的：
 * **一圈细圆环（留个小缺口）+ 一个小圆点绕着环跑**。
 *
 * 为什么不用 `CircularProgressIndicator`：它的观感是「一段弧在转」，
 * 和 HyperCeiler 那个「环 + 点」不是一回事，放一起会显得两套设计混着来。
 * 这里直接用 Canvas 画，尺寸/粗细/转速都可控，也不依赖任何额外资源。
 *
 * @param size  整体直径
 * @param color 环与点的颜色（默认跟随主题）
 */
@Composable
fun HyperLoading(
    size: Dp = 30.dp,
    color: Color? = null,
    modifier: Modifier = Modifier,
) {
    val tint = color ?: MiuixTheme.colorScheme.onSurface

    // 0 → 360 一直转。用 Animatable + 无限循环，帧率跟随系统刷新率。
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            angle.snapTo(0f)
            angle.animateTo(
                360f,
                animationSpec = tween(durationMillis = 1100, easing = LinearEasing),
            )
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.075f      // 环的粗细
        val radius = this.size.minDimension / 2f - stroke / 2f
        val dotR = stroke * 0.85f                          // 小圆点半径
        val gap = 48f                                      // 环上的缺口角度

        // 环：从缺口后面开始画，留出缺口
        drawArc(
            color = tint,
            startAngle = angle.value + gap,
            sweepAngle = 360f - gap,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // 小圆点：跟在缺口的前沿
        val rad = Math.toRadians(angle.value.toDouble())
        drawCircle(
            color = tint,
            radius = dotR,
            center = Offset(
                center.x + (radius * cos(rad)).toFloat(),
                center.y + (radius * sin(rad)).toFloat(),
            ),
        )
    }
}

/**
 * 整页加载态：白底（跟随主题的 surface）+ 居中那个加载图标。
 *
 * 用在 App 冷启动、第一批数据还没到的时候 —— 以前这段时间是**空屏**，
 * 看起来像卡住了。
 */
@Composable
fun HyperLoadingPage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        HyperLoading(size = 32.dp)
    }
}

/**
 * 列表内的加载占位：居中一个加载图标。
 *
 * 专门用来替掉以前那些「正在加载…」「载入中…」文字 —— 全 App 只留这一种加载观感。
 */
@Composable
fun HyperLoadingInline(
    modifier: Modifier = Modifier,
    padding: Dp = 36.dp,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        HyperLoading(size = 26.dp)
    }
}
