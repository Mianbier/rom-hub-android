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

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.linbaogu.romhub.core.Prefs

/**
 * 底部导航栏开关的**可观察状态**（Compose 与设置页共用一份）。
 *
 * ## 开关逻辑（2026-10-07 理顺）
 * | 悬浮底栏 | 胶囊玻璃 | 结果 |
 * |---|---|---|
 * | 开 | 开 | **液体玻璃悬浮胶囊**：能折射底下的内容，带内阴影与滑动指示器（默认） |
 * | 开 | 关 | 纯色悬浮胶囊：同样的形状与动效，不做折射 |
 * | 关 | —  | **贴地标签栏**：不悬浮、贴住屏幕底部，并留出系统手势条高度，不会遮挡内容 |
 *
 * 以前是两个开关语义重叠（「悬浮底栏」和「悬浮胶囊样式」都能切胶囊/标签），
 * 而且关掉悬浮会回落到另一套 Compose 底栏（白条遮挡）。现在只保留「悬浮 or 贴地」
 * 这一个主开关 + 「要不要玻璃」这一个外观开关。
 */
object HcNavState {

    /** 悬浮底栏：开 = 悬浮胶囊；关 = 贴地标签栏。 */
    var enabled by mutableStateOf(true)
        private set

    /**
     * 悬浮胶囊要不要玻璃。关掉就是纯色胶囊。
     * （字段名沿用 [style] 的历史值，避免老配置读不出来。）
     */
    var glass by mutableStateOf(true)
        private set

    /** 兼容旧代码：是否胶囊。现在「悬浮」就等于胶囊。 */
    val capsule: Boolean get() = enabled

    /** 从 Prefs 读一次（App 启动时调用）。 */
    fun load(ctx: Context) {
        enabled = Prefs.hcNavEnabled(ctx)
        glass = Prefs.hcNavGlass(ctx)
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        enabled = on
        Prefs.setHcNavEnabled(ctx, on)
    }

    fun setGlass(ctx: Context, on: Boolean) {
        glass = on
        Prefs.setHcNavGlass(ctx, on)
    }
}
