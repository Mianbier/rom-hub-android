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

package org.linbaogu.romhub.hc.provision.state;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.linbaogu.romhub.hc.provision.activity.DefaultActivity;

import java.lang.reflect.InvocationTargetException;

import fan.provision.OobeUtils;

public class State {

    private static final String TAG = "State";
    public static final String PREFIX = "org.linbaogu.romhub.hc.provision.state.";

    protected Context mContext;

    protected StateMachine mStateMachine;
    protected String mPackageName;
    public String mClassName;
    public Class<?> mTargetClass;

    protected Handler mHandler = new Handler(Looper.getMainLooper());

    public boolean canBackTo() {
        return true;
    }

    public State getNextState() {
        return null;
    }

    public void onLeave() {}

    public static State create(String name) {
        try {
            return (State) Class.forName(PREFIX + name).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            Log.e(TAG, String.valueOf(e));
            return null;
        } catch (InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public State setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    public State setClassName(String className) {
        mClassName = className;
        return this;
    }

    public State setTargetClass(Class<?> targetClass) {
        mTargetClass = targetClass;
        return this;
    }

    public State setStateMachine(StateMachine stateMachine) {
        mStateMachine = stateMachine;
        mContext = stateMachine.getContext();
        return this;
    }

    public void setStateContext(Context context) {
        mContext = context;
    }

    public void onEnter(boolean z, boolean z2) {
        onEnter(z, z2, null);
    }

    public void onEnter(boolean z, boolean z2, @Nullable Bundle activityOptions) {
        Log.d(TAG, "targetClass is " + mTargetClass);
        Intent intent = createEnterIntent(z, z2);
        launchIntent(intent, activityOptions);
    }

    protected Intent createEnterIntent(boolean canBack, boolean toNext) {
        Intent intent = getIntent();
        intent.putExtra("extra_disable_back", !canBack);
        intent.putExtra("extra_to_next", toNext);
        return intent;
    }

    protected void launchIntent(@NonNull Intent intent, @Nullable Bundle activityOptions) {
        if (mContext instanceof DefaultActivity activity) {
            activity.launchStateActivityForResult(intent, 0, activityOptions);
            return;
        }
        if (mContext instanceof Activity activity) {
            activity.startActivity(intent, activityOptions);
            return;
        }
        mContext.startActivity(intent);
    }

    public boolean isAvailable(boolean available) {
        return mContext.getPackageManager().resolveActivity(getIntent(), 0) != null;
    }

    protected Intent getIntent() {
        Intent intent = new Intent();
        if (TextUtils.isEmpty(mPackageName)) {
            intent.setClass(mContext, mTargetClass);
        } else {
            intent.setClassName(mPackageName, mClassName);
        }
        if (OobeUtils.isDebugOobeMode(mContext)) {
            intent.putExtra(OobeUtils.EXTRA_DEBUG_OOBE, true);
        }
        return intent;
    }

    public String getPageTag() {
        return "";
    }
}
