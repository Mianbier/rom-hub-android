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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.download.DownloadEntry
import org.linbaogu.romhub.pan.ShareLinkParser
import org.linbaogu.romhub.pan.store.BookmarkEntity
import org.linbaogu.romhub.pan.store.BookmarkStore
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.login.MiuixDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网盘链接收藏页。
 *
 * 保存下来的分享链接可以在这里：
 *   · 分类筛选（视频 / 文档 / 软件…，也能自定义）
 *   · 一键丢给下载器解析
 *   · 固定到首页快捷方式（带自定义标签）
 *
 * 功能对齐云析的收藏页，界面换成 Miuix 风格。
 */
@Composable
fun BookmarkScreen(
    store: BookmarkStore,
    bottomInnerPadding: Dp = 0.dp,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val all by store.all.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<BookmarkEntity?>(null) }
    var labelTarget by remember { mutableStateOf<BookmarkEntity?>(null) }
    var categoryTarget by remember { mutableStateOf<BookmarkEntity?>(null) }

    val categories = remember(all) { store.categories() }
    val filtered = remember(all, selectedCategory) {
        val c = selectedCategory
        if (c == null) all else all.filter { it.category == c }
    }

    ListScreen(
        title = "收藏",
        subtitle = if (all.isEmpty()) "收藏的网盘链接会出现在这里" else "共 ${all.size} 条",
        bottomInnerPadding = bottomInnerPadding,
        actions = {
            GhostButton("添加") { showAdd = true }
        },
    ) {
        // ------------------------------------------------ 分类筛选
        if (categories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip("全部", selectedCategory == null) { selectedCategory = null }
                    }
                    items(categories) { c ->
                        FilterChip(c, selectedCategory == c) {
                            selectedCategory = if (selectedCategory == c) null else c
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ 列表
        if (filtered.isEmpty()) {
            item {
                Hint(
                    if (all.isEmpty()) "还没有收藏 —— 点右上角「添加」，或者先收藏一条分享链接"
                    else "这个分类下没有内容"
                )
            }
        } else {
            items(filtered, key = { it.id }) { bm ->
                BookmarkRow(
                    bookmark = bm,
                    onDownload = {
                        scope.launch {
                            val r = DownloadEntry.start(ctx, bm.link, bm.pwd.ifBlank { null })
                            SnackbarController.show(
                                when (r) {
                                    is DownloadEntry.EntryResult.Started -> "已开始下载：${r.task.fileName}"
                                    is DownloadEntry.EntryResult.NeedLogin -> "需要先登录对应的网盘账号"
                                    is DownloadEntry.EntryResult.NeedPassword -> "需要提取码，去「下载」页填入"
                                    is DownloadEntry.EntryResult.ChooseFiles -> "分享里有多个文件，去「下载」页选择"
                                    is DownloadEntry.EntryResult.GitHubRepo -> "这是 GitHub 仓库，去「下载」页打开仓库挑文件"
                                    is DownloadEntry.EntryResult.Failed -> r.message
                                }
                            )
                        }
                    },
                    onCopy = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(bm.link))
                        SnackbarController.show("链接已复制")
                    },
                    onMenu = { menuTarget = bm },
                )
            }
        }
    }

    // ------------------------------------------------ 条目菜单
    menuTarget?.let { bm ->
        MiuixDialog(
            title = bm.title.ifBlank { bm.link.take(40) },
            message = buildString {
                append("分类：${bm.category}")
                if (bm.pwd.isNotBlank()) append("\n提取码：${bm.pwd}")
                if (bm.homePinned) append("\n已固定到首页快捷方式")
            },
            confirmText = "关闭",
            onConfirm = { menuTarget = null },
            onDismiss = { menuTarget = null },
            dismissText = "删除",
            onDismissButton = {
                store.delete(bm.id)
                menuTarget = null
                SnackbarController.show("已删除")
            },
        )
    }

    // ------------------------------------------------ 添加
    if (showAdd) {
        BookmarkEditor(
            title = "添加收藏",
            onDismiss = { showAdd = false },
            onSave = { link, name, pwd, category ->
                if (link.isBlank()) {
                    SnackbarController.show("链接不能为空")
                    return@BookmarkEditor
                }
                if (store.findByLink(link) != null) {
                    SnackbarController.show("这条链接已经收藏过了")
                    return@BookmarkEditor
                }
                val platform = ShareLinkParser.parse(link)?.platform?.name.orEmpty()
                store.insert(
                    BookmarkEntity(
                        link = link.trim(),
                        title = name.trim(),
                        pwd = pwd.trim(),
                        platform = platform,
                        category = category,
                    )
                )
                showAdd = false
                SnackbarController.show("已收藏")
            },
        )
    }
}

// ---------------------------------------------------------------- 单条

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onMenu: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(Modifier.padding(vertical = 2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                bookmark.title.ifBlank { bookmark.link },
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (bookmark.title.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    bookmark.link,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bookmark.category.isNotBlank()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(cs.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            bookmark.category,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = cs.primary,
                        )
                    }
                }
                if (bookmark.pwd.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "码 ${bookmark.pwd}",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = cs.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("下载") { onDownload() }
                GhostButton("复制") { onCopy() }
                GhostButton("更多") { onMenu() }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) cs.primary else cs.onSurfaceVariantSummary,
        )
    }
}

// ---------------------------------------------------------------- 编辑弹窗

@Composable
private fun BookmarkEditor(
    title: String,
    initial: BookmarkEntity? = null,
    onDismiss: () -> Unit,
    onSave: (link: String, name: String, pwd: String, category: String) -> Unit,
) {
    var link by remember { mutableStateOf(initial?.link.orEmpty()) }
    var name by remember { mutableStateOf(initial?.title.orEmpty()) }
    var pwd by remember { mutableStateOf(initial?.pwd.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: BookmarkEntity.DEFAULT_CATEGORY) }
    var custom by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    title,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                Field("分享链接", link, { link = it }, lines = 2)
                Spacer(Modifier.height(10.dp))
                Field("备注名称（可空）", name, { name = it })
                Spacer(Modifier.height(10.dp))
                Field("提取码（可空）", pwd, { pwd = it })
                Spacer(Modifier.height(12.dp))

                Text(
                    "分类",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BookmarkEntity.PRESET_CATEGORIES) { c ->
                        FilterChip(c, category == c) {
                            category = c
                            custom = ""
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field("自定义分类（填了就优先用它）", custom, { custom = it })

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("取消") { onDismiss() }
                    Spacer(Modifier.weight(1f))
                    GhostButton("保存") {
                        onSave(link, name, pwd, custom.ifBlank { category })
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    lines: Int = 1,
) {
    Column {
        Text(
            label,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 2.dp, bottom = 5.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (lines > 1) 62.dp else 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    "请输入",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                maxLines = lines,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
