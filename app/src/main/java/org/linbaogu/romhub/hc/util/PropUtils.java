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

import java.lang.reflect.Method;

/**
 * HyperCeiler `libhook.utils.api.PropUtils` 的等价替身（读系统属性）。
 *
 * 原版走 hook 框架读 MIUI 私有属性；这里用标准的 `SystemProperties` 反射实现，
 * 读不到就返回默认值，保证「关于页」的设备信息卡能正常渲染、不会崩。
 */
public final class PropUtils {

    private static Method sGet;

    private PropUtils() {
    }

    /** 读系统属性，读不到返回空串。 */
    public static String getProp(String key) {
        return getProp(key, "");
    }

    /** 读系统属性，带默认值。 */
    public static String getProp(String key, String def) {
        try {
            if (sGet == null) {
                Class<?> c = Class.forName("android.os.SystemProperties");
                sGet = c.getMethod("get", String.class, String.class);
            }
            Object v = sGet.invoke(null, key, def);
            return v == null ? def : v.toString();
        } catch (Throwable e) {
            return def;
        }
    }

    /** 以 root 读系统属性 —— ROM Hub 无 root，直接退回普通读取。 */
    public static String getPropSu(String key) {
        return getProp(key, "");
    }

    public static String getPropSu(String key, String def) {
        return getProp(key, def);
    }
}
