package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.download.ChunkDownloader
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.download.DownloadTask
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.download.formatSpeed
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.HcRow
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载任务属性页 —— 一条任务的全部细节。
 *
 * 为什么单独做一页而不是继续堆在卡片上：进度、速度这些"此刻态"放卡片上正好，
 * 但 URL、保存路径、分片明细、耗时、校验结论这些"档案态"信息量很大，
 * 全塞进卡片会把列表撑得没法看。分片表尤其 —— 几十个分片各自下到哪了，
 * 只有摊开成表格才看得清（排查"某个分片卡住"就靠它）。
 */
@Composable
fun DownloadDetailScreen(
    task: DownloadTask,
    speed: Long,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showChunks by remember { mutableStateOf(false) }

    val cs = MiuixTheme.colorScheme
    val pct = (task.uiProgress * 100).toInt()

    // ⚠ 必须套一层不透明底层。
    //   这一页是盖在下载列表**上面**的整页覆盖层，而 ListScreen 的
    //   containerColor 是 Transparent（那是为了在 pager 里透出极光）。
    //   直接裸用会让下面列表的文字透上来 —— 表现就是「两层字叠在一起」。
    //   FullScreenLayer 用烘焙成不透明的实色挡住下层，并统一处理状态栏 insets。
    org.linbaogu.romhub.ui.screens.FullScreenLayer(onBack = onBack) {
        // 复用项目统一的列表外壳：顶栏样式、返回键、极光采样都跟其它页一致
        org.linbaogu.romhub.ui.common.ListScreen(
            title = "任务详情",
            subtitle = task.stateLabel(),
            onBack = onBack,
        ) {
                // ---------------- 顶部进度卡 ----------------
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                task.fileName.ifBlank { task.url.take(60) },
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (task.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    progress = task.uiProgress,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${formatBytes(task.uiDoneBytes)} / " +
                                        if (task.totalBytes > 0L) formatBytes(task.totalBytes) else "未知",
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = cs.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (task.totalBytes > 0L) "$pct%" else "—",
                                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = if (task.isFailed) cs.error else cs.onSurface,
                                )
                            }
                            if (task.isActive && speed > 0L) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatSpeed(speed),
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = cs.primary,
                                )
                            }
                        }
                    }
                }

                // ---------------- 基本信息 ----------------
                item { SectionLabel("基本信息") }
                item {
                    HcGroup {
                        Column(Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                            InfoRow("状态", task.stateLabel())
                            InfoRow("大小", if (task.totalBytes > 0L) formatBytes(task.totalBytes) else "服务端未给")
                            InfoRow("已下载", formatBytes(task.uiDoneBytes))
                            InfoRow("剩余", task.remainingHint())
                            InfoRow("分片线程数", "${task.effectiveThreads(Prefs.downloadThreads(ctx))}")
                            InfoRow(
                                "支持断点续传",
                                if (task.resumable) "是" else "否（服务端不支持 Range）",
                            )
                            if (task.isHls) InfoRow("类型", "HLS 流（m3u8）")
                            InfoRow("已耗时", formatDuration(task.elapsedMs))
                            if (task.error.isNotBlank()) InfoRow("错误", task.error)
                        }
                    }
                }

                // ---------------- 地址与保存 ----------------
                item { SectionLabel("地址与保存") }
                item {
                    HcGroup {
                        Column(Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                            InfoRow("下载地址", task.url, mono = true)
                            InfoRow(
                                "保存位置",
                                runCatching {
                                    DownloadManager.fileOf(task).absolutePath
                                }.getOrElse { "未知" },
                                mono = true,
                            )
                        }
                        HcDivider()
                        HcRow(
                            title = "复制下载链接",
                            subtitle = "拷到别处继续下，或分享给别人",
                            showArrow = true,
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(task.url))
                                SnackbarController.show("链接已复制")
                            },
                        )
                        HcDivider()
                        HcRow(
                            title = "复制文件路径",
                            subtitle = "完整路径，可粘到文件管理器",
                            showArrow = true,
                            onClick = {
                                val p = runCatching { DownloadManager.fileOf(task).absolutePath }.getOrNull()
                                if (p.isNullOrBlank()) {
                                    SnackbarController.show("路径拿不到")
                                } else {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(p))
                                    SnackbarController.show("路径已复制")
                                }
                            },
                        )
                    }
                }

                // ---------------- 请求头 ----------------
                if (task.headers.isNotEmpty()) {
                    item { SectionLabel("请求头") }
                    item {
                        HcGroup {
                            Column(Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                                task.headers.forEach { (k, v) ->
                                    InfoRow(k, v, mono = true)
                                }
                            }
                        }
                    }
                }

                // ---------------- 校验结论 ----------------
                if (task.verifyNote.isNotBlank()) {
                    item { SectionLabel("完整性校验") }
                    item {
                        Card {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    task.verifyNote,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = when (task.verifyOk) {
                                        false -> cs.error
                                        true -> cs.primary
                                        else -> cs.onSurface
                                    },
                                )
                            }
                        }
                    }
                }

                // ---------------- 分片明细 ----------------
                if (task.chunks.size > 1) {
                    item { SectionLabel("分片明细 · ${task.chunks.size} 段") }
                    item {
                        HcRow(
                            title = if (showChunks) "收起分片" else "展开分片",
                            subtitle = "看每一段各自下到哪了（排查卡住的分片用）",
                            showArrow = true,
                            onClick = { showChunks = !showChunks },
                        )
                    }
                    if (showChunks) {
                        item {
                            Card {
                                Column(Modifier.padding(12.dp)) {
                                    val maxRows = 40
                                    val list = if (task.chunks.size > maxRows) {
                                        task.chunks.take(maxRows)
                                    } else {
                                        task.chunks
                                    }
                                    list.forEachIndexed { i, c ->
                                        val cp = if (c.size > 0) {
                                            (c.done.toFloat() / c.size).coerceIn(0f, 1f)
                                        } else 0f
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "#${i + 1}",
                                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                                color = cs.onSurfaceVariantSummary,
                                                modifier = Modifier.width(34.dp),
                                            )
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(cs.surfaceContainerHigh),
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth(cp)
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(
                                                            if (c.finished) cs.primary
                                                            else cs.primary.copy(alpha = 0.55f)
                                                        ),
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${formatBytes(c.done)} / ${formatBytes(c.size)}",
                                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                                color = cs.onSurfaceVariantSummary,
                                                modifier = Modifier.width(120.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (task.chunks.size > maxRows) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "……还有 ${task.chunks.size - maxRows} 段未列出",
                                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                            color = cs.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- 操作 ----------------
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.isDone) {
                            PrimaryButton("打开文件") { onOpen() }
                        } else {
                            if (task.isActive) {
                                PrimaryButton("暂停") { DownloadManager.pause(task.id) }
                            } else {
                                PrimaryButton("继续下载") { DownloadManager.resume(task.id) }
                            }
                            GhostButton("重新下载") { onRetry() }
                        }
                        GhostButton("删除任务") { onDelete() }
                    }
                }
        }
    }
}

/** 毫秒 → 「1h 02m」「3m 05s」「12s」。0 显示「—」。 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${"%02d".format(m)}m"
        m > 0 -> "${m}m ${"%02d".format(s)}s"
        else -> "${s}s"
    }
}

/** 未使用的常量引用保持（避免 IDE 提示 ChunkDownloader 未被本文件使用）。 */
private val DETAIL_MAX_THREADS = ChunkDownloader.MAX_THREADS
