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

package org.linbaogu.romhub.hc.provision.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Locale;

import fan.core.utils.HyperMaterialUtils;
import fan.internal.utils.LiteUtils;
import fan.os.Build;
import fan.provision.OobeUtils;

public class Utils {

    private static final String TAG = "Provision_Utils";

    public static boolean isFirstBoot = true;

    public static boolean IS_START_ANIMA = false;

    public static final boolean IS_SUPPORT_ANIM = !OobeUtils.isLiteOrLowDevice();

    public static int IS_RTL = 0;
    public static int LOCATION_X = -1;
    public static int LOCATION_Y = -1;
    public static Bitmap CACHE_BITMAP = null;

    private static Boolean mBlurEffectEnabledCache = null;


    public static boolean isRTL() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == 1;
    }

    public static boolean isFoldDevice() {
        return Build.IS_FOLDABLE;
    }

    public static Bitmap getCacheBitmap(boolean rotation) {
        if (CACHE_BITMAP != null) {
            if (rotation) {
                CACHE_BITMAP = ViewUtils.rotateBitmap180(CACHE_BITMAP);
            }
            return CACHE_BITMAP;
        } else {
            return null;
        }
    }

    public static String getTopActivityClassName(Context context) throws SecurityException {
        ActivityManager manager = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE));
        List<ActivityManager.RunningTaskInfo> runningTasks = manager.getRunningTasks(1);
        if (runningTasks == null || runningTasks.isEmpty()) {
            return null;
        }
        ActivityManager.RunningTaskInfo taskInfo = runningTasks.get(0);
        String topActivityClassName = taskInfo.topActivity.getClassName();
        Log.d(TAG, "getTopActivityClassName: " + topActivityClassName);
        return topActivityClassName;
    }

    public static boolean isBlurEffectEnabled(Context context) {
        if (mBlurEffectEnabledCache != null) {
            return mBlurEffectEnabledCache;
        }
        if (context == null) return false;
        mBlurEffectEnabledCache = HyperMaterialUtils.isEnable() && !LiteUtils.isCommonLiteStrategy() && HyperMaterialUtils.isFeatureEnable(context);
        Log.d(TAG, "isBlurEffectEnabled: " + mBlurEffectEnabledCache);
        return mBlurEffectEnabledCache;
    }
}
