package org.linbaogu.romhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.StoragePermission
import org.linbaogu.romhub.download.ChunkDownloader
import org.linbaogu.romhub.download.DownloadEntry
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.download.DownloadTask
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.download.formatSpeed
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.ui.common.Chip
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.login.MiuixDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载页。
 *
 * ## 版式（重做过两轮）
 *
 * 第一版把所有东西堆在一页，反馈「太乱」；第二版收敛成三段，但输入区要
 * 点一下才展开 —— 结果最常用的「粘贴一条链接」反而要多点一次。
 *
 * 现在定成**常驻单行链接条**：输入框永远在最上面，右侧三个圆形按钮
 * （粘贴 / 开始 / 更多）。不做折叠，一眼就知道往哪儿粘。
 *
 * 结构：
 *
 *   1. **链接条** —— 常驻。多行粘贴自动识别成批量（逐行一条）。
 *   2. **状态分段** —— 全部 / 下载中 / 已完成 / 失败，带数量角标。
 *   3. **任务列表** —— 主体。行内三栏：文件名 / 状态+进度 / 速度+百分比。
 *   4. **底部操作栏** —— 全暂停 / 全继续 / 队列 / 清空已完成 / 打开目录。
 *
 * ## 链接的三种走法（由 [DownloadEntry] 判定）
 *   · 普通直链（ROM 官方包 / GitHub / 任意 http）→ 立刻进队列开始下
 *   · 网盘分享且只有一个文件 → 自动解析直链，也是立刻开始下
 *   · 网盘分享有多个文件 → 就地列出文件，点哪个下哪个
 */
@Composable
fun DownloadScreen(
    bottomInnerPadding: Dp = 0.dp,
    /** 从外部点进来的链接：浏览器里点网盘分享选「用 ROM Hub 打开」，或 romhub://download?url= */
    initialUrl: String? = null,
    onConsumed: () -> Unit = {},
    /**
     * 跟着 [initialUrl] 一起带过来的提取码。
     *
     * 用途：更新弹窗里的网盘镜像 —— 站长在后台配了「夸克 + 提取码 abcd」，
     * 用户点一下就该直接开始解析，而不是停在「这个分享需要提取码」让他自己找。
     * 链接里本来就带 ?pwd= 的不用这个（解析器自己会读）。
     */
    initialPwd: String? = null,
    /** 解析时发现这家网盘没登录 —— 由外层切到「账号」段去登录 */
    onNeedLogin: ((SharePlatform) -> Unit)? = null,
    /** 点「下载设置」进详细设置页 */
    onOpenSettings: (() -> Unit)? = null,
    /** 解析时发现是 GitHub 仓库页 —— 由外层打开仓库详情（不在本页内嵌，避免嵌套滚动） */
    onOpenGitHubRepo: ((String, String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var messageIsError by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<DownloadEntry.EntryResult.ChooseFiles?>(null) }
    // 「浏览文件」的目录导航：栈里每层是 (目录 fid, 目录名)；空栈 = 停在 choices 的根目录
    var dirStack by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    // 当前目录列出来的条目（进子目录后替换；根目录直接用 choices.files）
    var dirEntries by remember { mutableStateOf<List<org.linbaogu.romhub.pan.model.ShareFile>>(emptyList()) }
    var dirLoading by remember { mutableStateOf(false) }
    var needPwdFor by remember { mutableStateOf<String?>(null) }
    // 取消/删除任务时要问一句：文件留着还是清掉（用户明确要求可自选）
    var confirmRemove by remember { mutableStateOf<DownloadTask?>(null) }
    // 点了任务卡片上的「N 线程」→ 弹出这个任务的线程数选择
    var threadPick by remember { mutableStateOf<DownloadTask?>(null) }
    // 校验详情弹窗（显示 APK 签名 / CRC 结果）
    var verifyDetail by remember { mutableStateOf<DownloadTask?>(null) }
    // 点开某条任务 → 进属性页
    var detail by remember { mutableStateOf<DownloadTask?>(null) }
    // 状态分段：0 全部 / 1 下载中 / 2 已完成 / 3 失败
    var filter by remember { mutableIntStateOf(0) }
    // 「更多」面板（批量粘贴 / 排队 / 设置入口）
    var showMore by remember { mutableStateOf(false) }
    // 提交时的偏好快照：高级选项里改过就用它，否则用全局
    var advanced by remember { mutableStateOf(false) }
    var optThreads by remember { mutableIntStateOf(Prefs.downloadThreads(ctx)) }
    var optQueue by remember { mutableStateOf(false) }

    val tasks by DownloadManager.tasks.collectAsState()
    val speeds by DownloadManager.speeds.collectAsState()

    fun note(text: String, isError: Boolean = false) {
        message = text
        messageIsError = isError
    }

    fun clearInput() {
        input = ""
        pwd = ""
        choices = null
        dirStack = emptyList()
        dirEntries = emptyList()
        needPwdFor = null
        advanced = false
        note("")
    }

    /**
     * 提交一条链接。
     *
     * 多行文本会被 [runBatch] 拦在前面处理，走到这里的都是单条。
     */
    fun run(text: String, password: String?, browseOnly: Boolean = false) {
        if (text.isBlank()) return
        busy = true
        note("")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DownloadEntry.start(ctx, text, password, browseOnly) }
                    .getOrElse { DownloadEntry.EntryResult.Failed(it.message ?: "出错了") }
            }
            busy = false
            when (result) {
                is DownloadEntry.EntryResult.Started -> {
                    // 高级选项：用户在展开区改了线程 / 勾了排队，落到这条任务上
                    if (advanced) {
                        DownloadManager.setThreads(result.task.id, optThreads)
                        if (optQueue) DownloadManager.startQueuedNow(result.task.id).let {
                            result.task.queued = true
                            result.task.queueOrder = Int.MAX_VALUE
                        }
                    }
                    clearInput()
                    val name = result.task.fileName.ifBlank { result.task.url.take(60) }
                    // 「仅 Wi-Fi 下载」开着 + 当前不是 Wi-Fi 时，任务会被闸门拦下。
                    // 这种情况必须**当场告诉用户**，而不是让他对着一条不动的任务干等 ——
                    // 老版本就是什么都不说，用户以为是网络问题 / 软件坏了。
                    val held = result.task.holdReason
                    if (held.isNotBlank()) {
                        note("$name 已加入，但$held", isError = true)
                    } else {
                        SnackbarController.show("已开始下载：$name")
                    }
                }

                is DownloadEntry.EntryResult.ChooseFiles -> {
                    choices = result
                    dirStack = emptyList()
                    dirEntries = emptyList()
                    val dirs = result.files.count { it.isdir }
                    note(
                        if (dirs > 0) "这个分享里有 $dirs 个文件夹，点进浏览找文件"
                        else "这个分享里有 ${result.files.size} 个文件，选一个下",
                    )
                }

                is DownloadEntry.EntryResult.NeedLogin -> {
                    needPwdFor = null
                    note(
                        "${PanHub.platformName(result.platform)} 还没登录 —— " +
                                "点「去登录」登上账号，再回来点一次。",
                        isError = true,
                    )
                    onNeedLogin?.invoke(result.platform)
                }

                is DownloadEntry.EntryResult.NeedPassword -> {
                    needPwdFor = result.platform.name
                    note(
                        "${PanHub.platformName(result.platform)} 需要提取码，请填下面的输入框" +
                            if (result.hint.isNotBlank() && !result.hint.contains("提取码"))
                                "（${result.hint}）" else "",
                        isError = true,
                    )
                }

                is DownloadEntry.EntryResult.GitHubRepo -> {
                    clearInput()
                    onOpenGitHubRepo?.invoke(result.owner, result.repo)
                }

                is DownloadEntry.EntryResult.Failed -> note(result.message, isError = true)
            }
        }
    }

    /**
     * 批量提交：把多行文本里每一条 http(s) 链接都入队。
     *
     * 为什么不用「一个分享里多个文件」那套：这里是**多条独立的链接**，
     * 各自指向不同的资源，所以逐条解析即可，解析失败的那几条单独报出来，
     * 不影响成功的。
     */
    fun runBatch(text: String) {
        val urls = text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .toList()
        if (urls.isEmpty()) {
            note("没找到 http/https 链接，检查一下粘进来的文本", isError = true)
            return
        }
        busy = true
        note("")
        scope.launch {
            var ok = 0
            val failed = mutableListOf<String>()
            for (u in urls) {
                val r = withContext(Dispatchers.IO) {
                    runCatching { DownloadEntry.start(ctx, u, null, false) }
                        .getOrElse { DownloadEntry.EntryResult.Failed(it.message ?: "出错了") }
                }
                if (r is DownloadEntry.EntryResult.Started) {
                    // 批量一律进队列，避免一次把带宽挤爆
                    r.task.queued = true
                    r.task.queueOrder = DownloadManager.queue().size + 1
                    ok++
                } else {
                    failed += u.take(48)
                }
            }
            DownloadManager.startQueue()
            busy = false
            clearInput()
            note(
                if (failed.isEmpty()) "已批量加入 $ok 条并开始下载"
                else "成功 $ok 条，失败 ${failed.size} 条：${failed.joinToString("、")}",
                isError = failed.isNotEmpty(),
            )
        }
    }

    // 外部点进来的链接：进来就自动开始下载（这就是「点链接直接下」）
    LaunchedEffect(initialUrl) {
        val u = initialUrl
        if (!u.isNullOrBlank()) {
            input = u
            // 提取码一起带过来：预填输入框，用户能看见也能改
            initialPwd?.takeIf { it.isNotBlank() }?.let { pwd = it }
            onConsumed()
            run(u, initialPwd?.takeIf { it.isNotBlank() })
        }
    }

    val activeCount = tasks.count { it.isActive }
    val running = tasks.filter { !it.isDone && !it.isFailed }
    val finished = tasks.filter { it.isDone }
    val failedList = tasks.filter { it.isFailed }

    // 分段统计（未完成里再拆出「排队等待」的）
    val segAll = tasks.size
    val segActive = running.size
    val segDone = finished.size
    val segFailed = failedList.size

    val shown = when (filter) {
        1 -> running
        2 -> finished
        3 -> failedList
        else -> tasks
    }

    ListScreen(
        title = "下载",
        subtitle = when {
            activeCount > 0 -> "正在下载 $activeCount 个"
            DownloadManager.queue().isNotEmpty() -> "队列里还有 ${DownloadManager.queue().size} 个"
            else -> "粘贴链接即可开始下载"
        },
        bottomInnerPadding = bottomInnerPadding,
        actions = {
            IconButton(onClick = { onOpenSettings?.invoke() }) {
                Icon(MiuixIcons.Settings, contentDescription = "下载设置")
            }
        },
    ) {
        // ------------------------------------------------ 常驻链接条
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                        ) {
                            if (input.isBlank()) {
                                Text(
                                    "粘贴下载链接",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it },
                                textStyle = MiuixTheme.textStyles.body2.copy(
                                    color = MiuixTheme.colorScheme.onSurface,
                                ),
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // 粘贴
                        RoundIcon("⎘") {
                            input = clipboard.getText()?.text.orEmpty()
                            note("")
                        }
                        Spacer(Modifier.width(6.dp))
                        // 开始 / 批量
                        val multi = input.lineSequence()
                            .count { it.trim().startsWith("http") } > 1
                        RoundIcon(
                            if (busy) "…" else "▶",
                            tint = MiuixTheme.colorScheme.primary,
                        ) {
                            if (busy) return@RoundIcon
                            if (multi) runBatch(input) else run(input, pwd.ifBlank { null })
                        }
                        Spacer(Modifier.width(6.dp))
                        RoundIcon("⋯") { showMore = !showMore }
                    }

                    // 提取码（网盘需要时）
                    if (needPwdFor != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            if (pwd.isBlank()) {
                                Text(
                                    "提取码",
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            BasicTextField(
                                value = pwd,
                                onValueChange = { pwd = it.take(8) },
                                singleLine = true,
                                textStyle = MiuixTheme.textStyles.body2.copy(
                                    color = MiuixTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = if (messageIsError) MiuixTheme.colorScheme.error
                            else MiuixTheme.colorScheme.primary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 「更多」展开区：实用动作 + 高级选项
                    AnimatedVisibility(visible = showMore) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GhostButton("浏览文件") {
                                    if (!busy) run(input, pwd.ifBlank { null }, browseOnly = true)
                                }
                                GhostButton("清空") { clearInput() }
                                GhostButton(if (advanced) "收起选项" else "高级选项") {
                                    advanced = !advanced
                                }
                            }

                            AnimatedVisibility(visible = advanced) {
                                Column {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "分片线程数",
                                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(1, 4, 8, 16, 32, 64).forEach { n ->
                                            Chip(
                                                text = n.toString(),
                                                selected = optThreads == n,
                                            ) { optThreads = n }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { optQueue = !optQueue },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        MiniSwitch(optQueue)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "加入队列",
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = MiuixTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                "先不下载，等你在队列里点开始",
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
            }
        }

        // ------------------------------------------------ 状态分段
        if (tasks.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("全部 $segAll", filter == 0) { filter = 0 }
                    Chip("下载中 $segActive", filter == 1) { filter = 1 }
                    Chip("已完成 $segDone", filter == 2) { filter = 2 }
                    if (segFailed > 0) Chip("失败 $segFailed", filter == 3) { filter = 3 }
                }
            }
        }

        // ------------------------------------------------ 多文件选择（支持进子文件夹）
        choices?.let { c ->
            // 当前目录的条目：栈空 = 根目录（用 choices.files），否则用 dirEntries
            val cur = if (dirStack.isEmpty()) c.files else dirEntries
            val dirName = dirStack.lastOrNull()?.second

            item { SectionLabel(dirName?.let { "浏览：$it" } ?: "选择要下载的文件") }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 进了子目录就给「返回上级」，逐层退回去
                    if (dirStack.isNotEmpty()) {
                        GhostButton("返回上级") {
                            dirStack = dirStack.dropLast(1)
                            if (dirStack.isEmpty()) {
                                dirEntries = emptyList()
                            } else {
                                dirLoading = true
                                scope.launch {
                                    val (fid, _) = dirStack.last()
                                    val r = withContext(Dispatchers.IO) {
                                        PanHub.listShareFiles(ctx, c.platform, c.session, fid, c.credential)
                                    }
                                    dirLoading = false
                                    r.fold(
                                        onSuccess = { dirEntries = it },
                                        onFailure = { note(it.message ?: "读取目录失败", isError = true) },
                                    )
                                }
                            }
                            note("")
                        }
                    }
                    GhostButton("清空选择") {
                        choices = null
                        dirStack = emptyList()
                        dirEntries = emptyList()
                        note("")
                    }
                }
            }

            // 根目录才提供「全部存网盘」（子目录里只挑单个下）
            if (dirStack.isEmpty()) {
                item {
                    GhostButton("全部保存到我的网盘") {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                PanHub.saveManyToMyDrive(
                                    ctx, c.platform, c.session, c.files.filter { !it.isdir }, c.credential,
                                )
                            }
                            busy = false
                            r.fold(
                                onSuccess = { (_, results) ->
                                    val ok = results.count { it.isSuccess }
                                    note(
                                        if (ok == results.size) "已全部转存到你的网盘（${PanHub.platformName(c.platform)}）"
                                        else "转存完成 $ok/${results.size} 个，失败的可以单个重试",
                                        isError = ok != results.size,
                                    )
                                },
                                onFailure = { note(it.message ?: "转存失败", isError = true) },
                            )
                        }
                    }
                }
            }

            if (dirLoading) {
                item { Hint("正在读取目录…") }
            }

            if (!dirLoading && cur.isEmpty()) {
                item { Hint(if (dirStack.isEmpty()) "这个分享里没有文件" else "这个文件夹是空的") }
            }

            items(cur, key = { it.fid }) { f ->
                Card(Modifier.padding(vertical = 4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (f.isdir) {
                                    // 文件夹 → 进入下一层
                                    dirStack = dirStack + (f.fid to f.fname)
                                    dirLoading = true
                                    note("")
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) {
                                            PanHub.listShareFiles(
                                                ctx, c.platform, c.session, f.fid, c.credential,
                                            )
                                        }
                                        dirLoading = false
                                        r.fold(
                                            onSuccess = { dirEntries = it },
                                            onFailure = {
                                                // 进不去就退回来，别把用户困在空目录
                                                dirStack = dirStack.dropLast(1)
                                                note(it.message ?: "读取目录失败", isError = true)
                                            },
                                        )
                                    }
                                } else {
                                    // 文件 → 取直链下载
                                    busy = true
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) {
                                            DownloadEntry.startFile(ctx, c.platform, c.session, f, c.credential)
                                        }
                                        busy = false
                                        when (r) {
                                            is DownloadEntry.EntryResult.Started -> {
                                                choices = null
                                                dirStack = emptyList()
                                                dirEntries = emptyList()
                                                note("已开始下载：${r.task.fileName}")
                                            }

                                            is DownloadEntry.EntryResult.Failed ->
                                                note(r.message, isError = true)

                                            else -> note("没处理成功，重试看看", isError = true)
                                        }
                                    }
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (f.isdir) "📁 ${f.fname}" else f.fname,
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (f.fsize > 0 || f.isdir) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (f.isdir) "文件夹 · 点进浏览" else formatBytes(f.fsize),
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // 文件夹没有「存网盘」，文件才有
                        if (!f.isdir) {
                            GhostButton("存网盘") {
                                busy = true
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        PanHub.saveToMyDrive(ctx, c.platform, c.session, f, c.credential)
                                    }
                                    busy = false
                                    r.fold(
                                        onSuccess = {
                                            note("已转存到你的网盘：${f.fname}")
                                        },
                                        onFailure = { note(it.message ?: "转存失败", isError = true) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ 队列
        val queue = DownloadManager.queue()
        if (queue.isNotEmpty() && choices == null) {
            item { SectionLabel("下载队列 · ${queue.size} 个等待中") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("开始队列") { DownloadManager.startQueue() }
                    GhostButton("清空队列") { DownloadManager.clearQueue() }
                }
            }
            items(queue, key = { "q_${it.id}" }) { t ->
                Card(Modifier.padding(vertical = 2.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${queue.indexOfFirst { it.id == t.id } + 1}",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.fileName.ifBlank { t.url.take(50) },
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (t.totalBytes > 0) formatBytes(t.totalBytes) else "大小未知",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        RoundIcon("↑") { DownloadManager.moveInQueue(t.id, up = true) }
                        Spacer(Modifier.width(5.dp))
                        RoundIcon("↓") { DownloadManager.moveInQueue(t.id, up = false) }
                        Spacer(Modifier.width(5.dp))
                        RoundIcon("▶", tint = MiuixTheme.colorScheme.primary) {
                            DownloadManager.startQueuedNow(t.id)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ 任务列表
        if (tasks.isNotEmpty()) {
            // 顶部汇总：总速度 / 总进度，一眼看清
            item {
                val active = tasks.filter { it.isActive }
                val totalSpeed = active.sumOf { speeds[it.id] ?: 0L }
                val doneB = tasks.sumOf { it.uiDoneBytes }
                val totalB = tasks.filter { it.totalBytes > 0L }.sumOf { it.totalBytes }
                Card {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (active.isEmpty()) "没有进行中的任务"
                                else "正在下载 ${active.size} 个",
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                buildString {
                                    append("累计 ")
                                    append(formatBytes(doneB))
                                    if (totalB > 0L) {
                                        append(" / ")
                                        append(formatBytes(totalB))
                                    }
                                    val sp = formatSpeed(totalSpeed)
                                    if (sp.isNotBlank()) {
                                        append("  总速度 ")
                                        append(sp)
                                    }
                                },
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    Hint(
                        when (filter) {
                            1 -> "没有正在下载的任务"
                            2 -> "还没有下完的文件"
                            else -> "没有失败的任务"
                        },
                    )
                }
            }

            items(shown, key = { it.id }) { task ->
                DownloadCard(
                    task = task,
                    speed = speeds[task.id] ?: 0L,
                    onPause = { DownloadManager.pause(task.id) },
                    onResume = { DownloadManager.resume(task.id) },
                    onRetry = { DownloadManager.retry(task.id) },
                    onRemove = { confirmRemove = task },
                    onPickThreads = { threadPick = task },
                    onOpen = { openLocalFile(ctx, task) },
                    onShowVerify = { verifyDetail = task },
                    onDetail = { detail = task },
                    onForceStart = { DownloadManager.forceStart(task.id) },
                )
            }
        } else if (choices == null) {
            item {
                Hint("还没有下载任务 —— 在上面粘一条链接就能开始")
            }
        }

        // ------------------------------------------------ 底部操作栏
        item {
            Card {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tasks.any { it.isActive }) {
                        GhostButton("全暂停") { DownloadManager.pauseAll() }
                    } else if (tasks.any { it.isPaused }) {
                        GhostButton("全继续") { DownloadManager.resumeAll() }
                    }
                    if (finished.isNotEmpty()) {
                        GhostButton("清空已完成") { DownloadManager.clearFinished() }
                    }
                    GhostButton("打开目录") { openDownloadDir(ctx) }
                }
            }
        }

        item {
            Hint(
                if (StoragePermission.granted(ctx)) "保存位置：Download/rom-hub/"
                else "未开存储权限，暂存在 App 私有目录（去「设置」里开启）"
            )
        }
    }

    // 任务属性页
    detail?.let { t ->
        val fresh = tasks.firstOrNull { it.id == t.id }
        if (fresh == null) {
            detail = null
        } else {
            DownloadDetailScreen(
                task = fresh,
                speed = speeds[fresh.id] ?: 0L,
                onBack = { detail = null },
                onOpen = {
                    openLocalFile(ctx, fresh)
                    detail = null
                },
                onRetry = {
                    DownloadManager.retry(fresh.id)
                    detail = null
                },
                onDelete = {
                    confirmRemove = fresh
                    detail = null
                },
            )
        }
    }

    // 删除任务前问一句：文件留着还是清掉（用户要的就是这个选择权）
    confirmRemove?.let { t ->
        val hasData = remember(t.id) { DownloadManager.hasLocalData(t) }
        MiuixDialog(
            title = "删除这条任务？",
            message = buildString {
                append("「${t.fileName.ifBlank { t.url.take(40) }}」\n\n")
                append(
                    if (hasData) {
                        "磁盘上已经有下载的数据（${formatBytes(t.doneBytes)}）。" +
                                "点「删除并清理文件」会把它一并删掉，不可恢复。"
                    } else {
                        "磁盘上还没有数据，只有一条任务记录。"
                    }
                )
            },
            confirmText = if (hasData) "删除并清理文件" else "确认删除",
            dismissText = "取消",
            onConfirm = {
                DownloadManager.remove(t.id, deleteFile = true)
                SnackbarController.show("已删除：${t.fileName.ifBlank { "任务" }}")
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }

    // 单任务线程数选择弹窗。
    // 用 androidx Dialog 而不是 MiuixDialog —— MiuixDialog 没有内容槽，塞不进档位网格。
    threadPick?.let { t ->
        val current = t.effectiveThreads(Prefs.downloadThreads(ctx))
        androidx.compose.ui.window.Dialog(onDismissRequest = { threadPick = null }) {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "线程数",
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.fileName.ifBlank { t.url.take(40) },
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    ThreadOptionsGrid(value = current) { n ->
                        DownloadManager.setThreads(t.id, n)
                        SnackbarController.show("已设为 $n 线程")
                        threadPick = null
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "线程越高越吃带宽。百度易限速建议 4；夸克/115 可到 32~64。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            GhostButton("跟随全局设置") {
                                DownloadManager.setThreads(t.id, 0)
                                SnackbarController.show("已改为跟随全局设置")
                                threadPick = null
                            }
                        }
                    }
                }
            }
        }
    }

    // 校验详情弹窗：把「完整性/签名」结论摊开给用户看
    verifyDetail?.let { t ->
        MiuixDialog(
            title = "完整性校验",
            message = buildString {
                append("「${t.fileName.ifBlank { t.url.take(40) }}」\n\n")
                if (t.verifyNote.isBlank()) {
                    append("这条任务还没做校验（可能是老任务，或校验被关掉了）。")
                } else {
                    append(t.verifyNote)
                    append("\n\n")
                    append("大小：")
                    append(formatBytes(t.doneBytes))
                    if (t.totalBytes > 0) {
                        append(" / ")
                        append(formatBytes(t.totalBytes))
                    }
                }
            },
            confirmText = "知道了",
            dismissText = null,
            onConfirm = { verifyDetail = null },
            onDismiss = { verifyDetail = null },
        )
    }
}

/** 圆形小操作按钮（链接条右侧那几个）。 */
@Composable
private fun RoundIcon(
    text: String,
    tint: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(cs.surfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 小开关（高级选项里用，比设置页那个紧凑）。 */
@Composable
private fun MiniSwitch(checked: Boolean) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .width(42.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) cs.primary else cs.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

/** 用系统应用打开下好的文件（按扩展名给 mime，认不出来就给通配）。 */
private fun openLocalFile(ctx: android.content.Context, task: DownloadTask) {
    runCatching {
        val f = DownloadManager.fileOf(task)
        if (!f.exists()) {
            SnackbarController.show("文件不在了，可能已被移动或删除")
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", f,
        )
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        SnackbarController.show("打不开：${it.message ?: "没有能处理该文件的应用"}")
    }
}

/**
 * 打开下载文件所在的目录（系统文件管理器里定位过去）。
 *
 * 三档兜底，尽量别让用户点了没反应：
 *   ① 有「所有文件访问」→ 直接用 DocumentsUI 打开 `Download/rom-hub/` 的 file:// 视图
 *   ② 没有权限（文件在私有目录）→ 用 DocumentsUI 打开 App 外部私有下载目录
 *   ③ DocumentsUI 打不开 → 退回「打开所有文件访问设置页」并提示
 */
private fun openDownloadDir(ctx: android.content.Context) {
    val dir = runCatching { org.linbaogu.romhub.download.DownloadStore.downloadDir(ctx) }
        .getOrElse { null }
    if (dir == null || !dir.exists()) {
        SnackbarController.show("还没下过文件，目录还没建出来")
        return
    }
    val opened = runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(
                android.net.Uri.fromFile(dir),
                "resource/folder",
            )
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    }.getOrElse { false }
    if (opened) return

    // 退路：用 DocumentsUI 打开「Download/rom-hub」，多数 ROM 上比 file:// 更稳
    val viaDocuments = runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(
                    android.net.Uri.parse(
                        "content://com.android.externalstorage.documents/root/primary"
                    ),
                    "vnd.android.document/root",
                )
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrElse { false }
    if (!viaDocuments) {
        SnackbarController.show("目录：${dir.absolutePath}（没有能打开它的应用）")
    }
}

// ---------------------------------------------------------------- 线程数选择

/**
 * 通用线程数网格（1~256，两行）。
 *
 * 单独抽出来是因为分片线程现在有 9 个档位，一行横排会被挤没。
 */
@Composable
private fun ThreadOptionsGrid(value: Int, onPick: (Int) -> Unit) {
    val options = Prefs.THREAD_OPTIONS
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        options.chunked(5).forEach { rowOpts ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowOpts.forEach { n ->
                    val selected = n == value
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { onPick(n) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            n.toString(),
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
                // 补齐最后一行缺失的格子，避免 weight 拉伸导致前面的格变宽
                repeat(5 - rowOpts.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 未使用但保留：给外部引用的线程上限提示（避免 ChunkDownloader 常量散落各处）。 */
private val MAX_THREADS_HINT = ChunkDownloader.MAX_THREADS
