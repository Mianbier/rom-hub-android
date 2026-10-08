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

import static org.linbaogu.romhub.hc.util.DeviceHelper.System.getSystemVersionIncremental;
import static org.linbaogu.romhub.hc.util.DeviceHelper.System.isMoreAndroidVersion;
import static org.linbaogu.romhub.hc.util.PropUtils.getProp;
import static org.linbaogu.romhub.hc.util.PropUtils.getPropSu;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.linbaogu.romhub.R;
import org.linbaogu.romhub.hc.util.AboutPhoneUtils;

import java.util.Objects;

public class DeviceInfoCard extends FrameLayout {

    private TextView mDeviceNameTitle;
    private TextView mDeviceInfoDeviceTitle, mDeviceInfoDeviceSummary;
    private TextView mDeviceInfoAndroidTitle, mDeviceInfoAndroidSummary;
    private TextView mDeviceInfoOSTitle, mDeviceInfoOSSummary;

    public DeviceInfoCard(@NonNull Context context) {
        this(context, null);
    }

    public DeviceInfoCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        LayoutInflater.from(getContext()).inflate(R.layout.app_device_info_item2, this, true);
        mDeviceNameTitle = findViewById(R.id.device_name);
        mDeviceInfoDeviceTitle = findViewById(R.id.device_info_device_title);
        mDeviceInfoDeviceSummary = findViewById(R.id.device_info_device_summary);
        mDeviceInfoAndroidTitle = findViewById(R.id.device_info_android_title);
        mDeviceInfoAndroidSummary = findViewById(R.id.device_info_android_summary);
        mDeviceInfoOSTitle = findViewById(R.id.device_info_os_title);
        mDeviceInfoOSSummary = findViewById(R.id.device_info_os_summary);

       refreshDeviceInfo();
    }


    public void refreshDeviceInfo() {
        String deviceName;
        if (isMoreAndroidVersion(36)) {
            // 我就说我设备名字怎么就对不上了，这玩意还要 Root 获取，破烂
            deviceName = getPropSu("persist.private.device_name");
        } else {
            deviceName = getProp("persist.sys.device_name");
        }
        String marketName = getProp("ro.product.marketname");
        String androidVersion = getProp("ro.build.version.release");
        String osVersion = AboutPhoneUtils.addVersionSuffix(getContext());

        if (Objects.equals(marketName, "")) marketName = android.os.Build.MODEL;
        if (Objects.equals(deviceName, "")) deviceName = marketName;
        if (Objects.equals(osVersion, "")) osVersion = getSystemVersionIncremental();
        if (Objects.equals(osVersion, "")) osVersion = androidVersion;

        mDeviceNameTitle.setText(deviceName);
        mDeviceInfoDeviceTitle.setText(marketName);
        mDeviceInfoDeviceSummary.setText(org.linbaogu.romhub.R.string.about_device_info_device);
        mDeviceInfoAndroidTitle.setText(androidVersion);
        mDeviceInfoAndroidSummary.setText(org.linbaogu.romhub.R.string.about_device_info_android);
        mDeviceInfoOSTitle.setText(osVersion);
        mDeviceInfoOSSummary.setText(org.linbaogu.romhub.R.string.about_device_info_os);
    }
}
