package org.linbaogu.romhub.download

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 下载任务的落盘存储。
 *
 * 用 JSON 而不是 Room：任务撑死几十条，一次全读全写完全够用，
 * 还省掉一个编译期注解处理器（Room 要 KSP）。
 */
object DownloadStore {

    private const val FILE_NAME = "downloads.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context): MutableList<DownloadTask> = runCatching {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        val text = f.readText()
        if (text.isBlank()) return mutableListOf()
        json.decodeFromString<List<DownloadTask>>(text).toMutableList()
    }.getOrElse { mutableListOf() }

    fun save(ctx: Context, tasks: List<DownloadTask>) {
        runCatching {
            val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(tasks))
            // 先写临时文件再改名：中途被杀掉也不会留半个坏 JSON
            val dst = file(ctx)
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
        }
    }

    /**
     * 下载文件的落地目录。
     *
     * 优先落到**公共** `Download/rom-hub/`（用户能在文件管理器里直接找到）；
     * 没拿到「所有文件访问」权限时退到 App 私有外部目录 —— 先保证能下，权限事后补。
     */
    fun downloadDir(ctx: Context, subDir: String = ""): File {
        val dir = if (org.linbaogu.romhub.core.StoragePermission.granted(ctx)) {
            org.linbaogu.romhub.core.StoragePermission.publicDownloadDir(subDir)
        } else {
            val base = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: File(ctx.filesDir, "download")
            if (subDir.isBlank()) base else File(base, subDir)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 这个任务当前应该落在哪个目录（给 UI 显示「保存位置」和查找已有文件用）。 */
    fun dirFor(ctx: Context, subDir: String): File = downloadDir(ctx, subDir)
}
