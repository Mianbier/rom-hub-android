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
    /**
     * 这次**实际**用了几个分片线程（探测后由 [DownloadManager] 写入）。
     *
     * 为什么要单独记：分片规划（切几片）和并发执行（同时在飞几个请求）
     * 必须用**同一个数字**。规划时线程数是
     * 「任务单独设的 > 按域名记住的 > 全局设置」三级回退的结果，
     * 而执行阶段如果一个不留神回退到了别的默认值（比如硬编码的 8），
     * 就会出现「切了 256 片、却只开 8 个并发」——文件照样能下完，
     * 但慢得像卡住，用户看到进度条几乎不动。
     *
     * 0 = 还没探测过（首次下载前）。
     */
    var activeThreads: Int = 0,
    /**
     * 这条链接是不是 HLS（m3u8）流。
     *
     * HLS 的「片」是播放列表里预先切好的 ts/fmp4 段，不能用普通分片的
     * `Range: bytes=a-b` 去切 —— 得走 [HlsDownloader]，按「同时拉几段」来提速
     * （对应设置里的「M3U8 分片线程数」）。
     * 网盘解析出来是 m3u8 时会把这个标成 true（如 UC 的转码流，用来绕过会员墙）。
     */
    var isHls: Boolean = false,
    /**
     * 队列顺序。
     *
     * 数值越小越先下。[DownloadManager.pump] 会优先挑 [queued] = false 且
     * 这个值最小的任务 —— 也就是「已经轮到、正在等着开跑」的任务。
     * 默认 0 = 没进过队列，按加入时间排队。
     */
    var queueOrder: Int = 0,
    /**
     * 是否还卡在队列里等轮到自己。
     *
     * = true 时任务停在「排队等待」，pump 不会碰它；
     * 轮到它（或用户点「开始队列」/「立即开始」）会把这里置 false，
     * 之后 pump 就按 [queueOrder] 的顺序放它出去。
     */
    var queued: Boolean = false,
    var status: String = Status.PENDING,
    var error: String = "",
    /**
     * 任务「等在这儿没动」的原因（空串 = 没有特殊原因）。
     *
     * 为什么要单独一个字段：有些拦截是**主动的、可恢复的**，不该算失败 ——
     * 比如「仅 Wi-Fi 下载」开着、当前走的是蜂窝网。这种情况任务状态合理
     * 就该是 PENDING，等网络切回 Wi-Fi 自动开始。
     *
     * 但老版本什么都没显示，用户看到的就是「点完了，一直排队中，一个字节都不下，
     * 也没报错」—— 完全不知道卡在哪。所以这里把原因落成一句人话，
     * 界面直接显示出来（「等 Wi-Fi」），并把「仅 Wi-Fi」这个开关的位置一并点出来。
     */
    var holdReason: String = "",
    /** 第一次开始下载的时间（0 = 还没开始过），用来算「已耗时」。 */
    var startedAt: Long = 0L,
    /** 累计下载耗时（毫秒）。暂停时不累加，用于属性页显示。 */
    var elapsedMs: Long = 0L,
    /**
     * 服务端声明的 SHA-256（小写十六进制）。
     *
     * 有值时下载完成后会**比对**下载内容的摘要 —— 这是最硬的完整性证明：
     * 任何一个字节不对（分片写错、被劫持替换）都会导致摘要不符。
     * 没有就只做大小 + 结构校验。来源可以是网盘接口返回的 md5/sha1，
     * 也可以是 ROM 官方页面公布的校验值。
     */
    var sha256: String = "",
    /**
     * 下载完成后做完整性校验的结论（一句话，直接显示在卡片上）。
     *
     * 空串 = 还没校验或校验不适用。有值时 UI 会把它当作"校验提示"展示 ——
     * 这是回应「下载后软件签名与之前不同」这条反馈的关键：
     * 与其让用户装到一半才看到失败，不如下完就把签名/大小摊开给他看。
     */
    var verifyNote: String = "",
    /**
     * 校验是否通过（用于界面上标绿/标红）。
     * true = 通过；false = 有问题（损坏/签名不符）；null = 未做/不适用。
     */
    var verifyOk: Boolean? = null,
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
     * 界面用的「已下字节数」：只增不减。
     *
     * 为什么跟 [doneBytes] 分开：分片重试时单片 `done` 可能回退（part 文件比记录短），
     * 直接拿 [doneBytes] 画进度条会往回缩，用户以为卡了。这里记一个历史最大值，
     * 界面只认它 —— 进度条永远向前，观感才是"接得上"的。
     * 重试/重新分片时由 [DownloadManager] 清空。
     */
    @kotlinx.serialization.Transient
    var progressFloor: Long = 0L
        private set

    /** 更新单调进度（只在超过历史最大值时生效）。 */
    fun bumpProgressFloor() {
        val d = doneBytes
        if (d > progressFloor) progressFloor = d
    }

    /** 重新计数（重试/重分片时调）。 */
    fun resetProgressFloor() {
        progressFloor = 0L
    }

    /** 单调进度对应的百分比（0f~1f）。 */
    val uiProgress: Float
        get() = when {
            status == Status.DONE -> 1f
            totalBytes > 0L -> {
                val d = if (progressFloor > 0L) progressFloor else doneBytes
                (d.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
            }
            else -> 0f
        }

    /** 单调进度对应的字节数（给卡片显示"已下/总量"用，避免数字倒退）。 */
    val uiDoneBytes: Long
        get() = if (progressFloor > 0L) progressFloor else doneBytes

    /**
     * 实际生效的线程数：任务自己设了就用它，否则回退到全局默认（[fallbackThreads]）。
     * 0 / 负数 = 未单独指定。
     */
    fun effectiveThreads(fallbackThreads: Int): Int = when {
        // 已经探测过、明确记下了这次用的线程数 → 一律以它为准，
        // 保证「切几片」和「开几个并发」永远是同一个数（见 activeThreads 注释）。
        activeThreads in 1..ChunkDownloader.MAX_THREADS -> activeThreads
        threads in 1..ChunkDownloader.MAX_THREADS -> threads
        else -> fallbackThreads.coerceIn(1, ChunkDownloader.MAX_THREADS)
    }

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

    /** 排着队但还没轮到：界面上要跟「正在跑」区分开，否则用户以为卡住了。 */
    val isWaiting: Boolean get() = queued && status == Status.PENDING

    /** 能不能暂停：只有跑着的、且服务端支持分片的才值得暂停 */
    val canPause: Boolean get() = status == Status.RUNNING && resumable

    fun stateLabel(): String = when {
        isWaiting -> "排队等待"
        holdReason.isNotBlank() -> holdReason
        status == Status.PENDING -> "排队中"
        status == Status.RUNNING -> "下载中"
        status == Status.PAUSED -> "已暂停"
        status == Status.DONE -> "已完成"
        status == Status.FAILED -> if (error.isBlank()) "失败" else "失败：$error"
        else -> status
    }

    /** 剩余时间的一句话描述（属性页用）。 */
    fun remainingHint(): String = when {
        isDone -> "已完成"
        isFailed -> "已中断"
        isPaused -> "已暂停"
        holdReason.isNotBlank() -> holdReason
        isWaiting -> "等待前面的任务"
        totalBytes > 0L -> "剩余 " + formatBytes(totalBytes - uiDoneBytes)
        else -> "大小未知"
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
