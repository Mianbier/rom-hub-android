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

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.linbaogu.romhub.R
import org.linbaogu.romhub.hc.about.widget.DeviceInfoCard
import org.linbaogu.romhub.hc.about.widget.VersionCard

/**
 * 关于页顶部的两张卡片，用 [AndroidView] 嵌进 Compose。
 *
 *  · [VersionCard]    —— 会呼吸的版本卡片（App 图标 + 名称 + 版本号 + 更新提示）
 *  · [DeviceInfoCard] —— 设备信息卡（设备名 / Android 版本 / OS 版本）
 *
 * 两张都是从 HyperCeiler 原样搬过来的（只改了包名 / R 引用 / 图标与字标资源），
 * 所以几何、动效、配色与它完全一致。
 *
 * ⚠️ 注意区分：这两张**都是 HyperCeiler 原版**，要保留。
 * 之前我自己另写过一个 Compose 版「设备信息」大卡片（显示 设备型号 / Android 版本 /
 * OS 版本 / 处理器 / 屏幕分辨率），那个已经按用户要求**整个删掉**了
 * （删的是 AboutScreen 里那个自制的，不是这里这张）。
 *
 * ⚠️ 布局里用了 Miuix 的主题属性（`?attr/preferenceSecondaryTextColor` 等，
 * 经 `@style/TextAppearance.PreferenceList.Secondary` 引用）。这些属性**只在
 * Miuix 的 `ThemeOverlay.Preference.*` 里定义**（Compose 那套 `Theme.RomHub`
 * 继承自 `android:Theme.Material`，完全没有）→ inflate 时会抛
 * `UnsupportedOperationException: Failed to resolve attribute at index 3` → 点「关于」直接闪退。
 *
 * 所以这里套**两层** [ContextThemeWrapper]：
 *   ① `ProvisionTheme` —— Miuix 的 `Theme.AppCompat.DayNight`，提供基础 Miuix 属性；
 *   ② `HcAboutOverlay`（= `ThemeOverlay.Preference.DayNight`）—— 补上 preference 系列属性。
 */
@Composable
fun HcAboutCards(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx -> VersionCard(themedContext(ctx)) },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
        AndroidView(
            factory = { ctx -> DeviceInfoCard(themedContext(ctx)) },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }
}

private fun themedContext(ctx: android.content.Context): android.content.Context {
    val base = ContextThemeWrapper(ctx, R.style.ProvisionTheme)
    return ContextThemeWrapper(base, R.style.HcAboutOverlay)
}
