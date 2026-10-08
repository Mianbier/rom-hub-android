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

package org.linbaogu.romhub.hc.about.controller;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.linbaogu.romhub.R;

import fan.appcompat.app.ActionBar;
import fan.os.Build;

public class BgEffectController implements Runnable {

    private float[] bound;
    private float mDeltaTime;
    private long mLastGlobalTime;
    private final View mTarget;
    private float mTime;
    private float mTimeDirection = 1.0f;

    /** 只在第一帧打一条日志，方便确认光效到底有没有跑起来。 */
    private boolean mLoggedOnce = false;

    BgEffectPainter mBgEffectPainter;

    public BgEffectController(View target) {
        mTarget = target;
    }

    public void start() {
        if (mBgEffectPainter == null) {
            mBgEffectPainter = new BgEffectPainter(mTarget.getContext());
            mLastGlobalTime = System.nanoTime();
            resetTime();
            mTarget.postOnAnimation(this);
        }
    }

    @Override
    public void run() {
        if (mBgEffectPainter != null) {
            tickPingPong();
            if (mTarget.getWidth() > 0 && mTarget.getHeight() > 0) {
                mBgEffectPainter.setResolution(mTarget.getWidth(), mTarget.getHeight());
                mBgEffectPainter.updateMaterials(mDeltaTime * mTimeDirection);
                mTarget.setRenderEffect(mBgEffectPainter.getRenderEffect());
                mTarget.invalidate();
                if (!mLoggedOnce) {
                    mLoggedOnce = true;
                    android.util.Log.i("BgEffectController", "first frame applied, size="
                        + mTarget.getWidth() + "x" + mTarget.getHeight() + " bound=" + java.util.Arrays.toString(bound));
                }
            }
            mTarget.postOnAnimation(this);
        }
    }

    private void tickPingPong() {
        long nanoTime = System.nanoTime();
        mDeltaTime = (float) ((nanoTime - mLastGlobalTime) * 1.0E-9d);
        mTime = mTime + (mDeltaTime * mTimeDirection);
        if (mTimeDirection > 0.0f) {
            if (mTime >= 120.0f) {
                mTimeDirection = -1.0f;
            }
        } else if (mTime <= 0.0f) {
            mTimeDirection = 1.0f;
        }
        mLastGlobalTime = nanoTime;
    }

    public void resetTime() {
        mLastGlobalTime = System.nanoTime();
        mTime = 0.0f;
        mTimeDirection = 1.0f;
    }

    public void stop() {
        if (mBgEffectPainter != null) {
            mTarget.removeCallbacks(this);
            mBgEffectPainter.stop();
            mBgEffectPainter = null;
            mTarget.setRenderEffect(null);
            mTarget.invalidate();
        }
    }

    public static boolean isDarkModeEnable(Context context) {
        return (context.getResources().getConfiguration().uiMode & 48) == 32;
    }

    public void setType(Context context, View view, ActionBar actionBar) {
        resetTime();
        calcAnimationBound(context, view, actionBar);
        if (isDarkModeEnable(context)) {
            if (Build.IS_TABLET) {
                mBgEffectPainter.setType(BgEffectDataManager.DeviceType.TABLET, BgEffectDataManager.ThemeMode.DARK, this.bound);
                return;
            } else {
                mBgEffectPainter.setType(BgEffectDataManager.DeviceType.PHONE, BgEffectDataManager.ThemeMode.DARK, this.bound);
                return;
            }
        }
        if (Build.IS_TABLET) {
            mBgEffectPainter.setType(BgEffectDataManager.DeviceType.TABLET, BgEffectDataManager.ThemeMode.LIGHT, this.bound);
        } else {
            mBgEffectPainter.setType(BgEffectDataManager.DeviceType.PHONE, BgEffectDataManager.ThemeMode.LIGHT, this.bound);
        }
    }

    private void calcAnimationBound(Context context, View view, ActionBar actionBar) {
        float height = (actionBar != null ? actionBar.getHeight() + 0.0f : 0.0f) + context.getResources().getDimensionPixelSize(R.dimen.app_logo_area_height);
        float height2 = height / ((ViewGroup) view.getParent()).getHeight();
        float width = ((ViewGroup) view.getParent()).getWidth();
        if (width <= height) {
            this.bound = new float[]{0.0f, 1.0f - height2, 1.0f, height2};
        } else {
            this.bound = new float[]{((width - height) / 2.0f) / width, 1.0f - height2, height / width, height2};
        }
    }
}
