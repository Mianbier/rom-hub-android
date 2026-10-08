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

package org.linbaogu.romhub.hc.about.widget;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.linbaogu.romhub.R;
import org.linbaogu.romhub.hc.util.LargeFontUtils;

public class DeviceNameCard extends FrameLayout implements View.OnClickListener {

    private TextView mDeviceNameText;

    public DeviceNameCard(@NonNull Context context) {
        super(context);
        initView();
    }

    public DeviceNameCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        LayoutInflater.from(getContext()).inflate(R.layout.app_device_info_item, this, true);
        ((TextView) findViewById(R.id.title)).setText(getResources().getString(R.string.device_name));
        mDeviceNameText = findViewById(R.id.summary);
        if (mDeviceNameText != null && LargeFontUtils.isLargeFontLevel(getContext())) {
            mDeviceNameText.setMaxLines(2);
            if (ViewCompat.getLayoutDirection(mDeviceNameText) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                mDeviceNameText.setGravity(8388613);
            } else {
                mDeviceNameText.setGravity(0);
            }
            mDeviceNameText.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        refreshDeviceName();
        setOnClickListener(this);
    }

    public void refreshDeviceName() {
        String deviceName = Settings.Global.getString(getContext().getContentResolver(), "device_name");
        if (!TextUtils.isEmpty(deviceName) && mDeviceNameText != null) {
            mDeviceNameText.setText(deviceName);
        }
    }

    @Override
    public void onClick(View v) {

    }
}
