package org.linbaogu.romhub.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.cloud.CloudBrowserViewModel
import org.linbaogu.romhub.cloud.CloudState
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.pan.model.ShareFile
import org.linbaogu.romhub.pan.model.ShareInfo
import org.linbaogu.romhub.ui.common.GlobalSnackbarHost
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 云盘文件浏览页（7 家网盘共用）。
 *
 * 对齐云析的 `*CloudScreen`：面包屑、文件列表、搜索过滤、多选、
 * 下载 / 重命名 / 移动 / 删除 / 新建文件夹 / 批量分享、下拉刷新。
 *
 * 与云析的差异：那边每家一个屏幕（7 份几乎一样的代码），这里一份 UI 配 7 个适配器。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudBrowserScreen(
    platform: SharePlatform,
    bottomInnerPadding: Dp = 0.dp,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = remember(platform) { CloudBrowserViewModel(ctx, platform) }
    val cs = MiuixTheme.colorScheme

    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val selectionRev by vm.selectionRev.collectAsState()
    val multiSelect by vm.multiSelectMode.collectAsState()
    val downloadTriggered by vm.downloadTriggered.collectAsState()
    val pendingDownload by vm.downloadLink.collectAsState()
    val searchResult by vm.searchResult.collectAsState()
    val searching by vm.searching.collectAsState()
    val uploadProgress by vm.uploadProgress.collectAsState()

    // ── 上传本地文件：系统文件选择器（多选）→ 逐个读元数据 → 交给 VM 上传
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val uploads = uris.mapNotNull { uri -> resolveLocalUpload(ctx, uri) }
            if (uploads.isEmpty()) {
                SnackbarController.show("读不到所选文件")
            } else {
                vm.uploadFiles(uploads)
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ShareFile?>(null) }
    var showBatchActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<ShareInfo?>(null) }
    val listState = rememberLazyListState()

    // 操作结果统一走全局提示条
    LaunchedEffect(message) {
        message?.let {
            SnackbarController.show(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(downloadTriggered) {
        if (downloadTriggered > 0) {
            vm.consumeDownloadTriggered()
            onDownloadStarted()
        }
    }

    // 首次进入加载根目录
    LaunchedEffect(platform) { vm.loadRoot() }

    // 返回键：搜索中 → 退出搜索；多选 → 退出多选；子目录 → 上一级；根目录 → 退出页面
    BackHandler {
        when {
            showSearch -> {
                showSearch = false
                searchQuery = ""
                vm.clearSearch()
            }
            multiSelect -> vm.exitMultiSelect()
            searchResult != null -> vm.clearSearch()
            (state as? CloudState.Loaded)?.pathNames?.isNotEmpty() == true -> {
                scope.launch { vm.back() }
            }
            else -> onExit()
        }
    }

    val loaded = state as? CloudState.Loaded
    // 搜索结果优先于当前目录内容；两者都是 Loaded，用 isSearchResult 区分
    val active: CloudState.Loaded? = searchResult ?: loaded
    val inSearchResult = searchResult != null

    // 本地过滤：只在搜索结果之外生效（搜索结果本身已经是服务端按关键词给的）
    val displayFiles = remember(active?.files, searchQuery, selectionRev, inSearchResult) {
        val files = active?.files ?: emptyList()
        val q = searchQuery.trim()
        if (inSearchResult || q.isEmpty()) files
        else files.filter { it.fname.contains(q, ignoreCase = true) }
    }

    // 下载直链确认弹窗
    pendingDownload?.let { pending ->
        CloudDownloadLinkDialog(
            fileName = pending.file.fname,
            url = pending.url,
            onDownload = { vm.startDownload() },
            onDismiss = { vm.dismissDownloadDialog() },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CloudTopBar(
                title = PanHub.platformName(platform),
                subtitle = when {
                    searching -> "正在全盘搜索…"
                    inSearchResult -> "全盘搜索 · 命中 ${displayFiles.size} 项"
                    active == null -> "加载中…"
                    searchQuery.isBlank() -> "共 ${active.files.size} 项"
                    else -> "匹配 ${displayFiles.size} / ${active.files.size} 项"
                },
                multiSelect = multiSelect,
                selectedCount = vm.selectedFiles.size,
                allSelected = displayFiles.isNotEmpty() && vm.selectedFiles.size == displayFiles.size,
                showSearch = showSearch,
                onBack = {
                    when {
                        showSearch -> {
                            showSearch = false
                            searchQuery = ""
                            vm.clearSearch()
                        }
                        multiSelect -> vm.exitMultiSelect()
                        inSearchResult -> vm.clearSearch()
                        loaded?.pathNames?.isNotEmpty() == true -> scope.launch { vm.back() }
                        else -> onExit()
                    }
                },
                onToggleSearch = {
                    showSearch = !showSearch
                    if (!showSearch) {
                        searchQuery = ""
                        vm.clearSearch()
                    }
                },
                onToggleSelectAll = {
                    if (vm.selectedFiles.size == displayFiles.size) vm.clearSelection()
                    else vm.selectAll(displayFiles)
                },
                onAdd = { showAddMenu = true },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            // 搜索中：整页转圈（结果没回来之前保持旧列表会让人以为没搜）
            val bodyState: CloudState = if (searching) CloudState.Loading else (active ?: state)
            when (val s = bodyState) {
                is CloudState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(if (searching) "正在全盘搜索…" else "正在加载…", color = cs.onSurfaceVariantSummary)
                }

                is CloudState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            s.message,
                            color = cs.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("返回") { onExit() }
                            PrimaryButton("重试") { scope.launch { vm.loadRoot() } }
                        }
                    }
                }

                is CloudState.Loaded -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = inner.calculateTopPadding() + 8.dp,
                            bottom = inner.calculateBottomPadding() +
                                bottomInnerPadding + if (multiSelect) 84.dp else 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // 搜索框固定在列表顶部（之前放在 item 里会随滚动移出屏幕、失去焦点）
                        if (showSearch) {
                            item(key = "search") {
                                SearchField(
                                    value = searchQuery,
                                    onChange = { searchQuery = it },
                                    onSubmit = { scope.launch { vm.search(it) } },
                                )
                            }
                        }

                        // 搜索结果：给个提示条 + 一个「退出搜索」
                        if (s.isSearchResult) {
                            item(key = "searchBanner") {
                                SearchResultBanner(
                                    keyword = vm.currentKeyword,
                                    count = displayFiles.size,
                                    onExit = {
                                        searchQuery = ""
                                        vm.clearSearch()
                                    },
                                )
                            }
                        } else if (s.pathNames.isNotEmpty()) {
                            // 面包屑（搜索结果没有目录层级，不显示）
                            item(key = "crumbs") {
                                CrumbBar(
                                    names = s.pathNames,
                                    onJumpTo = { depth ->
                                        scope.launch {
                                            // 回退到第 depth 层（0 = 根）
                                            val steps = s.pathNames.size - depth
                                            repeat(steps.coerceAtLeast(0)) { vm.back() }
                                        }
                                    },
                                )
                            }
                        }

                        // 文件列表
                        if (displayFiles.isEmpty()) {
                            item(key = "empty") {
                                Hint(
                                    when {
                                        s.isSearchResult -> "没有找到匹配的文件"
                                        searchQuery.isNotBlank() -> "没有匹配的文件"
                                        else -> "这个文件夹是空的"
                                    }
                                )
                            }
                        } else {
                            items(displayFiles, key = { it.fid }) { f ->
                                CloudFileRow(
                                    file = f,
                                    selected = vm.isSelected(f.fid),
                                    multiSelect = multiSelect,
                                    // 搜索结果里的文件来自不同目录，副标题显示它的父目录便于定位
                                    subtitle = if (s.isSearchResult && !f.isdir) f.pdirFid else null,
                                    onClick = {
                                        when {
                                            multiSelect -> vm.toggleSelect(f)
                                            // 搜索结果里的目录不属于当前路径，要单独打开
                                            f.isdir && s.isSearchResult -> scope.launch { vm.openSearchDir(f) }
                                            f.isdir -> scope.launch { vm.enter(f) }
                                            else -> scope.launch { vm.prepareDownload(f) }
                                        }
                                    },
                                    onLongClick = {
                                        if (!multiSelect) vm.enterMultiSelect(f)
                                    },
                                    onDownload = { scope.launch { vm.prepareDownload(f) } },
                                    onMore = { vm.enterMultiSelect(f) },
                                )
                            }
                        }

                        item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
                    }

                    // 多选底部操作栏
                    if (multiSelect) {
                        MultiSelectBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomInnerPadding + 12.dp),
                            selectedCount = vm.selectedFiles.size,
                            onDownload = { scope.launch { vm.downloadFiles(vm.selectedFiles) } },
                            onDelete = { showDeleteConfirm = true },
                            onShare = { showBatchActions = true },
                        )
                    }
                }
            }

            GlobalSnackbarHost()

            // 上传进度覆盖层（上传期间挡住列表，避免用户在传一半时又点别的操作）
            if (uploadProgress >= 0) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(cs.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "正在上传… $uploadProgress%",
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            color = cs.onSurface,
                        )
                        Text(
                            "请保持应用在前台，上传完成前不要退出",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = cs.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }

    // 「+」菜单：新建文件夹 / 上传本地文件
    if (showAddMenu) {
        CloudAddMenu(
            onDismiss = { showAddMenu = false },
            onCreateFolder = {
                showAddMenu = false
                showCreateFolder = true
            },
            onUpload = if (vm.supportsUpload) {
                {
                    showAddMenu = false
                    uploadLauncher.launch(arrayOf("*/*"))
                }
            } else null,
        )
    }

    // 新建文件夹
    if (showCreateFolder) {
        CloudInputDialog(
            title = "新建文件夹",
            label = "文件夹名",
            initial = "",
            confirmText = "创建",
            onConfirm = { name ->
                showCreateFolder = false
                scope.launch { vm.createFolder(name) }
            },
            onDismiss = { showCreateFolder = false },
        )
    }

    // 重命名
    renameTarget?.let { t ->
        CloudInputDialog(
            title = "重命名",
            label = "新名称",
            initial = t.fname,
            confirmText = "保存",
            onConfirm = { name ->
                renameTarget = null
                scope.launch { vm.rename(t, name) }
            },
            onDismiss = { renameTarget = null },
        )
    }

    // 批量删除二次确认
    if (showDeleteConfirm) {
        CloudConfirmDialog(
            title = "删除 ${vm.selectedFiles.size} 项？",
            message = "删除后进入回收站（部分网盘）或直接删除，不可恢复。",
            confirmText = "删除",
            onConfirm = {
                showDeleteConfirm = false
                scope.launch { vm.delete(vm.selectedFiles) }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    // 批量操作面板（分享 / 移动）
    if (showBatchActions) {
        CloudBatchActionSheet(
            vm = vm,
            onDismiss = { showBatchActions = false },
            onShareCreated = {
                showBatchActions = false
                shareResult = it
            },
        )
    }

    // 分享结果
    shareResult?.let { info ->
        ShareResultDialog(info = info, onDismiss = { shareResult = null })
    }
}

// ---------------------------------------------------------------- 顶栏

@Composable
private fun CloudTopBar(
    title: String,
    subtitle: String,
    multiSelect: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    showSearch: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                if (multiSelect) MiuixIcons.Close else MiuixIcons.Back,
                contentDescription = "返回",
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                if (multiSelect) "已选 $selectedCount 项" else title,
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        if (multiSelect) {
            IconButton(onClick = onToggleSelectAll) {
                Icon(MiuixIcons.SelectAll, contentDescription = if (allSelected) "取消全选" else "全选")
            }
        } else {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    MiuixIcons.Search,
                    contentDescription = "搜索",
                    tint = if (showSearch) cs.primary else cs.onSurface,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(MiuixIcons.Add, contentDescription = "新建")
            }
        }
    }
}

// ---------------------------------------------------------------- 面包屑

@Composable
private fun CrumbBar(names: List<String>, onJumpTo: (Int) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "根目录",
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = cs.primary,
            modifier = Modifier.clickable { onJumpTo(0) },
        )
        names.forEachIndexed { i, name ->
            Text(
                " › ",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = cs.onSurfaceVariantSummary,
            )
            val last = i == names.lastIndex
            Text(
                name,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = if (last) cs.onSurface else cs.primary,
                fontWeight = if (last) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                modifier = if (last) Modifier else Modifier.clickable { onJumpTo(i + 1) },
            )
        }
    }
}

// ---------------------------------------------------------------- 搜索框

/**
 * 搜索框：回车（ImeAction.Search）触发**全盘搜索**。
 *
 * 之前这里的毛病：① 只做本地过滤，用户以为坏了；② 输入框被塞在 LazyColumn 的 item 里，
 * 一滚动就移出屏幕、焦点也丢 —— 现在提到顶部固定位置（见调用点）。
 */
@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isBlank()) {
            Text(
                "输入文件名，回车全盘搜索",
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
    // 打开搜索框就自动聚焦，省一次点击
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

// ---------------------------------------------------------------- 文件行

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudFileRow(
    file: ShareFile,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit,
    /** 可选的第三行（搜索结果里显示父目录，帮用户定位文件在哪） */
    subtitle: String? = null,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelect) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) cs.primary else cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", color = Color.White, fontSize = MiuixTheme.textStyles.footnote2.fontSize)
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            if (file.isdir) MiuixIcons.Folder else MiuixIcons.Download,
            contentDescription = null,
            tint = if (file.isdir) cs.primary else cs.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.fname,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (file.isdir) append("文件夹") else append(formatSize(file.fsize))
                    if (file.modifyTime.isNotBlank()) {
                        append("  ")
                        append(file.modifyTime.take(16))
                    }
                },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    "位置：${subtitle.ifBlank { "/" }}",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!multiSelect && !file.isdir) {
            IconButton(onClick = onDownload) {
                Icon(MiuixIcons.Download, contentDescription = "下载", tint = cs.primary)
            }
        }
        if (!multiSelect) {
            IconButton(onClick = onMore) {
                Icon(MiuixIcons.More, contentDescription = "更多")
            }
        }
    }
}

// ---------------------------------------------------------------- 本地上传辅助

/**
 * 把一个 `content://` Uri 解析成 [CloudBrowserViewModel.LocalUpload]：
 * 查文件名 + 大小（拿不到就算了，VM 里 size=-1 会退化成流式上传），
 * 输入流用工厂延迟打开 —— 重试 / 多线程时每次都要拿到新的流。
 */
private fun resolveLocalUpload(
    ctx: android.content.Context,
    uri: android.net.Uri,
): CloudBrowserViewModel.LocalUpload? {
    val name = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: return null

    val size = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else -1L
        }
    }.getOrNull() ?: -1L

    return CloudBrowserViewModel.LocalUpload(
        name = name,
        size = size,
        openStream = {
            ctx.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("无法打开 $name")
        },
    )
}

// ---------------------------------------------------------------- 搜索结果提示条

/** 搜索结果顶部提示：说清楚这是「全盘搜索」而不是当前目录，并给一个退出入口。 */
@Composable
private fun SearchResultBanner(keyword: String, count: Int, onExit: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.primary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "全盘搜索「$keyword」",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = cs.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "命中 $count 项（不限当前文件夹）",
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
            )
        }
        Text(
            "退出搜索",
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = cs.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onExit)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- 多选底部栏

@Composable
private fun MultiSelectBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "已选 $selectedCount 项",
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = cs.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("下载", icon = MiuixIcons.Download) { onDownload() }
                GhostButton("分享", icon = MiuixIcons.Share) { onShare() }
                GhostButton("删除", icon = MiuixIcons.Delete) { onDelete() }
            }
        }
    }
}

// ---------------------------------------------------------------- 下载直链确认

@Composable
private fun CloudDownloadLinkDialog(
    fileName: String,
    url: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val cs = MiuixTheme.colorScheme
    CloudDialogScaffold(title = "下载直链", onDismiss = onDismiss) {
        Text(fileName, fontSize = MiuixTheme.textStyles.body1.fontSize, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceContainerHigh)
                .padding(10.dp),
        ) {
            Text(
                url,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = cs.onSurfaceVariantSummary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("复制直链") {
                clipboard.setText(AnnotatedString(url))
                SnackbarController.show("已复制直链")
            }
            PrimaryButton("开始下载") { onDownload() }
        }
    }
}

// ---------------------------------------------------------------- 工具

/** 字节格式化（B/KB/MB/GB）。 */
internal fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
