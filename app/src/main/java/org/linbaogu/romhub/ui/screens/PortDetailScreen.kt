package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.PortPackage
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.StateChip
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PortDetailScreen(
    id: Long,
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var item by remember { mutableStateOf<PortPackage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(id, reload) {
        loading = true
        error = ""
        runCatching { Api.port(ctx, id).item }
            .onSuccess { item = it; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    val p = item
    ListScreen(
        title = "移植包",
        largeTitle = "移植包详情",
        subtitle = p?.deviceName.orEmpty(),
        onBack = onBack,
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        if (loading) item { Hint("正在加载…") }
        if (error.isNotBlank()) item { ErrorHint(error) { reload++ } }

        if (p != null) {
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StateChip(state = "移植包")
                            if (p.portType.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                StateChip(state = "", textOverride = p.portType)
                            }
                            if (p.platformZh.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                StateChip(state = "", textOverride = p.platformZh)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = p.title.ifBlank { p.fileName.ifBlank { "未命名移植包" } },
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        InfoRow("作者", p.author)
                        InfoRow("来源", p.source)
                        InfoRow("机型", "${p.deviceName}（${p.codename}）")
                        InfoRow("版本", p.version)
                        InfoRow("文件名", p.fileName, mono = true)
                        InfoRow("大小", p.fileSize)
                        InfoRow("发布", p.publishedAt)
                        InfoRow("提交", p.submittedBy)
                    }
                }
            }

            if (p.notice.isNotBlank()) {
                item {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "作者公告",
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                p.notice,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            item {
                PrimaryButton("打开网盘链接") { openUrl(ctx, p.shareUrl) }
            }
            item {
                GhostButton("复制分享链接") {
                    clipboard.setText(AnnotatedString(p.shareUrl))
                }
            }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "刷机提示",
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "移植包由社区作者制作，与小米官方无关。刷入前请完整备份数据，" +
                                    "务必先看清楚作者的公告与适配机型；刷机有风险，后果自负。",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
