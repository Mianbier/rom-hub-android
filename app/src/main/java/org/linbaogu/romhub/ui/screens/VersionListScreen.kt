package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.Mirror
import org.linbaogu.romhub.data.RomVersion
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.common.StateChip
import org.linbaogu.romhub.ui.common.mirrorsOf
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VersionListScreen(
    code: String,
    deviceName: String,
    region: String,
    branch: String,
    highlight: String,
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
    /** 点下载后跳「网盘下载器 → 下载」段（可选） */
    onGoDownloader: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<RomVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var expanded by remember(highlight) { mutableStateOf(setOf(highlight)) }

    LaunchedEffect(code, region, branch, reload) {
        loading = true
        error = ""
        runCatching { Api.romVersions(ctx, code, region, branch).items }
            .onSuccess { items = it; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
        // 顺手让服务端把缺的直链补齐（失败也不影响浏览）
        runCatching { Api.resolvePending(ctx, code, region, branch) }
    }

    val subtitle = remember(items) {
        val first = items.firstOrNull()
        listOfNotNull(
            first?.regionZh?.takeIf { it.isNotBlank() },
            first?.branchZh?.takeIf { it.isNotBlank() },
            "${items.size} 个版本",
        ).joinToString(" · ")
    }

    ListScreen(
        title = deviceName,
        largeTitle = "版本列表",
        subtitle = subtitle,
        onBack = onBack,
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        if (loading) item { Hint("正在加载版本列表…") }
        if (error.isNotBlank()) item { ErrorHint(error) { reload++ } }
        if (!loading && error.isBlank() && items.isEmpty()) {
            item { Hint("该分支暂无版本记录") }
        }

        // HyperCeiler 结构：全部版本装进一张实底大卡片，每个版本是一行，
        // 点行就地展开下载链接（和 HyperOS 设置项的展开方式一致）
        if (items.isNotEmpty()) {
            item {
                HcGroup {
                    items.forEachIndexed { i, v ->
                        val isHighlight = highlight.isNotBlank() && v.version == highlight
                        val isOpen = expanded.contains(v.version)
                        if (i > 0) HcDivider(startIndent = 16.dp)
                        VersionRow(
                            v = v,
                            highlighted = isHighlight,
                            expanded = isOpen,
                            onToggle = {
                                expanded =
                                    if (isOpen) expanded - v.version else expanded + v.version
                            },
                        )
                        if (isOpen) {
                            VersionLinks(
                                v = v,
                                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                                onOpen = { url -> openUrl(ctx, url) },
                                onDownload = { url, name -> downloadWithApp(ctx, scope, url, name, onGoDownloader) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** HyperCeiler 式版本行：版本号 + 日期/Android/大小 + 状态标签 + 展开箭头。 */
@Composable
private fun VersionRow(
    v: RomVersion,
    highlighted: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val mirrors = remember(v) { mirrorsOf(v) }
    val state = versionState(v)
    val noLink = mirrors.isEmpty() && v.recoveryUrl.isBlank() && v.fastbootUrl.isBlank()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = mirrors.isNotEmpty(), onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = v.version,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (highlighted) cs.primary else cs.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    val parts = listOfNotNull(
                        v.romDate.takeIf { it.isNotBlank() },
                        v.android.takeIf { it.isNotBlank() }?.let { "Android $it" },
                        v.sizeText.takeIf { it.isNotBlank() },
                    )
                    append(parts.joinToString(" · "))
                    if (noLink) {
                        if (isNotEmpty()) append(" · ")
                        append("直链解析中")
                    }
                },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (highlighted) {
            Spacer(Modifier.width(6.dp))
            StateChip(state = "更新", textOverride = "订阅的版本")
        }
        if (state.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            StateChip(state = state)
        }
        if (mirrors.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                contentDescription = null,
                tint = cs.onSurfaceVariantSummary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 展开后的下载链接区（在卡片内部、行下方就地展开）。 */
@Composable
private fun VersionLinks(
    v: RomVersion,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDownload: (String, String) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val mirrors = remember(v) { mirrorsOf(v) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        HorizontalDivider(color = cs.dividerLine)
        Spacer(Modifier.height(8.dp))

        // 版本级「复制链接」方框：一次把这个版本的包名 + 全部直链都拿走，
        // 不用一个镜像一个镜像地点。原版只有每行的小「复制」，
        // 要拿全量链接得点好几次，这里给一个整块的入口。
        val allText = remember(v) { buildCopyText(v, mirrors) }
        if (allText.isNotBlank()) {
            Box(Modifier.fillMaxWidth()) {
                GhostButton(
                    text = "复制链接",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onCopy(allText)
                    SnackbarController.show("已复制该版本的全部链接")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (v.filenameRec.isNotBlank()) InfoRow("卡刷包名", v.filenameRec, mono = true)
        if (v.filenameFast.isNotBlank()) InfoRow("线刷包名", v.filenameFast, mono = true)
        if (v.filenameRec.isNotBlank() || v.filenameFast.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
        }
        mirrors.forEach { m ->
            MirrorBlock(
                m = m,
                recName = v.filenameRec,
                fastName = v.filenameFast,
                onCopy = onCopy,
                onOpen = onOpen,
                onDownload = onDownload,
            )
        }
    }
}

@Composable
private fun MirrorBlock(
    m: Mirror,
    recName: String,
    fastName: String,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDownload: (String, String) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    // 用户两次反馈这里「上下空隙太大」（截图里卡刷包和线刷包之间能空出一大片）。
    //
    // 真正的原因不是 Spacer，而是**按钮排布**：
    // PrimaryButton 内部是 fillMaxWidth()，放在 Row 里会吃掉整行宽度，
    // 把后面的 GhostButton 挤换行 → 三个按钮变成上下两行 → 视觉上拉出大片空白。
    //
    // 所以改成：主按钮独占一行，「浏览器下载 / 复制」收在右侧窄按钮。
    // 这样每个包固定两行高度，段落之间靠 Spacer 分隔，间距就正常了。
    Column(Modifier.padding(top = 2.dp)) {
        Text(
            text = m.name,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        if (m.recovery.isNotBlank()) {
            DownloadRow(
                mainText = "用下载器下卡刷包",
                onDownload = { onDownload(m.recovery, recName.ifBlank { guessFileName(m.recovery) }) },
                onOpen = { onOpen(m.recovery) },
                onCopy = { onCopy(m.recovery) },
            )
        }
        if (m.fastboot.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            DownloadRow(
                mainText = "用下载器下线刷包",
                onDownload = { onDownload(m.fastboot, fastName.ifBlank { guessFileName(m.fastboot) }) },
                onOpen = { onOpen(m.fastboot) },
                onCopy = { onCopy(m.fastboot) },
            )
        }
        if (m.recovery.isBlank() && m.fastboot.isBlank()) {
            Text(
                "该镜像暂无直链",
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 一个下载包对应的一行操作。
 *
 * 左：主按钮（占满剩余宽度）。右：浏览器下载 + 复制（固定窄宽）。
 * 这样无论有几个包，每行高度都一致，不会出现「按钮自己换行」造成的空隙。
 */
@Composable
private fun DownloadRow(
    mainText: String,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { PrimaryButton(mainText) { onDownload() } }
        GhostButton("浏览器", Modifier.wrapContentWidth(), onClick = onOpen)
        GhostButton("复制", Modifier.wrapContentWidth(), onClick = onCopy)
    }
}

/** 官方直链的包名兜底：从 URL 最后一段取，取不到就交给下载器自己嗅探。 */
private fun guessFileName(url: String): String =
    url.substringBefore('?').substringAfterLast('/').takeIf { it.contains('.') }.orEmpty()

/**
 * 把一个版本的全部可复制信息拼成一段纯文本。
 *
 * 顺序按「版本 → 包名 → 直链」排，粘到聊天窗口/备忘录里也能看懂，
 * 不用自己从界面上抄。这里只收真链接（recovery / fastboot / 官方下载页），
 * 空的一律跳过，免得复制出一堆空气行。
 */
private fun buildCopyText(v: RomVersion, mirrors: List<Mirror>): String {
    val lines = mutableListOf<String>()
    lines += v.version
    if (v.filenameRec.isNotBlank()) lines += "卡刷包名：${v.filenameRec}"
    if (v.filenameFast.isNotBlank()) lines += "线刷包名：${v.filenameFast}"
    mirrors.forEach { m ->
        if (m.name.isNotBlank()) lines += "[${m.name}]"
        if (m.recovery.isNotBlank()) lines += m.recovery
        if (m.fastboot.isNotBlank()) lines += m.fastboot
    }
    // 官方下载页（小米那套 xiaomifirmwareupdater 之类的入口）
    if (v.downloadPage.isNotBlank()) lines += "下载页：${v.downloadPage}"
    // 只有版本号、没有链接的话就别弹复制框了
    return if (lines.size > 1) lines.joinToString("\n") else ""
}

/** 官方直链丢进内置下载器（多线程 + 断点续传），不跳浏览器。 */
private fun downloadWithApp(
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    url: String,
    fileName: String,
    onGoDownloader: (() -> Unit)? = null,
) {
    scope.launch {
        val task = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                DownloadManager.add(
                    url = url,
                    fileName = fileName.ifBlank { guessFileName(url) },
                    subDir = "official",
                )
            }.getOrNull()
        }
        SnackbarController.show(
            if (task == null) "加入下载队列失败，换个镜像试试"
            else "已开始下载：${task.fileName.ifBlank { "ROM 包" }}"
        )
        // 成功入队就跳到「下载」段看进度，别让用户以为点了没反应
        if (task != null) onGoDownloader?.invoke()
    }
}

/** 用状态机的几个时间戳粗略判断当前处在什么阶段（与网站的判定口径一致）。 */
private fun versionState(v: RomVersion): String = when {
    v.goneAt != null -> "撤包"
    v.publicAt != null -> "公开"
    v.resumedAt != null -> "更新"
    else -> ""
}
