/*
 * This file is part of ROM Hub, released under the GNU Affero General Public License v3.0.
 *
 * 本文件移植 / 改写自 HyperCeiler（AGPL-3.0，Copyright (C) 2023-2026 HyperCeiler
 * Contributions），或为其等价替身实现 —— 完整来源与鸣谢见项目根目录 NOTICE.md。
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.linbaogu.romhub.hc

import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.linbaogu.romhub.R
import org.linbaogu.romhub.hc.about.controller.BgEffectController

/**
 * 「关于」页的**流动光效背景** —— 就是 HyperCeiler 关于页那一层会缓慢流动的彩色光。
 *
 * 实现完全照搬 HyperCeiler：`res/layout/app_about_bg.xml` + `BgEffectController`
 * + `BgEffectPainter`（AGSL 着色器 `res/raw/bg_frag.glsl`，用 `View.setRenderEffect`
 * 把 shader 直接渲染在那一层 View 上）。
 *
 * 和 App 其它地方的背景无关 —— 用户明确要求：别的地方要干净的纯色底，只有关于页保留这个光效。
 *
 * ⚠️ `RuntimeShader` 要 Android 13(API 33) 以上，低版本直接不挂（退化成透明，不会崩）。
 */
@Composable
fun HcAboutBg(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val bg = LayoutInflater.from(ctx).inflate(R.layout.app_about_bg, null)

            val host = object : FrameLayout(ctx) {
                private var controller: BgEffectController? = null

                override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                    super.onSizeChanged(w, h, oldw, oldh)
                    if (controller == null && w > 0 && h > 0) {
                        try {
                            val c = BgEffectController(bg)
                            // ⚠ 顺序不能反！`start()` 里才会 new 出 BgEffectPainter，
                            // 而 `setType()` 第一件事就是 mBgEffectPainter.setType(...) ——
                            // 先 setType 会 NPE（之前就是这样被静默吞掉，表现是「光效什么都没有」）。
                            // HyperCeiler 原版也是 start() 在前、setType() 在后。
                            c.start()
                            // actionBar 传 null：Compose 里没有 ActionBar，
                            // 原版此时把标题栏高度按 0 算，正是我们要的效果。
                            c.setType(ctx, bg, null)
                            controller = c
                            android.util.Log.i("HcAboutBg", "flow effect started, size=${w}x$h")
                        } catch (t: Throwable) {
                            // 不吞异常：光效挂了要能在 logcat 里看到原因
                            android.util.Log.e("HcAboutBg", "flow effect failed", t)
                        }
                    }
                }

                override fun onDetachedFromWindow() {
                    try {
                        controller?.stop()
                    } catch (t: Throwable) {
                        android.util.Log.e("HcAboutBg", "stop failed", t)
                    }
                    controller = null
                    super.onDetachedFromWindow()
                }
            }
            host.addView(
                bg,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            host
        },
    )
}
