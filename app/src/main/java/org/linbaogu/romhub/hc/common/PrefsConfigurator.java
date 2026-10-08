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

package org.linbaogu.romhub.hc.common;

import androidx.preference.PreferenceFragmentCompat;

/**
 * HyperCeiler `PrefsConfigurator` 的等价替身。
 *
 * 原版干的事：给 PreferenceFragmentCompat 统一套上 Miuix 的样式/分组/重置逻辑。
 * 我们用 fan.miuix:preference 时大多已经自带样式，所以这里退化成最小实现：
 * 只保留接口，保证 provision 里的调用不炸。
 */
public final class PrefsConfigurator {

    private PrefsConfigurator() {
    }

    public static void setup(PreferenceFragmentCompat fragment) {
        // 样式由 fan.miuix:preference 自带，无需额外配置
    }

    public static void reset(PreferenceFragmentCompat fragment, int xmlResId) {
        if (fragment == null) return;
        try {
            fragment.setPreferencesFromResource(xmlResId, null);
        } catch (Throwable ignored) {
            // 资源不存在等情况下静默，不影响流程
        }
    }

    public static void performReset(PreferenceFragmentCompat fragment, int currentXmlId) {
        reset(fragment, currentXmlId);
    }
}
