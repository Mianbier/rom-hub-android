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
import android.os.Bundle;

/**
 * HyperCeiler `EggHelper` 的等价替身（彩蛋：点版本号触发的那点小玩意）。
 *
 * 照抄的 `VersionCard` 会在长按版本号时调 `EggHelper.INSTANCE.focusBuild(msg, ctx)`
 * 给通知加 extras。ROM Hub 不搞彩蛋，这里返回一个空 Bundle，保证调用不炸、
 * 界面与 HyperCeiler 完全一致。
 */
public final class EggHelper {

    /** 单例（原版是 Kotlin object，调用点是 `EggHelper.INSTANCE.xxx`）。 */
    public static final EggHelper INSTANCE = new EggHelper();

    private EggHelper() {
    }

    /** 长按版本号时的 extras —— 空实现。 */
    public Bundle focusBuild(String msg, Context ctx) {
        return new Bundle();
    }
}
