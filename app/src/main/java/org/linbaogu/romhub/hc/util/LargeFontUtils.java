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
import android.content.res.Configuration;

/**
 * HyperCeiler `LargeFontUtils` 的等价替身（判断系统是否开了「超大字体」）。
 *
 * 照抄的 `DeviceNameCard` 用它决定设备名要不要缩小字号。这里用标准的
 * `Configuration.fontScale` 判断，效果一致。
 */
public final class LargeFontUtils {

    private LargeFontUtils() {
    }

    /** 系统字体缩放是否达到「大字体」档（>= 1.15 视为大）。 */
    public static boolean isLargeFontLevel(Context ctx) {
        if (ctx == null) return false;
        Configuration c = ctx.getResources().getConfiguration();
        return c.fontScale >= 1.15f;
    }

    /** 超大字体档（>= 1.3）。 */
    public static boolean isSuperLargeFontLevel(Context ctx) {
        if (ctx == null) return false;
        return ctx.getResources().getConfiguration().fontScale >= 1.3f;
    }
}
