package org.linbaogu.romhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 当前是否深色。KernelSU 用的是 `ui.theme.isInDarkTheme()`，这里保持一致。
 */
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 卡片等「容器色」的不透明度。
 *
 * 极光铺满所有界面后，容器做成微透明，流动的色彩才会从卡片里透出来。
 * 原来是不透明的 `surfaceContainer`（浅色下就是纯白），卡片会把光效整块挡住，
 * 看起来就像「没有光效」。
 *
 * ⚠ 全局只有一个旋钮：想更实心就往 0.85 调，想更透就往 0.6 调。
 */
private const val CONTAINER_ALPHA = 0.72f

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = LocalIsDark.current

@Composable
fun RomHubTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        // 只动「容器」这三档（Card 默认取 surfaceContainer），
        // surface 保持不透明 —— 极光的底色和底栏玻璃都靠它。
        surfaceContainer = base.surfaceContainer.copy(alpha = CONTAINER_ALPHA),
        surfaceContainerHigh = base.surfaceContainerHigh.copy(alpha = CONTAINER_ALPHA),
        surfaceContainerHighest = base.surfaceContainerHighest.copy(alpha = CONTAINER_ALPHA),
    )
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(LocalIsDark provides dark) {
            content()
        }
    }
}
