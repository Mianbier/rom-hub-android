// Adapted from Kyant0/AndroidLiquidGlass (Apache 2.0)
// via KernelSU ui/component/liquid/Vibrancy.kt — 原样移植，保持视觉效果一致。

package org.linbaogu.romhub.ui.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
