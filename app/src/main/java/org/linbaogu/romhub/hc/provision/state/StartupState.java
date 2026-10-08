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

import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.linbaogu.romhub.R;
import org.linbaogu.romhub.hc.provision.activity.DefaultActivity;
import org.linbaogu.romhub.hc.provision.fragment.StartupFragment;
import org.linbaogu.romhub.hc.provision.utils.IKeyEvent;
import org.linbaogu.romhub.hc.provision.utils.IOnFocusListener;

public class StartupState extends State implements IKeyEvent, IOnFocusListener {

    private boolean mHasBooted;
    private Fragment.SavedState mSavedState;
    private StartupFragment mStartupFragment;

    public void setBooted(boolean booted) {
        mHasBooted = booted;
    }

    @Override
    public boolean isAvailable(boolean available) {
        return true;
    }

    @Override
    public void onEnter(boolean z, boolean z2) {
        FragmentManager fragmentManager = ((DefaultActivity) mContext).getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(StartupFragment.class.getSimpleName());
        if (fragment instanceof StartupFragment startupFragment) {
            mStartupFragment = startupFragment;
            return;
        }
        mStartupFragment = new StartupFragment();
        if (mSavedState != null) {
            mStartupFragment.setInitialSavedState(mSavedState);
        }
        buildStartupFragment(mStartupFragment, fragmentManager);
    }

    @Override
    public void onLeave() {
        FragmentManager supportFragmentManager = ((DefaultActivity) mContext).getSupportFragmentManager();
        Fragment fragmentByTag = supportFragmentManager.findFragmentByTag(StartupFragment.class.getSimpleName());
        if (fragmentByTag != null) {
            mSavedState = supportFragmentManager.saveFragmentInstanceState(supportFragmentManager.findFragmentByTag(StartupFragment.class.getSimpleName()));
            FragmentTransaction beginTransaction = supportFragmentManager.beginTransaction();
            beginTransaction.setCustomAnimations(0, R.anim.provision_slide_out_left_animator);
            beginTransaction.remove(fragmentByTag);
            beginTransaction.commitAllowingStateLoss();
        }
    }

    private void buildStartupFragment(Fragment fragment, FragmentManager fragmentManager) {
        FragmentTransaction beginTransaction = fragmentManager.beginTransaction();
        beginTransaction.replace(android.R.id.content, fragment, StartupFragment.class.getSimpleName());
        beginTransaction.commitAllowingStateLoss();
    }

    @Override
    public void keyDownDispatcher(int keyCode, KeyEvent event) {

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mStartupFragment != null) {
            mStartupFragment.onWindowFocusChanged(hasFocus);
        }
    }
}
