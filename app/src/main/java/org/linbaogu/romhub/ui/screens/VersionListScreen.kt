package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.Mirror
import org.linbaogu.romhub.data.RomVersion
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.ListScreen
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
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
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
) {
    val cs = MiuixTheme.colorScheme
    val mirrors = remember(v) { mirrorsOf(v) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        HorizontalDivider(color = cs.dividerLine)
        Spacer(Modifier.height(8.dp))
        if (v.filenameRec.isNotBlank()) InfoRow("卡刷包名", v.filenameRec, mono = true)
        if (v.filenameFast.isNotBlank()) InfoRow("线刷包名", v.filenameFast, mono = true)
        if (v.filenameRec.isNotBlank() || v.filenameFast.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
        }
        mirrors.forEach { m -> MirrorBlock(m, onCopy, onOpen) }
    }
}

@Composable
private fun MirrorBlock(m: Mirror, onCopy: (String) -> Unit, onOpen: (String) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = m.name,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m.recovery.isNotBlank()) {
                GhostButton("下载卡刷包") { onOpen(m.recovery) }
                if (m.fastboot.isNotBlank()) {
                    GhostButton("复制") { onCopy(m.recovery) }
                }
            }
            if (m.fastboot.isNotBlank()) {
                GhostButton("下载线刷包") { onOpen(m.fastboot) }
            }
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

/** 用状态机的几个时间戳粗略判断当前处在什么阶段（与网站的判定口径一致）。 */
private fun versionState(v: RomVersion): String = when {
    v.goneAt != null -> "撤包"
    v.publicAt != null -> "公开"
    v.resumedAt != null -> "更新"
    else -> ""
}
