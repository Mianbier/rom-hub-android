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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import org.linbaogu.romhub.download.DownloadEntry
import org.linbaogu.romhub.download.DownloadManager
import org.linbaogu.romhub.download.DownloadTask
import org.linbaogu.romhub.download.ChunkDownloader
import org.linbaogu.romhub.download.formatBytes
import org.linbaogu.romhub.download.formatEta
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.login.MiuixDialog
import org.linbaogu.romhub.download.formatSpeed
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载页。
 *
 * 一条链接进来后的三种走法（都由 [DownloadEntry] 判定）：
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
    var confirmRemove by remember { mutableStateOf<org.linbaogu.romhub.download.DownloadTask?>(null) }
    // 点了任务卡片上的「N 线程」→ 弹出这个任务的线程数选择
    var threadPick by remember { mutableStateOf<org.linbaogu.romhub.download.DownloadTask?>(null) }
    // 全局线程 / 并发数：必须用 state 承接，不能在组合期直接读 Prefs，
    // 否则点完档位高亮不刷新（看着像没反应）。
    var globalThreads by remember { mutableIntStateOf(Prefs.downloadThreads(ctx)) }
    var globalConcurrent by remember { mutableIntStateOf(Prefs.downloadConcurrent(ctx)) }

    val tasks by DownloadManager.tasks.collectAsState()
    val speeds by DownloadManager.speeds.collectAsState()

    fun note(text: String, isError: Boolean = false) {
        message = text
        messageIsError = isError
    }

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
                    input = ""
                    pwd = ""
                    choices = null
                    dirStack = emptyList()
                    dirEntries = emptyList()
                    needPwdFor = null
                    val name = result.task.fileName.ifBlank { result.task.url.take(60) }
                    note("已开始下载：$name")
                    // 页内那行字容易被忽略（列表长 / 正要切页），再弹一条全局提示条兜底
                    SnackbarController.show("已开始下载：$name")
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
                    // 通知外层切到「账号」段
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
                    input = ""
                    pwd = ""
                    choices = null
                    needPwdFor = null
                    note("这是 GitHub 仓库，正在打开仓库页让你挑文件…")
                    onOpenGitHubRepo?.invoke(result.owner, result.repo)
                }

                is DownloadEntry.EntryResult.Failed -> note(result.message, isError = true)
            }
        }
    }

    // 外部点进来的链接：进来就自动开始下载（这就是「点链接直接下」）
    androidx.compose.runtime.LaunchedEffect(initialUrl) {
        val u = initialUrl
        if (!u.isNullOrBlank()) {
            input = u
            onConsumed()
            run(u, null)
        }
    }

    ListScreen(
        title = "下载",
        subtitle = if (tasks.any { it.isActive }) {
            "正在下载 ${tasks.count { it.isActive }} 个"
        } else {
            "粘贴链接即可开始下载"
        },
        bottomInnerPadding = bottomInnerPadding,
    ) {
        // ------------------------------------------------ 粘贴 / 输入
        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "下载链接",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (input.isBlank()) {
                            Text(
                                "ROM 官方包直链 / GitHub / 夸克·百度·115·UC·123·移动·迅雷 分享链接",
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = MiuixTheme.textStyles.body1.copy(
                                color = MiuixTheme.colorScheme.onSurface,
                            ),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

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
                                    "提取码（4 位）",
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            BasicTextField(
                                value = pwd,
                                onValueChange = { pwd = it.take(8) },
                                textStyle = MiuixTheme.textStyles.body1.copy(
                                    color = MiuixTheme.colorScheme.onSurface,
                                ),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(if (busy) "解析中…" else "开始下载") {
                            if (!busy) run(input, pwd.ifBlank { null })
                        }
                        // 显式的「浏览文件」入口：粘贴链接点这个，直接进目录树挑文件下，
                        // 不用先「开始下载」才看到列表（用户要求）。
                        GhostButton(if (busy) "读取中…" else "浏览文件") {
                            if (!busy) run(input, pwd.ifBlank { null }, browseOnly = true)
                        }
                        GhostButton("粘贴") {
                            input = clipboard.getText()?.text.orEmpty()
                            note("")
                        }
                        GhostButton("清空") {
                            input = ""
                            pwd = ""
                            choices = null
                            dirStack = emptyList()
                            dirEntries = emptyList()
                            needPwdFor = null
                            note("")
                        }
                    }

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = if (messageIsError) MiuixTheme.colorScheme.error
                            else MiuixTheme.colorScheme.primary,
                        )
                    }
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

        // ------------------------------------------------ 任务列表
        val running = tasks.filter { !it.isDone }
        val finished = tasks.filter { it.isDone }
        if (tasks.isNotEmpty()) {
            // 顶部汇总：几条在跑 / 总速度 / 总进度，一眼看清别点进去翻
            item {
                val active = tasks.filter { it.isActive }
                val totalSpeed = active.sumOf { speeds[it.id] ?: 0L }
                val doneB = tasks.sumOf { it.doneBytes }
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
                        if (tasks.any { it.isActive }) {
                            GhostButton("全暂停") { DownloadManager.pauseAll() }
                        } else if (tasks.any { it.isPaused }) {
                            GhostButton("全继续") { DownloadManager.resumeAll() }
                        }
                    }
                }
            }
            // ① 下载中（含等待 / 暂停 / 失败，没完成的一律归这儿）
            if (running.isNotEmpty()) {
                item { SectionLabel("下载中") }
                items(running, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        speed = speeds[task.id] ?: 0L,
                        onPause = { DownloadManager.pause(task.id) },
                        onResume = { DownloadManager.resume(task.id) },
                        onRetry = { DownloadManager.retry(task.id) },
                        onRemove = { confirmRemove = task },
                        onPickThreads = { threadPick = task },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("全部暂停") { DownloadManager.pauseAll() }
                        GhostButton("全部继续") { DownloadManager.resumeAll() }
                    }
                }
            }

            // ② 已完成：卡片下方给「打开本地目录 / 清空记录」
            if (finished.isNotEmpty()) {
                item { SectionLabel("已完成") }
                items(finished, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        speed = speeds[task.id] ?: 0L,
                        onPause = { DownloadManager.pause(task.id) },
                        onResume = { DownloadManager.resume(task.id) },
                        onRetry = { DownloadManager.retry(task.id) },
                        onRemove = { confirmRemove = task },
                        onPickThreads = { threadPick = task },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("打开本地目录") { openDownloadDir(ctx) }
                        GhostButton("清空已完成记录") { DownloadManager.clearFinished() }
                    }
                }
            }
        } else if (choices == null) {
            item {
                Hint("还没有下载任务 —— 上面粘贴一条链接试试")
            }
        }

        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "下载设置",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "分片线程",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                    )
                    ThreadOptionsGrid(value = globalThreads) {
                        // 存完立刻更新 state：否则高亮还停在旧档位上，
                        // 看着像「点了没反应」（直接读 prefs 不会触发重组）
                        globalThreads = it
                        Prefs.setDownloadThreads(ctx, it)
                    }
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    CountRow(label = "同时任务数", value = globalConcurrent) {
                        globalConcurrent = it
                        Prefs.setDownloadConcurrent(ctx, it)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (StoragePermission.granted(ctx)) {
                            "文件保存在 Download/rom-hub/，文件管理器里直接能找到。"
                        } else {
                            "当前保存在 App 私有目录（卸载会清）。开启「所有文件访问」后改存 Download/rom-hub/。"
                        },
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (!StoragePermission.granted(ctx)) {
                        Spacer(Modifier.height(8.dp))
                        GhostButton("去开启存储权限") {
                            StoragePermission.openAllFilesSettings(ctx)
                        }
                    }
                    if (onOpenSettings != null) {
                        Spacer(Modifier.height(8.dp))
                        GhostButton("更多设置（按网盘分线程 / 限速 / 重试）") { onOpenSettings() }
                    }
                }
            }
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
        val current = t.effectiveThreads(Prefs.downloadThreads(LocalContext.current))
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
}

// ---------------------------------------------------------------- 单个任务卡片

@Composable
private fun TaskCard(
    task: DownloadTask,
    speed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPickThreads: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme
    val pct = (task.progress * 100).toInt()
    val eta = formatEta(task, speed)

    Card(Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                task.fileName.ifBlank { task.url.take(60) },
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))

            // 进度条 + 右侧百分比（自己画，省得引额外组件）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(cs.surfaceContainerHigh),
                ) {
                    if (task.progress > 0f || task.totalBytes <= 0L) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (task.totalBytes > 0L) task.progress else 0.15f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        task.isFailed -> cs.error
                                        task.isDone -> cs.primary
                                        task.isPaused -> cs.onSurfaceVariantSummary
                                        else -> cs.primary
                                    }
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (task.totalBytes > 0L) "$pct%" else "—",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isFailed) cs.error else cs.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))
            // 状态行：状态 · 已下/总量 · 速度 · 剩余时间
            Text(
                buildString {
                    append(task.stateLabel())
                    if (task.totalBytes > 0L) {
                        append("  ")
                        append(formatBytes(task.doneBytes))
                        append(" / ")
                        append(formatBytes(task.totalBytes))
                    } else {
                        append("  ")
                        append(formatBytes(task.doneBytes))
                    }
                    val sp = formatSpeed(speed)
                    if (sp.isNotBlank() && task.isActive) {
                        append("  ")
                        append(sp)
                    }
                    if (eta.isNotBlank() && task.isActive) {
                        append("  ")
                        append(eta)
                    }
                },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = when {
                    task.isFailed -> cs.error
                    task.isDone -> cs.primary
                    else -> cs.onSurfaceVariantSummary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 保存位置：让用户知道文件去哪了（存储权限没给就说明是私有目录）
            if (task.isDone) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (StoragePermission.granted(ctx)) "已保存到 Download/rom-hub/"
                    else "已保存到 App 私有目录（开启存储权限后会存到 Download/rom-hub/）",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    task.isActive -> GhostButton("暂停") { onPause() }
                    task.isPaused -> GhostButton("继续") { onResume() }
                    task.isFailed -> GhostButton("重试") { onRetry() }
                    else -> {}
                }
                // 下完了就给「打开 / 分享」—— 下载完了不用再跑文件管理器翻
                if (task.isDone) {
                    GhostButton("打开") { openLocalFile(ctx, task) }
                }
                // 单任务线程数：网盘各家对并发的容忍度差很多，
                // 允许每个任务单独调（百度 4 不限速，115/夸克可以拉到 64+）。
                // 标签跟着 task.threads 走（改完 DownloadManager.setThreads 会更新任务对象），
                // 任务没单独设过时显示「跟随全局」并带上当前全局值。
                val isCustom = task.threads in 1..ChunkDownloader.MAX_THREADS
                val shownThreads = task.effectiveThreads(Prefs.downloadThreads(ctx))
                GhostButton(if (isCustom) "$shownThreads 线程" else "$shownThreads·全局") {
                    onPickThreads()
                }
                GhostButton("删除") { onRemove() }
            }
        }
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

/** 小数值选择行（并发任务数这种，档位少，标签和选项同一行）。 */
@Composable
private fun CountRow(label: String, value: Int, onPick: (Int) -> Unit) {
    val options = listOf(1, 2, 3, 5)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = MiuixTheme.textStyles.body1.fontSize, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { n ->
                val selected = n == value
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable { onPick(n) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        n.toString(),
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
