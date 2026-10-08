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

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * HyperCeiler `PrefsBridge` 的等价替身。
 *
 * provision 流程里只用到 `putByApp(key, value)`（把设置写进 App 侧 prefs）。
 * 我们直接落到本 App 自己的 SharedPreferences，不搬 HyperCeiler 那套
 * 跨进程 prefs 桥（它又拖 prefs 包，链条太长）。
 */
public final class PrefsBridge {

    private static final String SP_NAME = "hc_provision";

    private static Context sAppContext;

    private PrefsBridge() {
    }

    /** App 启动时调一次，供无 Context 的 putByApp 使用。 */
    public static void initForApp(Context ctx) {
        if (ctx != null) sAppContext = ctx.getApplicationContext();
    }

    private static SharedPreferences sp() {
        if (sAppContext == null) throw new IllegalStateException("PrefsBridge.initForApp() 未调用");
        return sAppContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /** 原版签名：无 Context（走静态 app context）。 */
    public static void putByApp(String key, Object value) {
        SharedPreferences.Editor e = sp().edit();
        if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) e.putInt(key, (Integer) value);
        else if (value instanceof Long) e.putLong(key, (Long) value);
        else if (value instanceof Float) e.putFloat(key, (Float) value);
        else e.putString(key, value == null ? null : String.valueOf(value));
        e.apply();
    }

    public static SharedPreferences getSharedPreferences(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /** 写一个设置项（按值类型分派）。 */
    public static void putByApp(Context ctx, String key, Object value) {
        SharedPreferences.Editor e = getSharedPreferences(ctx).edit();
        if (value instanceof Boolean) {
            e.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            e.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            e.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            e.putFloat(key, (Float) value);
        } else {
            e.putString(key, value == null ? null : String.valueOf(value));
        }
        e.apply();
    }

    /** 兼容：有些调用点用 (key, value) 两参重载。 */
    public static void putByApp(Context ctx, Map<String, ?> values) {
        SharedPreferences.Editor e = getSharedPreferences(ctx).edit();
        for (Map.Entry<String, ?> en : values.entrySet()) {
            Object v = en.getValue();
            if (v instanceof Boolean) {
                e.putBoolean(en.getKey(), (Boolean) v);
            } else if (v instanceof Integer) {
                e.putInt(en.getKey(), (Integer) v);
            } else if (v instanceof Long) {
                e.putLong(en.getKey(), (Long) v);
            } else if (v instanceof Float) {
                e.putFloat(en.getKey(), (Float) v);
            } else {
                e.putString(en.getKey(), v == null ? null : String.valueOf(v));
            }
        }
        e.apply();
    }

    public static boolean isHookProcess() {
        return false;
    }
}
