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

package org.linbaogu.romhub.hc.util;

import android.content.Context;

/**
 * HyperCeiler `SettingsFeatures` 的等价替身。
 *
 * 原版判断「是否是平板/折叠屏分栏」这类系统特性（走 MIUI 私有 API）。
 * 照抄关于页的 `VersionCard` 时用到它来微调动画位移量 —— 这里恒返回 false，
 * 走普通手机的位移量，视觉上与 HyperCeiler 在手机上完全一致。
 */
public final class SettingsFeatures {

    private SettingsFeatures() {
    }

    /** 是否是分栏（平板/折叠屏展开）设备。 */
    public static boolean isSplitTabletDevice() {
        return false;
    }

    /** 是否开启某个特性开关 —— ROM Hub 无对应设置，恒 false。 */
    public static boolean isEnabled(Context ctx, String key) {
        return false;
    }
}
