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

package org.linbaogu.romhub.hc.provision.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkManager {

    public static boolean isNetworkConnected(Context context) {
        if (context == null) {
            return false;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network != null) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(network);
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true;
                } else return nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            }
        }
        return false;
    }

    public static boolean isInternetAvailable() {
        try {
            InetAddress address = InetAddress.getByName("cloudflare.com");
            return !address.equals("");
        } catch (UnknownHostException e) {
            // Log error
        }
        return false;
    }

}
