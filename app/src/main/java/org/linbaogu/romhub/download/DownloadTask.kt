package org.linbaogu.romhub.download

import kotlinx.serialization.Serializable

/**
 * 一个下载分片。
 *
 * 分片按 [start, end] 闭区间切，`done` 是这一片已经落盘的字节数 ——
 * 有了它才能断点续传：下次接着从 `start + done` 继续要数据。
 */
@Serializable
data class ChunkState(
    val start: Long,
    val end: Long,
    var done: Long = 0L,
) {
    val size: Long get() = end - start + 1
    val finished: Boolean get() = done >= size
}

/**
 * 一个下载任务。
 *
 * 持久化到 filesDir/downloads.json，App 被杀掉/重启后能接着下。
 * 注意 [status] 存的是字符串（方便以后加状态，也避免序列化枚举踩坑）。
 */
@Serializable
data class DownloadTask(
    val id: Long,
    val url: String,
    var fileName: String,
    val subDir: String = "",
    val headers: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    /** 总字节数；-1 表示服务端没给（未知大小） */
    var totalBytes: Long = -1L,
    /** 是否支持 Range 分片（不支持就只能单线程顺着下） */
    var resumable: Boolean = true,
    var chunks: List<ChunkState> = emptyList(),
    /**
     * 这个任务用几个分片线程（1~[ChunkDownloader.MAX_THREADS]）。
     *
     * 为什么存到任务上而不是只读全局设置：网盘下载器里可以给单个任务单独选线程
     * （百度易限速给 4，115/夸克给 64~256），互不影响。
     * 老版本存的任务没有这个字段，反序列化会用默认值 0，
     * [effectiveThreads] 会把它回退到全局设置，所以不用做数据迁移。
     */
    var threads: Int = 0,
    var status: String = Status.PENDING,
    var error: String = "",
    /**
     * 每次刷新 UI 时 +1。
     *
     * 任务对象是原地修改的（进度、状态），如果 StateFlow 里还是同一个实例，
     * `value = value` 因为 equals 相等不会发出通知 → 界面不动。
     * 加个自增的 rev 让每次都是「新值」，界面才会跟着刷。
     */
    @kotlinx.serialization.Transient
    var rev: Long = 0L,
) {
    object Status {
        const val PENDING = "pending"
        const val RUNNING = "running"
        const val PAUSED = "paused"
        const val DONE = "done"
        const val FAILED = "failed"
    }

    val doneBytes: Long get() = chunks.sumOf { it.done }

    /**
     * 实际生效的线程数：任务自己设了就用它，否则回退到全局默认（[fallbackThreads]）。
     * 0 / 负数 = 未单独指定。
     */
    fun effectiveThreads(fallbackThreads: Int): Int =
        if (threads in 1..ChunkDownloader.MAX_THREADS) threads
        else fallbackThreads.coerceIn(1, ChunkDownloader.MAX_THREADS)

    val progress: Float
        get() = when {
            status == Status.DONE -> 1f
            totalBytes > 0L -> (doneBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
            else -> 0f
        }

    val isActive: Boolean get() = status == Status.RUNNING || status == Status.PENDING
    val isPaused: Boolean get() = status == Status.PAUSED
    val isDone: Boolean get() = status == Status.DONE
    val isFailed: Boolean get() = status == Status.FAILED

    /** 能不能暂停：只有跑着的、且服务端支持分片的才值得暂停 */
    val canPause: Boolean get() = status == Status.RUNNING && resumable

    fun stateLabel(): String = when (status) {
        Status.PENDING -> "排队中"
        Status.RUNNING -> "下载中"
        Status.PAUSED -> "已暂停"
        Status.DONE -> "已完成"
        Status.FAILED -> if (error.isBlank()) "失败" else "失败：$error"
        else -> status
    }
}

/** 把字节数变成人能看的字符串。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "未知"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

fun formatSpeed(bytesPerSec: Long): String =
    if (bytesPerSec <= 0) "" else formatBytes(bytesPerSec) + "/s"

/**
 * 剩余时间估算：拿「总 - 已下」除以当前速度。
 *
 * 速度 <= 0（暂停/排队）或总大小未知时返回空串 —— 宁可什么都不显示，
 * 也别给个「剩余 --:--」让人以为卡住了。
 */
fun formatEta(task: DownloadTask, speed: Long): String {
    if (speed <= 0L || task.totalBytes <= 0L) return ""
    val remain = task.totalBytes - task.doneBytes
    if (remain <= 0L) return ""
    val sec = remain / speed
    return "剩余 " + when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
    }
}
