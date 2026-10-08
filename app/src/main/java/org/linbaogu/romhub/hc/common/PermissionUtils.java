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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * 权限工具 —— **按用户选择走的 B 方案**：结构照抄 HyperCeiler 的 `PermissionUtils`，
 * 但把「读取已安装应用列表」换成 ROM Hub 真正需要的权限。
 *
 * ROM Hub 需要的是：**通知权限**（推送新 ROM / 新包提醒）。
 * 原来的 `PERMISSION_GET_INSTALLED_APPS` 常量保留同名（provision 流程里引用它），
 * 值改成通知权限，这样界面结构不用动、内容变成我们自己的。
 */
public final class PermissionUtils {

    /** 原版是 "android.permission.QUERY_ALL_PACKAGES"，这里换成 ROM Hub 真正要的。 */
    public static final String PERMISSION_GET_INSTALLED_APPS = Manifest.permission.POST_NOTIFICATIONS;

    private PermissionUtils() {
    }

    public static boolean hasPermission(Context ctx, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** provision 的「权限」那一步用它判断是否已授权。 */
    public static boolean hasInstalledAppsPermission(Context ctx) {
        return hasPermission(ctx, PERMISSION_GET_INSTALLED_APPS);
    }

    public static boolean canReadInstalledApps(Context ctx) {
        return hasInstalledAppsPermission(ctx);
    }

    public static boolean isPermissionGranted(String permission, String[] permissions, int[] grantResults) {
        if (permissions == null || grantResults == null) return false;
        for (int i = 0; i < permissions.length; i++) {
            if (permission.equals(permissions[i])) {
                return i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        return false;
    }

    public static boolean isInstalledAppsPermissionGranted(String[] permissions, int[] grantResults) {
        return isPermissionGranted(PERMISSION_GET_INSTALLED_APPS, permissions, grantResults);
    }
}
