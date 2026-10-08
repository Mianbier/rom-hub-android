package org.linbaogu.romhub.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.download.DownloadTask
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.download.formatEta
import org.linbaogu.romhub.download.formatSpeed
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载任务卡片。
 *
 * ## 布局
 *
 * 卡片是**纯色底**（不再整卡染色），底部压一条**独立的细进度条**：
 *
 *   ┌────────────────────────────────────────────┐
 *   │ 文件名                        [❚❚]  [✕]   │
 *   │ 下载中  12.3 MB / 11.6 GB     2.1 MB/s  37%│
 *   │ [16 线程] [详情]                           │
 *   ├────────────────────────────────────────────┤  ← 进度条（贴着卡片底边）
 *   │████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
 *   └────────────────────────────────────────────┘
 *
 * 为什么不再整卡染色（老做法：进度色铺满卡片背景）：
 *   · 染色会让整卡在不同进度下颜色深浅不一，同一列表里读起来很花；
 *   · 文件名/状态文字压在有色底上，对比度随进度变化，后半段容易看不清；
 *   · 一条贴底的进度条信息更准确 —— 它**就是**进度本身，不会被误读成"卡片高亮"。
 *
 * 进度条用 3dp 高、无圆角（贴左右两边到底），只在**已经知道总大小时**才画：
 * 大小未知的文件没有分母，画个假进度只会误导。
 */
@Composable
internal fun DownloadCard(
    task: DownloadTask,
    speed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPickThreads: () -> Unit,
    onOpen: () -> Unit,
    onShowVerify: () -> Unit,
    onDetail: () -> Unit,
    onForceStart: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val ctx = LocalContext.current

    // 目标进度：完成就是满格。用 uiProgress（单调递增）而不是 progress ——
    // 分片重试时单片进度会回退，用 progress 会让进度条往回缩，看着像卡死。
    val target = when {
        task.isDone -> 1f
        task.totalBytes > 0L -> task.uiProgress
        else -> 0f
    }
    // 平滑过渡：原始进度是每 64KB 一跳的，直接铺会闪，所以做动画过渡。
    val fraction by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "download_progress",
    )

    // 进度条颜色：跟状态走（失败红 / 完成主色 / 暂停与等条件用灰）
    val barColor = when {
        task.isFailed -> cs.error
        task.isDone -> cs.primary
        task.isPaused || task.holdReason.isNotBlank() -> cs.onSurfaceVariantSummary
        else -> cs.primary
    }

    val pct = (task.uiProgress * 100).toInt()
    val eta = formatEta(task, speed)
    val spd = formatSpeed(speed)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainerHigh),
    ) {
        // ---- 内容层 ----
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // 行 1：文件名 + 右侧操作图标（右边是圆形图标按钮）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.fileName.ifBlank { task.url.take(60) },
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    // 行 2：三栏（左：状态+进度；中：速度；右：百分比）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(task.stateLabel())
                                if (task.totalBytes > 0L) {
                                    append("  ")
                                    append(formatBytes(task.uiDoneBytes))
                                    append(" / ")
                                    append(formatBytes(task.totalBytes))
                                } else {
                                    append("  ")
                                    append(formatBytes(task.uiDoneBytes))
                                }
                            },
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = when {
                                task.isFailed -> cs.error
                                task.holdReason.isNotBlank() -> cs.error
                                task.isDone -> cs.primary
                                else -> cs.onSurfaceVariantSummary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        // 速度 / 剩余时间（中间弹性区，空间不够先挤这里）
                        val mid = buildString {
                            if (spd.isNotBlank() && task.isActive) append(spd)
                            if (eta.isNotBlank() && task.isActive) {
                                if (isNotEmpty()) append("  ")
                                append(eta)
                            }
                        }
                        if (mid.isNotBlank()) {
                            Text(
                                mid,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = cs.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        // 百分比固定靠右，等宽（固定宽度保证数字不跳）
                        Text(
                            if (task.totalBytes > 0L) "$pct%" else "—",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = if (task.isFailed) cs.error else cs.onSurface,
                            modifier = Modifier.width(44.dp),
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // 圆形操作按钮（圆形底 + 图标）
                val (icon, action) = when {
                    task.isDone -> "↗" to onOpen
                    task.isWaiting -> "▶" to onDetail
                    task.isActive -> "❚❚" to onPause
                    task.isPaused -> "▶" to onResume
                    task.isFailed -> "↻" to onRetry
                    else -> "❚❚" to onPause
                }
                RoundIcon(icon, cs.primary) { action() }
                Spacer(Modifier.width(6.dp))
                RoundIcon("✕", cs.onSurfaceVariantSummary) { onRemove() }
            }

            // 行 3：完成后显示保存位置 / 校验结论；未完成时点这一行进属性页
            if (task.isDone) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = task.verifyNote.isNotBlank()) { onShowVerify() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (task.verifyNote.isNotBlank()) task.verifyNote
                        else if (org.linbaogu.romhub.core.StoragePermission.granted(ctx))
                            "已保存到 Download/rom-hub/"
                        else "已保存到 App 私有目录",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = when (task.verifyOk) {
                            false -> cs.error
                            true -> cs.primary
                            else -> cs.onSurfaceVariantSummary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (task.verifyNote.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("详情", fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = cs.primary)
                    }
                }
            }

            // 行 4：低频操作（线程数 / 查看详情）—— 放最底下，不跟主操作抢视觉
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 「仅 Wi-Fi 下载」把任务挡在门外时，给一个**明确的出口**：
                // 一键放行这一条（只对这条任务生效，不动全局开关）。
                // 没有这个按钮，用户只能自己去设置里翻开关 —— 而他根本不知道
                // 是这个开关拦的（老版本连提示都没有）。
                if (task.holdReason.isNotBlank()) {
                    CardAction("用移动网络下载") { onForceStart() }
                }
                if (task.isFailed) {
                    CardAction("重试") { onRetry() }
                }
                val isCustom = task.threads in 1..org.linbaogu.romhub.download.ChunkDownloader.MAX_THREADS
                val shownThreads = task.effectiveThreads(
                    org.linbaogu.romhub.core.Prefs.downloadThreads(ctx)
                )
                CardAction(if (isCustom) "$shownThreads 线程" else "$shownThreads·全局") { onPickThreads() }
                CardAction("详情") { onDetail() }
                if (task.isDone) {
                    CardAction("打开") { onOpen() }
                }
            }
        }

        // ---- 进度条：贴着卡片底边的独立细条 ----
        // 只有知道总大小时才画（未知大小没有分母，画了就是假的）。
        // 高度 3dp、直角（被外层 clip 裁成跟卡片一样的圆角），左右撑满。
        if (task.totalBytes > 0L || task.isDone) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(cs.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(barColor),
                )
            }
        }
    }
}

/** 圆形小按钮（卡片右侧的操作图标）。 */
@Composable
private fun RoundIcon(text: String, tint: Color, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            // 卡片底已是 surfaceContainerHigh，按钮用 surfaceContainerHighest 才看得出是"按钮"
            .background(cs.surfaceContainerHighest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 卡片底部的轻量文字按钮（比 GhostButton 更小，不抢主视觉）。 */
@Composable
private fun CardAction(label: String, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceContainerHighest)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = cs.onSurface,
        )
    }
}
