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

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * HyperCeiler `AppLanguageHelper` 的等价替身。
 *
 * ROM Hub 只出中文，不做应用内语言切换，所以这里全部退化成「跟随系统」，
 * 只保证 provision 流程调用不炸。调用面实测 6 个方法。
 */
public final class AppLanguageHelper {

    private AppLanguageHelper() {
    }

    /** provision 里用到的语言条目（只留「跟随系统」一项）。 */
    public static String[] getLanguageEntries(Context ctx) {
        return new String[]{"跟随系统"};
    }

    public static String[] getLanguageEntryValues() {
        return new String[]{""};
    }

    public static int getCurrentLanguageIndex(Context ctx) {
        return 0;
    }

    /** 切换语言：ROM Hub 不切，直接返回。 */
    public static void setIndexLanguage(Activity activity, int index, boolean recreate) {
        // no-op
    }

    public static void freezeCurrentLocaleIfUnset(Context ctx) {
        // no-op
    }

    /** 包装 Context（原版用来注入 Locale）——这里原样返回。 */
    public static Context wrapContext(Context ctx) {
        return ctx;
    }

    public static Locale getCurrentLocale(Context ctx) {
        Configuration c = ctx.getResources().getConfiguration();
        return c.getLocales().isEmpty() ? Locale.getDefault() : c.getLocales().get(0);
    }

    public static String getLanguage(Context ctx) {
        return getCurrentLocale(ctx).getLanguage();
    }

    /** 供外部解包（原版 wrapContext 会包一层，这里没有）。 */
    public static Context unwrap(Context ctx) {
        while (ctx instanceof ContextWrapper && !(ctx instanceof Activity)) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return ctx;
    }
}
