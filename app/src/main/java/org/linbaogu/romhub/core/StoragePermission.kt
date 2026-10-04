package org.linbaogu.romhub.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 存储权限工具。
 *
 * 为什么需要：下载要落到公共的 `Download/rom-hub/`，让用户在文件管理器里直接找得到。
 * 但 Android 10 起分区存储把这条路径锁了，只有两条路：
 *   ① MediaStore（复杂，且不适合多线程分片写）
 *   ② `MANAGE_EXTERNAL_STORAGE`「所有文件访问」—— 直接给 `File` API 全盘读写权
 *
 * 自用 App 选 ②：简单、可靠、能真正写到公共目录。
 * 代价是得引导用户去系统设置页手动开一次（无法用运行时弹窗申请）。
 */
object StoragePermission {

    /** 是否已拿到「能写公共 Download 目录」的权限。 */
    fun granted(ctx: Context): Boolean = when {
        // Android 11+：查 MANAGE_EXTERNAL_STORAGE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()

        // Android 10 及以下：运行时 WRITE_EXTERNAL_STORAGE
        else -> ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** 打开系统设置里本 App 的「所有文件访问」页（Android 11+）。 */
    fun openAllFilesSettings(ctx: Context) {
        runCatching {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        }.onFailure {
            // 个别 ROM 没有这个 action，退回「所有文件访问」总列表
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** 公共下载目录：`/sdcard/Download/rom-hub/`（+ 可选的平台子目录）。 */
    fun publicDownloadDir(subDir: String = ""): java.io.File {
        val base = java.io.File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "rom-hub",
        )
        val dir = if (subDir.isBlank()) base else java.io.File(base, subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
