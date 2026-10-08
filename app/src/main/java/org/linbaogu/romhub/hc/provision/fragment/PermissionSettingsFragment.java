/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package org.linbaogu.romhub.hc.provision.fragment;

import static org.linbaogu.romhub.hc.provision.utils.NetworkManager.isInternetAvailable;
import static org.linbaogu.romhub.hc.provision.utils.NetworkManager.isNetworkConnected;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.linbaogu.romhub.hc.common.PermissionUtils;
import org.linbaogu.romhub.R;
import org.linbaogu.romhub.hc.provision.widget.PermissionItemView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fan.provision.OobeUtils;

public class PermissionSettingsFragment extends BaseFragment {
    private static final int REQUEST_GET_INSTALLED_APPS = 1101;

    private View mNextView;

    PermissionItemView mRootPermissionItem;
    PermissionItemView mNetworkPermissionItem;
    PermissionItemView mLspPermissionItem;
    PermissionItemView mInstalledAppsPermissionItem;
    PermissionItemView mStoragePermissionItem;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private long lastNetworkCheck = 0;

    public static boolean isModuleActive = false;

    @Override
    protected int getLayoutId() {
        return R.layout.provision_permission_layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mNetworkPermissionItem = view.findViewById(R.id.network);
        mInstalledAppsPermissionItem = view.findViewById(R.id.installed_apps);
        mStoragePermissionItem = view.findViewById(R.id.storage);

        if (mRootPermissionItem != null) mRootPermissionItem.setItemTitle(R.string.provision_permission_root);
        mNetworkPermissionItem.setItemTitle(R.string.provision_permission_internet);
        if (mLspPermissionItem != null) mLspPermissionItem.setItemTitle(R.string.provision_permission_lsp);
        mInstalledAppsPermissionItem.setItemTitle(R.string.provision_permission_installed_apps);
        if (mStoragePermissionItem != null) {
            mStoragePermissionItem.setItemTitle(R.string.provision_permission_storage);
            mStoragePermissionItem.setEnabled(true);   // 要能点，不能像网络项那样只读
            mStoragePermissionItem.setOnClickListener(v -> requestStoragePermission());
        }

        mNetworkPermissionItem.setEnabled(false);
        mInstalledAppsPermissionItem.setOnClickListener(v -> requestInstalledAppsPermission());

        checkNetwork();
        checkRooted();
        checkLsp();
        checkInstalledAppsPermission();
        checkStoragePermission();
        checkStoragePermission();
        registerNetworkCallback();
    }

    private void checkRooted() {
        executor.execute(() -> {
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (mRootPermissionItem != null) {
                    mRootPermissionItem.setChecked(isDeviceRooted());
                }
            });
        });
    }

    private void checkLsp() {
        executor.execute(() -> {
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (mLspPermissionItem != null) {
                    mLspPermissionItem.setChecked(isModuleActive);
                }
            });
        });
    }

    private void checkNetwork() {
        executor.execute(() -> {
            boolean connected = isNetworkConnected(requireContext());
            boolean internet = connected && isInternetAvailable();

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                mNetworkPermissionItem.setChecked(internet);
                setAllowNext(internet);
            });
        });
    }

    private void updateNetworkState(boolean state) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            mNetworkPermissionItem.setChecked(state);
            setAllowNext(state);
        });
    }

    private void setAllowNext(boolean allowNext) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            mNextView = OobeUtils.getNextView(getActivity());
            mNextView.setEnabled(allowNext);
            mNextView.setAlpha(allowNext ? OobeUtils.NO_ALPHA : OobeUtils.HALF_ALPHA);
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        checkNetwork();
        checkRooted();
        checkLsp();
        checkInstalledAppsPermission();
        checkStoragePermission();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unregisterNetworkCallback();
        executor.shutdownNow();
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager)
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                checkNetworkDebounced();
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateNetworkState(false);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    updateNetworkState(false);
                } else {
                    checkNetworkDebounced();
                }
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }

    private void checkNetworkDebounced() {
        long now = System.currentTimeMillis();
        if (now - lastNetworkCheck < 1500) return;
        lastNetworkCheck = now;
        checkNetwork();
    }

    private boolean isDeviceRooted() {
        String[] paths = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        };

        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }

        try {
            Process process = Runtime.getRuntime().exec(new String[]{ "su", "-c", "id" });
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception ignored) {
        }

        return false;
    }

    private void checkInstalledAppsPermission() {
        if (!isAdded() || mInstalledAppsPermissionItem == null) {
            return;
        }
        mInstalledAppsPermissionItem.setChecked(
            PermissionUtils.hasInstalledAppsPermission(requireContext())
        );
    }

    private void requestInstalledAppsPermission() {
        if (!isAdded()) {
            return;
        }
        if (PermissionUtils.hasInstalledAppsPermission(requireContext())) {
            checkInstalledAppsPermission();
        checkStoragePermission();
            return;
        }
        requestPermissions(
            new String[]{PermissionUtils.PERMISSION_GET_INSTALLED_APPS},
            REQUEST_GET_INSTALLED_APPS
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_GET_INSTALLED_APPS) {
            return;
        }

        if (PermissionUtils.isInstalledAppsPermissionGranted(permissions, grantResults)
            || PermissionUtils.hasInstalledAppsPermission(requireContext())) {
            checkInstalledAppsPermission();
        checkStoragePermission();
            return;
        }
        checkInstalledAppsPermission();
        checkStoragePermission();
    }


    /** 存储权限：查一下有没有给（Android 11+ 看「所有文件访问」）。 */
    private void checkStoragePermission() {
        executor.execute(() -> {
            if (!isAdded()) return;
            boolean ok;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                ok = android.os.Environment.isExternalStorageManager();
            } else {
                ok = org.linbaogu.romhub.hc.common.PermissionUtils.hasPermission(
                        requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            requireActivity().runOnUiThread(() -> {
                if (mStoragePermissionItem != null) mStoragePermissionItem.setChecked(ok);
            });
        });
    }

    /**
     * 点存储项 → 申请存储权限。
     *
     * ⚠️ Android 13+ 上 READ/WRITE_EXTERNAL_STORAGE 已是空操作（targetSdk 37），
     * 必须走「所有文件访问」（MANAGE_EXTERNAL_STORAGE）的系统设置页。
     */
    private void requestStoragePermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.content.Intent i = new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(i);
            } else {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            }
        } catch (Throwable t) {
            try {
                startActivity(new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Throwable ignored) {
            }
        }
    }


}
