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
import android.content.res.Resources;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RawRes;

import org.linbaogu.romhub.R;
import org.linbaogu.romhub.hc.common.AndroidLog;

import java.io.InputStream;
import java.util.Scanner;

/**
 * 「关于」页流动光效的绘制器 —— 照抄 HyperCeiler
 * `about/controller/BgEffectPainter.java`，只做了一处替身：
 *
 * HyperCeiler 原版用 `fan.animation.Folme`（米 UI 的物理动画引擎）去补间
 * `colorInterpT`（颜色过渡进度）和 `gradientSpeed`（流动速度）。我们这套
 * `fan.miuix:*` 里**没有** Folme（folme/animation 两个 AAR 里没有任何 class），
 * 所以这里用一段等价的时间补间替代 —— 观感一样：颜色周期切换时平滑过渡，
 * 切完速度回落。其余（AGSL 着色器、uniform 参数、颜色表、ping-pong 时间轴）
 * 全部和原版一致。
 */
public class BgEffectPainter {

    BgEffectDataManager.BgEffectData mBgEffectData;
    BgEffectDataManager mBgEffectDataManager;
    RuntimeShader mBgRuntimeShader;
    Handler mHandler;

    /** 颜色过渡补间时长（替代 Folme 的 spring）。 */
    private static final long COLOR_ANIM_MS = 700L;
    /** 流动速度回落延迟（原版是 300L）。 */
    private static final long SPEED_REST_DELAY_MS = 300L;

    private float cycleCount = 0.0f;
    private float[] endColorValue;
    private float[] startColorValue;
    private float uAnimTime = 0.0f;
    private float[] uBgBound = {0.0f, 0.4489f, 1.0f, 0.5511f};
    private final float[] uColors = {0.57f, 0.76f, 0.98f, 1.0f, 0.98f, 0.85f, 0.68f, 1.0f, 0.98f, 0.75f, 0.93f, 1.0f, 0.73f, 0.7f, 0.98f, 1.0f};
    private float prevT = 0.0f;
    private float colorInterpT = 0.0f;
    private float gradientSpeed = 1.0f;

    // ---- 替代 Folme 的补间状态 ----
    private boolean mColorAnimRunning = false;
    private long mColorAnimStart = 0L;

    public BgEffectPainter(Context context) {
        String loadShader = loadShader(context.getResources(), R.raw.bg_frag);
        mBgRuntimeShader = new RuntimeShader(loadShader);
        android.util.Log.i("BgEffectPainter", "RuntimeShader created, shaderLen=" + (loadShader == null ? -1 : loadShader.length()));
        mHandler = new Handler(Looper.getMainLooper());
        mBgEffectDataManager = new BgEffectDataManager();
        mBgEffectData = mBgEffectDataManager.getData(BgEffectDataManager.DeviceType.PHONE, BgEffectDataManager.ThemeMode.LIGHT);
        cycleCount = 0.0f;
        mBgRuntimeShader.setFloatUniform("uTranslateY", mBgEffectData.uTranslateY);
        mBgRuntimeShader.setFloatUniform("uPoints", mBgEffectData.uPoints);
        mBgRuntimeShader.setFloatUniform("uColors", uColors);
        mBgRuntimeShader.setFloatUniform("uNoiseScale", mBgEffectData.uNoiseScale);
        mBgRuntimeShader.setFloatUniform("uPointOffset", mBgEffectData.uPointOffset);
        mBgRuntimeShader.setFloatUniform("uPointRadiusMulti", mBgEffectData.uPointRadiusMulti);
        mBgRuntimeShader.setFloatUniform("uSaturateOffset", mBgEffectData.uSaturateOffset);
        mBgRuntimeShader.setFloatUniform("uShadowColorMulti", mBgEffectData.uShadowColorMulti);
        mBgRuntimeShader.setFloatUniform("uShadowColorOffset", mBgEffectData.uShadowColorOffset);
        mBgRuntimeShader.setFloatUniform("uShadowOffset", mBgEffectData.uShadowOffset);
        mBgRuntimeShader.setFloatUniform("uBound", uBgBound);
        mBgRuntimeShader.setFloatUniform("uAlphaMulti", mBgEffectData.uAlphaMulti);
        mBgRuntimeShader.setFloatUniform("uLightOffset", mBgEffectData.uLightOffset);
        mBgRuntimeShader.setFloatUniform("uAlphaOffset", mBgEffectData.uAlphaOffset);
        mBgRuntimeShader.setFloatUniform("uShadowNoiseScale", mBgEffectData.uShadowNoiseScale);
        startColorValue = mBgEffectData.gradientColors2;
        endColorValue = mBgEffectData.gradientColors2;
    }

    public RenderEffect getRenderEffect() {
        return RenderEffect.createShaderEffect(mBgRuntimeShader);
    }

    public void stop() {
        mColorAnimRunning = false;
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    public void updateMaterials(float f) {
        uAnimTime += f * gradientSpeed;
        tickColorAnim();
        computeGradientColor();
        mBgRuntimeShader.setFloatUniform("uAnimTime", uAnimTime);
        mBgRuntimeShader.setFloatUniform("uColors", uColors);
    }

    public void setResolution(float f, float f2) {
        mBgRuntimeShader.setFloatUniform("uResolution", f, f2);
    }

    private String loadShader(Resources resources, @RawRes int id) {
        try {
            InputStream stream = resources.openRawResource(id);
            Scanner scanner = new Scanner(stream);
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine());
                sb.append("\n");
            }
            String s = sb.toString();
            scanner.close();
            if (stream != null) {
                stream.close();
            }
            return s;
        } catch (Exception e) {
            AndroidLog.e("BgEffectPainter", "loadShader failed", e);
            return null;
        }
    }

    /** 等价替代 Folme 的 `stateStyle.to("colorInterpT", 1f, ...)`。 */
    private void tickColorAnim() {
        if (!mColorAnimRunning) return;
        long elapsed = System.nanoTime() - mColorAnimStart;
        float t = Math.min(1f, elapsed / (COLOR_ANIM_MS * 1_000_000f));
        // ease-out，接近原版 spring(0.9, 1.3) 的手感
        float eased = 1f - (1f - t) * (1f - t);
        colorInterpT = eased;
        if (t >= 1f) {
            colorInterpT = 1f;
            mColorAnimRunning = false;
        }
    }

    private void computeGradientColor() {
        double d = uAnimTime / mBgEffectData.colorInterpPeriod;
        float floor = (float) Math.floor((d - Math.floor(d)) * 2.0d);
        if (Math.abs(prevT - floor) > 0.5d) {
            if (cycleCount % 4.0f == 0.0f) {
                startColorValue = mBgEffectData.gradientColors2;
                endColorValue = mBgEffectData.gradientColors1;
                executeAnim();
            } else if (cycleCount % 4.0f == 1.0f) {
                startColorValue = mBgEffectData.gradientColors1;
                endColorValue = mBgEffectData.gradientColors2;
                executeAnim();
            } else if (cycleCount % 4.0f == 2.0f) {
                startColorValue = mBgEffectData.gradientColors2;
                endColorValue = mBgEffectData.gradientColors3;
                executeAnim();
            } else if (cycleCount % 4.0f == 3.0f) {
                startColorValue = mBgEffectData.gradientColors3;
                endColorValue = mBgEffectData.gradientColors2;
                executeAnim();
            }
            cycleCount += 1.0f;
        }
        prevT = floor;
        linearInterpolate(uColors, startColorValue, endColorValue, colorInterpT);
    }

    private void executeAnim() {
        if (mHandler == null) return;
        mHandler.removeCallbacksAndMessages(null);
        colorInterpT = 0.0f;
        mColorAnimStart = System.nanoTime();
        mColorAnimRunning = true;
        gradientSpeed = mBgEffectData.gradientSpeedChange;
        mHandler.postDelayed(
            () -> gradientSpeed = mBgEffectData.gradientSpeedRest,
            SPEED_REST_DELAY_MS
        );
    }

    public static void linearInterpolate(float[] colors, float[] startColor, float[] endColor, float color) {
        for (int i = 0; i < startColor.length; i++) {
            float f2 = startColor[i];
            colors[i] = f2 + ((endColor[i] - f2) * color);
        }
    }

    public void setType(BgEffectDataManager.DeviceType deviceType, BgEffectDataManager.ThemeMode themeMode, float[] uBound) {
        uBgBound = uBound;
        mBgRuntimeShader.setFloatUniform("uBound", uBound);
        mBgEffectData = mBgEffectDataManager.getData(deviceType, themeMode);
        uAnimTime = 0.0f;
        cycleCount = 0.0f;
        prevT = 0.0f;
        colorInterpT = 0.0f;
        mColorAnimRunning = false;
        gradientSpeed = mBgEffectData.gradientSpeedRest;
        startColorValue = mBgEffectData.gradientColors2;
        endColorValue = mBgEffectData.gradientColors2;
        linearInterpolate(uColors, startColorValue, endColorValue, colorInterpT);
        mBgRuntimeShader.setFloatUniform("uTranslateY", mBgEffectData.uTranslateY);
        mBgRuntimeShader.setFloatUniform("uPoints", mBgEffectData.uPoints);
        mBgRuntimeShader.setFloatUniform("uColors", uColors);
        mBgRuntimeShader.setFloatUniform("uNoiseScale", mBgEffectData.uNoiseScale);
        mBgRuntimeShader.setFloatUniform("uPointOffset", mBgEffectData.uPointOffset);
        mBgRuntimeShader.setFloatUniform("uPointRadiusMulti", mBgEffectData.uPointRadiusMulti);
        mBgRuntimeShader.setFloatUniform("uSaturateOffset", mBgEffectData.uSaturateOffset);
        mBgRuntimeShader.setFloatUniform("uShadowColorMulti", mBgEffectData.uShadowColorMulti);
        mBgRuntimeShader.setFloatUniform("uShadowColorOffset", mBgEffectData.uShadowColorOffset);
        mBgRuntimeShader.setFloatUniform("uShadowOffset", mBgEffectData.uShadowOffset);
        mBgRuntimeShader.setFloatUniform("uAlphaMulti", mBgEffectData.uAlphaMulti);
        mBgRuntimeShader.setFloatUniform("uLightOffset", mBgEffectData.uLightOffset);
        mBgRuntimeShader.setFloatUniform("uAlphaOffset", mBgEffectData.uAlphaOffset);
        mBgRuntimeShader.setFloatUniform("uShadowNoiseScale", mBgEffectData.uShadowNoiseScale);
    }
}
