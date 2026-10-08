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

package org.linbaogu.romhub.hc.provision.renderengine;

import android.view.View;

public class GlowController implements Runnable {

    GlowPainter mGlowPainter;

    private float mTime;
    private float mDeltaTime;
    private float mTimeDirection = 1.0f;

    private long mLastGlobalTime;

    private final View mTarget;

    public GlowController(View target) {
        mTarget = target;
    }

    public void start(boolean z) {
        if (mGlowPainter == null) {
            mGlowPainter = new GlowPainter(mTarget.getContext());
            mGlowPainter.needAdmission(z);
            mLastGlobalTime = System.nanoTime();
            resetTime();
            mTarget.post(this);
        }
    }

    @Override
    public void run() {
        if (mGlowPainter != null) {
            tickPingPong();
            mGlowPainter.setAnimTime(mTime);
            mGlowPainter.setResolution(mTarget.getWidth(), mTarget.getHeight());
            mTarget.setRenderEffect(mGlowPainter.getRenderEffect());
            mTarget.postDelayed(this, 16L);
        }
    }

    private void tick() {
        long jNanoTime = System.nanoTime();
        float f = (float) ((jNanoTime - mLastGlobalTime) * 1.0E-9d);
        mDeltaTime = f;
        mTime += f;
        mLastGlobalTime = jNanoTime;
    }

    private void tickPingPong() {
        long jNanoTime = System.nanoTime();
        mDeltaTime = (float) ((jNanoTime - mLastGlobalTime) * 1.0E-9d);
        mTime = mTime + (mDeltaTime * mTimeDirection);
        if (mTimeDirection > 0.0f) {
            if (mTime >= 120.0f) {
                mTimeDirection = -1.0f;
            }
        } else if (mTime <= 2.0f) {
            mTimeDirection = 1.0f;
        }
        mLastGlobalTime = jNanoTime;
    }

    public void resetTime() {
        mLastGlobalTime = System.nanoTime();
        mTime = 0.0f;
    }

    public void setCircleYOffset(float f) {
        if (mGlowPainter != null) {
            mGlowPainter.setCircleYOffset(f);
        }
    }

    public void setCircleYOffsetWithView(View view, View view2) {
        if (mGlowPainter == null || view == null || view2 == null) {
            return;
        }
        int[] iArr = new int[2];
        view.getLocationOnScreen(iArr);
        int height = iArr[1] + (view.getHeight() / 2);
        float height2 = view2.getHeight();
        setCircleYOffset(((height2 / 2.0f) - height) / height2);
    }

    public void stop() {
        if (mGlowPainter != null) {
            mTarget.removeCallbacks(this);
            mGlowPainter = null;
            mTarget.setRenderEffect(null);
        }
    }

}
