package org.linbaogu.romhub.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏玻璃。照搬 KernelSU 的 `ui/util/BlurExt.kt`：
 *
 * ```kotlin
 * fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
 *     if (!enableBlur || !isRenderEffectSupported()) return null
 *     val surfaceColor = MiuixTheme.colorScheme.surface
 *     return rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
 * }
 * fun BlurredBar(backdrop, blurActive = true, content) {
 *     Box(if (blurActive && backdrop != null) Modifier.textureBlur(
 *         backdrop, RectangleShape, blurRadius = 25f,
 *         colors = BlurColors(listOf(BlendColorEntry(surface.copy(0.87f))))) else Modifier) { content() }
 * }
 * ```
 *
 * 顶栏的采样层必须**只包含页面内容、不包含顶栏自己** —— 否则顶栏会采到上一帧的自己，
 * 越糊越实。KSU 的做法是：顶栏放在 Scaffold 的 topBar 槽（在外面），
 * 内容用 `Modifier.layerBackdrop(backdrop)` 录进采样层。这里完全一致。
 */

/**
 * 玻璃混色强度。KSU 用 0.87（偏实心）。这里调低一点，让极光的颜色能透到顶栏上；
 * 想更实心就往 0.87 调，想更透就往 0.5 调。
 */
private const val BAR_BLEND_ALPHA = 0.66f

/** 顶栏模糊半径，照 KernelSU 的 25f。 */
private const val BAR_BLUR_RADIUS = 25f

/** 建一个给顶栏采样的背景层。设备不支持就返回 null，顶栏退化成不透明。 */
@Composable
fun rememberBarBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    // ⚠ drawRect 必须在这里取好颜色：rememberLayerBackdrop 的 lambda 不是 composable 上下文
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

@Composable
fun BlurredBar(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = BAR_BLUR_RADIUS,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = BAR_BLEND_ALPHA)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
