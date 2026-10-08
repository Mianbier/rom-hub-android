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

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import org.linbaogu.romhub.hc.common.AndroidLog;


public class ViewUtils {
    private static final String TAG = "ViewUtils";

    public interface RoundedBitmapCallback {
        void onBitmapReady(Bitmap bitmap);
    }

    public static void captureRoundedBitmap(Activity activity, View view, Handler handler, final RoundedBitmapCallback roundedBitmapCallback) {
        if (activity == null || view == null || handler == null || roundedBitmapCallback == null) {
            if (roundedBitmapCallback != null) {
                roundedBitmapCallback.onBitmapReady(null);
            }
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            roundedBitmapCallback.onBitmapReady(null);
            return;
        }
        try {
            int[] iArr = new int[2];
            view.getLocationInWindow(iArr);
            int width = view.getWidth();
            int height = view.getHeight();
            if (width != 0 && height != 0) {
                final Bitmap bitmapCreateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Window window = activity.getWindow();
                if (window == null) {
                    roundedBitmapCallback.onBitmapReady(null);
                    return;
                }
                int i = iArr[0];
                int i2 = iArr[1];
                PixelCopy.request(window, new Rect(i, i2, width + i, height + i2), bitmapCreateBitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                    @Override
                    public void onPixelCopyFinished(int i3) {
                        ViewUtils.$r8$lambda$NnYv_JfPqrX4fW6_6EFBfXItcPU(bitmapCreateBitmap, roundedBitmapCallback, i3);
                    }
                }, handler);
                return;
            }
            AndroidLog.d(TAG, "width  " + width + " height " + height);
            roundedBitmapCallback.onBitmapReady(null);
        } catch (IllegalArgumentException | IllegalStateException unused) {
            roundedBitmapCallback.onBitmapReady(null);
        }
    }

    public static void $r8$lambda$NnYv_JfPqrX4fW6_6EFBfXItcPU(Bitmap bitmap, RoundedBitmapCallback roundedBitmapCallback, int i) {
        AndroidLog.d(TAG, "PixelCopy request " + i);
        if (i == 0) {
            roundedBitmapCallback.onBitmapReady(cropBitmapToCircle(bitmap));
        } else {
            roundedBitmapCallback.onBitmapReady(null);
        }
    }

    private static Bitmap cropBitmapToCircle(Bitmap bitmap) {
        int iMin = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMin, iMin, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        Rect rect = new Rect(0, 0, iMin, iMin);
        canvas.drawARGB(0, 0, 0, 0);
        float f = iMin / 2.0f;
        canvas.drawCircle(f, f, f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, null, rect, paint);
        return bitmapCreateBitmap;
    }

    public static Bitmap rotateBitmap180(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.setScale(-1.0f, 1.0f);
        matrix.postTranslate(bitmap.getWidth(), 0.0f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
