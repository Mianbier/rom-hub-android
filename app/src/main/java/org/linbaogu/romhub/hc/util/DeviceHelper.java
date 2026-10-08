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

import android.os.Build;

/**
 * HyperCeiler `libhook.utils.api.DeviceHelper` 的等价替身。
 *
 * 照抄「关于页」的设备信息卡时用到它的 `System` 内部类（读系统版本 / 判断 Android 版本）。
 * 原版是 hook 框架的一部分（会读一堆 MIUI 私有属性），ROM Hub 用不到那套，
 * 这里直接用标准 Android API 还原同样的信息。
 */
public final class DeviceHelper {

    private DeviceHelper() {
    }

    /** 设备 / 系统相关的取值。 */
    public static final class System {

        private System() {
        }

        /** 系统增量版本号（HyperCeiler 用来显示 OS 版本；这里退回 Android 版本）。 */
        public static String getSystemVersionIncremental() {
            return Build.VERSION.RELEASE;
        }

        /** Android 版本是否 >= 指定值。 */
        public static boolean isMoreAndroidVersion(int version) {
            return Build.VERSION.SDK_INT >= version;
        }
    }
}
