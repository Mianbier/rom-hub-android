package org.linbaogu.romhub.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.data.RomUpdate
import org.linbaogu.romhub.ui.common.Chip
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.StateChip
import org.linbaogu.romhub.ui.common.kindLabel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val STATES = listOf("全部", "公开", "内测", "Beta", "撤包")

@Composable
fun FeedScreen(
    subtitle: String,
    bottomInnerPadding: Dp,
    onSeen: () -> Unit,
    onOpenVersion: (RomUpdate) -> Unit,
) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(Repo.cachedFeed(ctx).items) }
    var counts by remember { mutableStateOf(emptyMap<String, Int>()) }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var error by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("全部") }
    var onlyCN by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(state, reload) {
        loading = items.isEmpty()
        error = ""
        runCatching {
            val resp = if (state == "全部") {
                Api.romUpdates(ctx, limit = 120)
            } else {
                Api.romUpdates(ctx, limit = 120, state = state)
            }
            counts = resp.counts
            // 「全部」时把结果合并进本地快照，保持动态页的离线数据是最新的
            if (state == "全部") Repo.refreshFeed(ctx, limit = 120).items else resp.items
        }.onSuccess {
            items = it
            loading = false
        }.onFailure {
            error = it.message ?: "加载失败"
            loading = false
        }
    }

    // 看过了就清小红点
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) onSeen()
    }

    val shown = remember(items, onlyCN) {
        if (onlyCN) items.filter { it.region == "cn" } else items
    }

    ListScreen(
        title = "动态",
        largeTitle = "动态",
        subtitle = subtitle,
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
        actions = {
            IconButton(onClick = { reload++ }) {
                Icon(MiuixIcons.Refresh, contentDescription = "刷新")
            }
        },
        bottomContent = {
            Column(Modifier.padding(bottom = 6.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    items(STATES) { s ->
                        val c = counts[s]
                        val label = if (s == "全部" || c == null) s else "$s（$c）"
                        Chip(text = label, selected = s == state, onClick = { state = s })
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    item {
                        Chip(text = "只看国行", selected = onlyCN, onClick = { onlyCN = true })
                    }
                    item {
                        Chip(text = "全部地区", selected = !onlyCN, onClick = { onlyCN = false })
                    }
                }
            }
        },
    ) {
        if (loading) item { Hint("正在加载动态…") }
        if (error.isNotBlank()) item { ErrorHint(error) { reload++ } }
        if (!loading && error.isBlank() && shown.isEmpty()) {
            item { Hint("这个筛选下暂无动态") }
        }

        var lastDay = ""
        shown.forEach { u ->
            val day = u.shortDate.substringBefore(' ').ifBlank { u.detectedAt.take(10) }
            if (day != lastDay) {
                lastDay = day
                item(key = "day_${day}_${u.id}") { SectionLabel(day) }
            }
            item(key = u.id) {
                FeedCard(u) { onOpenVersion(u) }
            }
        }
    }
}

@Composable
private fun FeedCard(u: RomUpdate, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    val label = kindLabel(u.kind, u.kindZh, u.state)
    Card(onClick = onClick, showIndication = true) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = u.deviceName.ifBlank { u.codename },
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                StateChip(state = u.state.ifBlank { label }, textOverride = label)
                if (u.kind == "port") {
                    Spacer(Modifier.width(4.dp))
                    StateChip(state = "移植包")
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = u.newVersion.ifBlank { u.oldVersion },
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = cs.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = listOfNotNull(
                        u.regionZh.takeIf { it.isNotBlank() },
                        u.branchZh.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                )
            }

            if (u.desc.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = u.desc,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (u.stateText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = u.stateText,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = u.shortDate,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
            )
        }
    }
}
