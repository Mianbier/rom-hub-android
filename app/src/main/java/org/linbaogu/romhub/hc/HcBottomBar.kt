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

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.linbaogu.romhub.R
import org.linbaogu.romhub.hc.widget.NavigationStyle
import org.linbaogu.romhub.hc.widget.SwitchView

/**
 * HyperCeiler 的悬浮底栏（View 版），用 [AndroidView] 嵌进 Compose。
 *
 * 里面的 [SwitchView] 是从 HyperCeiler **原样拷过来**的（只改了包名和 R 引用），
 * 几何、材质、形变动画、Edge-to-Edge 全是原版行为。样式两档见 [NavigationStyle]：
 *  · [NavigationStyle.CAPSULE_ICON] —— 悬浮胶囊（默认）
 *  · [NavigationStyle.BOTTOM_LABEL] —— 传统贴地底部标签
 *
 * ⚠️ [SwitchView] 内部把 `layoutParams` 强转成 `FrameLayout.LayoutParams`，
 * 所以**必须**把它放进一个 FrameLayout 里。
 */
@Composable
fun HcBottomBar(
    selectedIndex: Int,
    style: NavigationStyle,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                val sv = SwitchView(ctx)
                // 先挂进 FrameLayout，再 updateStyle —— SwitchView 内部要用 layoutParams
                addView(
                    sv,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                sv.inflateMenu(R.menu.hc_bottom_nav)
                sv.updateStyle(style)
                sv.setOnSwitchChangeListener { position, _ -> onSelected(position) }
                tag = sv
            }
        },
        update = { root ->
            val sv = root.tag as? SwitchView ?: return@AndroidView
            sv.updateStyle(style)
            if (sv.selectedPosition != selectedIndex) {
                sv.setSelectedTab(selectedIndex, false)
            }
        },
    )
}

/**
 * 关于页头部 —— 照抄 HyperCeiler 的 `activity_about.xml`（布局原样，见
 * `res/layout/hc_about_header.xml`），只把文字内容换成这个 App 的。
 */
@Composable
fun HcAboutHeader(
    appName: String,
    version: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.hc_about_header, null).also { bind(it, appName, version, summary) }
        },
        update = { bind(it, appName, version, summary) },
    )
}

private fun bind(v: android.view.View, appName: String, version: String, summary: String) {
    v.findViewById<TextView>(R.id.hc_about_name)?.text = appName
    v.findViewById<TextView>(android.R.id.summary)?.text = summary
    v.findViewById<TextView>(android.R.id.title)?.text = version
}
