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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.PortPackage
import org.linbaogu.romhub.download.DownloadEntry
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.NoticeRichText
import org.linbaogu.romhub.ui.common.SnackbarController
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
    /** 点「用下载器下载」后跳到「网盘下载器 → 下载」段，并把分享链接一起带过去（可选） */
    onGoDownloader: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<PortPackage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    // 「用下载器下载」在途状态：解析分享链接要几秒，给按钮一个视觉反馈，防重复点击
    var starting by remember { mutableStateOf(false) }

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
                            // 公告可能是「文字 + 下载链接 + 截图」，走富文本渲染：
                            // 链接蓝色下划线可点开浏览器、图片直接显示、整段可复制
                            NoticeRichText(
                                text = p.notice,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                PrimaryButton(if (starting) "正在开始下载…" else "用下载器下载") {
                    if (starting) return@PrimaryButton
                    starting = true
                    // 这就是「点分享链接直接进下载器」：把网盘分享链接丢给统一入口，
                    // 只有一个文件就自动开始下，多个文件会把选择权交回「下载」段。
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { DownloadEntry.start(ctx, p.shareUrl, null) }
                                .getOrElse { DownloadEntry.EntryResult.Failed(it.message ?: "出错了") }
                        }
                        starting = false
                        val msg = when (r) {
                            is DownloadEntry.EntryResult.Started -> "已开始下载：${r.task.fileName}"
                            is DownloadEntry.EntryResult.ChooseFiles -> "分享里有 ${r.files.size} 个文件，去「下载」段选"
                            is DownloadEntry.EntryResult.NeedLogin -> "需要先登录${PanHub.platformName(r.platform)}（已跳到账号段）"
                            is DownloadEntry.EntryResult.NeedPassword -> "这个分享需要提取码，去「下载」段填入"
                            is DownloadEntry.EntryResult.GitHubRepo -> "这是 GitHub 仓库，去「下载」段打开仓库挑文件"
                            is DownloadEntry.EntryResult.Failed -> r.message
                        }
                        SnackbarController.show(msg)

                        // 跳转规则：**不管什么结果都跳到「网盘下载器」**，让用户直接看到进度/选择。
                        //   · 已开始   → 「下载」段看进度（不带链接，避免重复入队）
                        //   · 未登录   → 「账号」段去登录
                        //   · 多文件 / 要提取码 → 「下载」段并预填链接，用户直接选/填
                        // 注意：本页是栈里的深层页面，切 tab 会整栈替换掉它，所以必须先开始下载再跳。
                        when (r) {
                            is DownloadEntry.EntryResult.Started -> onGoDownloader?.invoke("")
                            is DownloadEntry.EntryResult.NeedLogin -> onGoDownloader?.invoke("")
                            else -> onGoDownloader?.invoke(p.shareUrl)
                        }
                    }
                }
            }
            item {
                GhostButton("打开网盘链接") { openUrl(ctx, p.shareUrl) }
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
