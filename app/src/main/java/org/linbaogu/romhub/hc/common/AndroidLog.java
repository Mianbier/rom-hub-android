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

import android.util.Log;

/**
 * HyperCeiler `com.sevtinge.hyperceiler.common.log.AndroidLog` 的等价替身。
 *
 * 照抄 HyperCeiler 的 provision 流程时，它到处调 `AndroidLog.d/i/w`；
 * 我们不想把 HyperCeiler 整个 common 模块搬过来，就用这个薄壳转发到系统 Log。
 * 调用面实测只有 d / i / w 三个。
 */
public final class AndroidLog {

    private static final String DEFAULT_TAG = "HcProvision";

    private AndroidLog() {
    }

    private static String tag(String t) {
        return (t == null || t.isEmpty()) ? DEFAULT_TAG : t;
    }

    public static void d(String t, String msg) {
        Log.d(tag(t), String.valueOf(msg));
    }

    public static void d(String t, String msg, Throwable e) {
        Log.d(tag(t), String.valueOf(msg), e);
    }

    public static void i(String t, String msg) {
        Log.i(tag(t), String.valueOf(msg));
    }

    public static void i(String t, String msg, Throwable e) {
        Log.i(tag(t), String.valueOf(msg), e);
    }

    public static void w(String t, String msg) {
        Log.w(tag(t), String.valueOf(msg));
    }

    public static void w(String t, String msg, Throwable e) {
        Log.w(tag(t), String.valueOf(msg), e);
    }

    public static void e(String t, String msg) {
        Log.e(tag(t), String.valueOf(msg));
    }

    public static void e(String t, String msg, Throwable e) {
        Log.e(tag(t), String.valueOf(msg), e);
    }

    public static void v(String t, String msg) {
        Log.v(tag(t), String.valueOf(msg));
    }
}
