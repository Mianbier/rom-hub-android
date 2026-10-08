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

package org.linbaogu.romhub.hc.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.linbaogu.romhub.R;

import fan.cardview.HyperCardView;
import fan.core.utils.HyperMaterialUtils;
import fan.core.utils.MaterialDayNightConfig;
import fan.core.utils.RomUtils;
import fan.theme.token.BloomStrokeToken;
import fan.theme.token.ColorBlendToken;
import fan.theme.token.MaterialDayNightToken;
import fan.theme.token.MaterialToken;
import fan.theme.token.hypermaterial.Mask;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class SwitchManager {

    private final Context mContext;
    private final ViewGroup mParent;
    private SwitchView mSwitchView;

    private boolean isFloatingStyle;
    private OnSwitchChangeListener mUserListener;

    public SwitchManager(ViewGroup parent) {
        mParent = parent;
        mContext = parent.getContext();
    }

    public boolean isFloatingStyle() {
        return isFloatingStyle;
    }

    /**
     * 初始化并挂载视图
     */
    public void addSwitchView(int menuRes, NavigationStyle style) {
        if (mSwitchView == null) {
            mSwitchView = (SwitchView) LayoutInflater.from(mContext)
                .inflate(R.layout.switch_card_view, mParent, false);
            if (mUserListener != null) {
                mSwitchView.setOnSwitchChangeListener(mUserListener);
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );

            mParent.addView(mSwitchView, lp);

            ViewCompat.requestApplyInsets(mSwitchView);
        }

        mSwitchView.inflateMenu(menuRes);
        setFloatingStyle(style == NavigationStyle.CAPSULE_ICON);
    }

    /**
     * 统一入口：切换样式
     */
    public void setFloatingStyle(boolean useFloating) {
        this.isFloatingStyle = useFloating;
        if (mSwitchView != null) {
            mSwitchView.updateStyle(useFloating ? NavigationStyle.CAPSULE_ICON : NavigationStyle.BOTTOM_LABEL);
        }
    }

    /**
     * 外部控制选中：按索引
     */
    public void setSelectedPosition(int position, boolean notify) {
        if (mSwitchView != null) {
            mSwitchView.setSelectedTab(position, notify);
        }
    }

    /**
     * 外部控制选中：按 Menu ID
     */
    public void setSelectedItemId(int itemId, boolean notify) {
        if (mSwitchView != null) {
            int pos = mSwitchView.getPositionById(itemId);
            if (pos != -1) mSwitchView.setSelectedTab(pos, notify);
        }
    }

    /**
     * 代理设置监听器
     */
    public void setOnSwitchChangeListener(OnSwitchChangeListener listener) {
        mUserListener = listener;
        if (mSwitchView != null) {
            mSwitchView.setOnSwitchChangeListener(listener);
        }
    }

    public void show() {
        if (mSwitchView != null) mSwitchView.setVisibility(View.VISIBLE);
    }

    public void hide() {
        if (mSwitchView != null) mSwitchView.setVisibility(View.GONE);
    }

    public SwitchView getSwitchView() {
        return mSwitchView;
    }
}
