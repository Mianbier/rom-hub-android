package org.linbaogu.romhub.download

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.linbaogu.romhub.core.Prefs
import java.io.File

/**
 * 下载完成时的用户反馈：震动 / 提示音 / 自动打开。
 *
 * 三个开关：
 *   · 完成时震动
 *   · 完成时响铃
 *   · 完成后自动打开文件
 *
 * 三件事都做成"可关、失败静默"：用户没开就不做，系统不给权限（比如没开震动）
 * 也不该让下载失败 —— 所以全部 `runCatching` 包住。
 */
internal object CompletionNotifier {

    /** 一个任务下完时调用。 */
    fun onTaskDone(ctx: Context, task: DownloadTask, file: File) {
        runCatching {
            if (Prefs.downloadVibrate(ctx)) vibrate(ctx)
        }
        runCatching {
            if (Prefs.downloadSound(ctx)) playSound(ctx)
        }
        runCatching {
            // 自动打开只在"文件确实存在且用户开了"时才做
            if (Prefs.downloadAutoOpen(ctx) && file.exists()) {
                openFile(ctx, file)
            }
        }
    }

    private fun vibrate(ctx: Context) {
        val vib: Vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = ctx.getSystemService(VibratorManager::class.java)
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }) ?: return

        if (!vib.hasVibrator()) return
        // 短促两下，别用长震（夜里震一下就够了）
        val pattern = longArrayOf(0, 120, 80, 120)
        vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun playSound(ctx: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(ctx, uri)?.play()
    }

    private fun openFile(ctx: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file,
        )
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
