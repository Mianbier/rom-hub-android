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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.linbaogu.romhub.R;
import org.linbaogu.romhub.hc.util.AboutPhoneUtils;

public class VersionNameCard extends FrameLayout implements View.OnClickListener {

    private TextView mValue;

    public VersionNameCard(@NonNull Context context) {
        super(context);
        initView();
    }

    public VersionNameCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        LayoutInflater.from(getContext()).inflate(R.layout.app_device_info_item, this, true);
        TextView textView = findViewById(R.id.title);
        mValue = findViewById(R.id.summary);
        ImageView imageView = findViewById(R.id.arrow_right);
        if (textView != null) {
            textView.setText(getContext().getResources().getString(R.string.app_device_version_parameters));
        }
        setVersionCode();
        if (imageView != null) {
            imageView.setVisibility(View.GONE);
        }
        setOnClickListener(this);
    }

    public void setVersionCode() {
        if (mValue != null) {
            mValue.setText(AboutPhoneUtils.addVersionSuffix(getContext()));
            mValue.setGravity(0);
        }
    }


    @Override
    public void onClick(View v) {

    }
}
