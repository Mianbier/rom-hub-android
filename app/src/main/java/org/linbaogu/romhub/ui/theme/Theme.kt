package org.linbaogu.romhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.linbaogu.romhub.R
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 当前是否深色。KernelSU 用的是 `ui.theme.isInDarkTheme()`，这里保持一致。
 */
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 全局字体：MiSans（HyperOS / HyperCeiler 同款）。
 *
 * 只打包 4 个常用字重（Regular 400 / Medium 500 / Demibold 600 / Bold 700），
 * 其余字重回退到最近的档位 —— Compose 会自动挑最接近的。
 */
val MiSans: FontFamily = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_demibold, FontWeight.SemiBold),
    Font(R.font.misans_bold, FontWeight.Bold),
)

/** M3 Typography 也指到 MiSans（少数直接用 Typography 的地方保持一致）。 */
val MiSansTypography = Typography().withFontFamily(MiSans)

private fun Typography.withFontFamily(family: FontFamily): Typography = this.copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * 卡片等「容器色」的不透明度。
 *
 * HyperCeiler 的卡片是**完全实底**的（浅色纯白 / 深色纯灰），
 * 流动光效只在卡片之间的缝隙和列表首尾露出来。
 */
private const val CONTAINER_ALPHA = 1.0f

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
    // 全套 miuix 文本样式指到 MiSans —— HyperCeiler 的字体观感
    val d = defaultTextStyles()
    val styles = d.copy(
        main = d.main.copy(fontFamily = MiSans),
        paragraph = d.paragraph.copy(fontFamily = MiSans),
        body1 = d.body1.copy(fontFamily = MiSans),
        body2 = d.body2.copy(fontFamily = MiSans),
        button = d.button.copy(fontFamily = MiSans),
        footnote1 = d.footnote1.copy(fontFamily = MiSans),
        footnote2 = d.footnote2.copy(fontFamily = MiSans),
        headline1 = d.headline1.copy(fontFamily = MiSans),
        headline2 = d.headline2.copy(fontFamily = MiSans),
        subtitle = d.subtitle.copy(fontFamily = MiSans),
        title1 = d.title1.copy(fontFamily = MiSans),
        title2 = d.title2.copy(fontFamily = MiSans),
        title3 = d.title3.copy(fontFamily = MiSans),
        title4 = d.title4.copy(fontFamily = MiSans),
    )
    MiuixTheme(colors = colors, textStyles = styles) {
        CompositionLocalProvider(LocalIsDark provides dark) {
            content()
        }
    }
}
