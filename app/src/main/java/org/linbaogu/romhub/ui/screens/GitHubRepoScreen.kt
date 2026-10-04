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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.pan.GitHubApi
import org.linbaogu.romhub.pan.GitHubAsset
import org.linbaogu.romhub.pan.GitHubLinkParser
import org.linbaogu.romhub.pan.GitHubLinkType
import org.linbaogu.romhub.pan.GitHubRelease
import org.linbaogu.romhub.pan.GitHubRepo
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * GitHub 仓库页：README 渲染 + Release 资产列表 + 一键走下载器。
 *
 * 这是「云析 GitHub 功能」的落点 —— 在下载页粘一条 GitHub 链接，识别出是仓库就跳到这里；
 * 识别出是文件直链就直接进下载队列（那个分支在 [org.linbaogu.romhub.download.DownloadEntry]）。
 *
 * README 用自己的 [SimpleMarkdown] 渲染，不引第三方 markdown 库（理由见那个文件的注释）。
 */
@Composable
fun GitHubRepoScreen(
    owner: String,
    repo: String,
    bottomInnerPadding: Dp = 0.dp,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val api = remember { GitHubApi() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<GitHubRepo?>(null) }
    var readme by remember { mutableStateOf("") }
    var releases by remember { mutableStateOf<List<GitHubRelease>>(emptyList()) }
    var expandedRelease by remember { mutableStateOf<Int?>(null) }

    suspend fun load() {
        loading = true
        error = ""
        withContext(Dispatchers.IO) {
            val r = runCatching { api.getRepo(owner, repo) }.getOrNull()
            if (r == null) {
                error = "打不开这个仓库：${owner}/${repo}\n可能是私有仓库，或者网络不通。"
                loading = false
                return@withContext
            }
            info = r
            readme = runCatching { api.getReadme(owner, repo, r.defaultBranch).orEmpty() }
                .getOrDefault("")
            releases = runCatching { api.getReleases(owner, repo) }.getOrNull().orEmpty()
        }
        loading = false
    }

    LaunchedEffect(owner, repo) { load() }

    ListScreen(
        title = repo,
        subtitle = if (loading) "加载中…" else "$owner/${repo}",
        bottomInnerPadding = bottomInnerPadding,
    ) {
        if (loading) {
            item { Hint("正在读取仓库信息…") }
            return@ListScreen
        }
        if (error.isNotBlank()) {
            item { ErrorHint(error, onRetry = { scope.launch { load() } }) }
            return@ListScreen
        }

        // ------------------------------------------------ 仓库概览
        info?.let { r ->
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "${r.owner}/${r.name}",
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        val desc = r.description.orEmpty()
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                desc,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val lang = r.language.orEmpty()
                            if (lang.isNotBlank()) Tag(lang)
                            if (r.stars > 0) Tag("★ ${formatCount(r.stars)}")
                            if (r.forks > 0) Tag("⚡ ${formatCount(r.forks)}")
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ Release 资产
        if (releases.isNotEmpty()) {
            item { SectionLabel("Release（点资产直接下载）") }
            items(releases.size) { idx ->
                val rel = releases[idx]
                val expanded = expandedRelease == idx
                Card(Modifier.padding(vertical = 2.dp)) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expandedRelease = if (expanded) null else idx }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        rel.tagName.ifBlank { rel.name.orEmpty() },
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (rel.prerelease) {
                                        Spacer(Modifier.width(6.dp))
                                        Tag("预发布", warn = true)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                val published = rel.publishedAt.orEmpty().take(10)
                                Text(
                                    "${rel.assets.size} 个文件" +
                                        (if (published.isNotBlank()) " · $published" else ""),
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            GhostButton(if (expanded) "收起" else "展开") {
                                expandedRelease = if (expanded) null else idx
                            }
                        }
                        if (expanded) {
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                            rel.assets.forEachIndexed { ai, asset ->
                                AssetRow(asset) {
                                    // 直接丢给下载器
                                    val task = DownloadManager.add(
                                        url = asset.downloadUrl,
                                        fileName = asset.name,
                                    )
                                    SnackbarController.show("已开始下载：${task.fileName}")
                                }
                                if (ai != rel.assets.lastIndex) {
                                    HcDivider(startIndent = 14.dp)
                                }
                            }
                            if (rel.assets.isEmpty()) {
                                Box(Modifier.padding(14.dp)) {
                                    Text(
                                        "这个 Release 没有可下载的文件",
                                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ README
        if (readme.isNotBlank()) {
            item { SectionLabel("README") }
            item {
                Card {
                    Box(Modifier.padding(14.dp)) {
                        SimpleMarkdown(readme)
                    }
                }
            }
        } else if (releases.isEmpty()) {
            item { Hint("这个仓库既没有 Release，也没有 README") }
        }
    }
}

// ---------------------------------------------------------------- 资产行

@Composable
private fun AssetRow(asset: GitHubAsset, onDownload: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                asset.name,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (asset.size > 0) append(org.linbaogu.romhub.download.formatBytes(asset.size))
                    if (asset.downloadCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${formatCount(asset.downloadCount)} 次下载")
                    }
                }.ifBlank { " " },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(8.dp))
        GhostButton("下载") { onDownload() }
    }
}

@Composable
private fun Tag(text: String, warn: Boolean = false) {
    val cs = MiuixTheme.colorScheme
    val color = if (warn) Color(0xFFE08A16) else cs.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = color)
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
    else -> n.toString()
}

/**
 * 从一段文本里识别 GitHub 仓库链接，识别到就返回 (owner, repo)。
 * 给下载页调用 —— 判断该不该跳这个页面。
 */
fun githubRepoOf(text: String): Pair<String, String>? =
    when (val t = GitHubLinkParser.parse(text)) {
        is GitHubLinkType.Repository -> t.owner to t.repo
        else -> null
    }
