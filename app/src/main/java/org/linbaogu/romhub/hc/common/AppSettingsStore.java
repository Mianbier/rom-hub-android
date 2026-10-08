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

/**
 * HyperCeiler `AppSettingsStore` 的等价替身（图标模式 / 悬浮导航 / 作用域同步 等设置）。
 * ROM Hub 里这些都不需要真正生效，但要保证 provision 流程读写不炸 —— 落到本地 prefs。
 */
public final class AppSettingsStore {

    private static final String SP_NAME = "hc_app_settings";

    private AppSettingsStore() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // ---- 桌面图标（true = 在桌面显示图标；默认开，否则图标会从桌面消失）----
    public static boolean isHideAppIconEnabled(Context ctx) {
        return ctx == null || sp(ctx).getBoolean("hide_app_icon", true);
    }

    public static void setHideAppIconEnabled(Context ctx, boolean enabled) {
        if (ctx != null) sp(ctx).edit().putBoolean("hide_app_icon", enabled).apply();
    }

    // ---- 悬浮导航（我们的悬浮底栏开关也存这里，方便统一）----
    public static boolean isFloatNavEnabled(Context ctx) {
        return ctx == null || sp(ctx).getBoolean("float_nav", true);
    }

    public static void setFloatNavEnabled(Context ctx, boolean enabled) {
        if (ctx != null) sp(ctx).edit().putBoolean("float_nav", enabled).apply();
    }

    // ---- 作用域同步 ----
    public static boolean isScopeSyncEnabled(Context ctx) {
        return ctx != null && sp(ctx).getBoolean("scope_sync", true);
    }

    public static void setScopeSyncEnabled(Context ctx, boolean enabled) {
        if (ctx != null) sp(ctx).edit().putBoolean("scope_sync", enabled).apply();
    }

    // ---- 图标样式 ----
    public static int getIconIndex(Context ctx) {
        return ctx == null ? 0 : sp(ctx).getInt("icon_index", 0);
    }

    public static void setIconIndex(Context ctx, int index) {
        if (ctx != null) sp(ctx).edit().putInt("icon_index", index).apply();
    }

    public static int getIconModeIndex(Context ctx) {
        return ctx == null ? 0 : sp(ctx).getInt("icon_mode_index", 0);
    }

    public static void setIconModeIndex(Context ctx, int index) {
        if (ctx != null) sp(ctx).edit().putInt("icon_mode_index", index).apply();
    }

    // ---- 应用语言（ROM Hub 只有跟随系统）----
    public static int getAppLanguageIndex(Context ctx) {
        return getAppLanguageIndex(ctx, 0);
    }

    public static int getAppLanguageIndex(Context ctx, int defValue) {
        return ctx == null ? defValue : sp(ctx).getInt("app_language_index", defValue);
    }

    public static void setAppLanguageIndex(Context ctx, int index) {
        if (ctx != null) sp(ctx).edit().putInt("app_language_index", index).apply();
    }
}
