package org.linbaogu.romhub.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.pan.model.ShareFile
import org.linbaogu.romhub.pan.model.ShareInfo
import org.linbaogu.romhub.pan.model.ShareExpire

/**
 * 云盘文件浏览的统一 ViewModel（7 家网盘共用）。
 *
 * 云析为每家写了一个独立的 `*CloudViewModel`（各约 680 行，90% 是重复的分页/选择/路径逻辑）。
 * 这里只保留一份状态机，把「怎么调这家网盘」全部下沉到 [CloudApi] 适配器里。
 *
 * 状态机只有三种：加载中 / 出错 / 加载完成（带当前目录的文件列表 + 面包屑路径）。
 *
 * 搬运自云析（CYQawa/YunX，AGPL-3.0）的 ui/viewmodel 下的各家 CloudViewModel，
 * 合并去重后的等价实现。
 */
class CloudBrowserViewModel(
    private val ctx: Context,
    val platform: SharePlatform,
) {

    private val api: CloudApi? = PanHub.cloudApi(platform)

    // ---------------------------------------------------------------- 状态

    val state: StateFlow<CloudState> get() = _state.asStateFlow()
    private val _state = MutableStateFlow<CloudState>(CloudState.Loading)

    /** 下拉刷新中（与首次加载区分：首次显示整页 Loading，刷新只在顶部转圈） */
    val refreshing: StateFlow<Boolean> get() = _refreshing.asStateFlow()
    private val _refreshing = MutableStateFlow(false)

    /** 操作结果提示（弹窗关闭后仍能弹出，所以放 ViewModel 层） */
    val message: StateFlow<String?> get() = _message.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)

    fun consumeMessage() {
        _message.value = null
    }

    // ---------------------------------------------------------------- 搜索

    /** 这家网盘支不支持全盘搜索 */
    val supportsSearch: Boolean get() = api?.supportsSearch == true

    /** 正在搜索中（搜索框后面转圈） */
    val searching: StateFlow<Boolean> get() = _searching.asStateFlow()
    private val _searching = MutableStateFlow(false)

    /**
     * 搜索结果；null = 没在搜索（显示正常目录列表）。
     *
     * 搜索是**全盘**的（跨目录），所以结果里的文件夹点进去走的是「用 fid 直接列目录」，
     * 而不是相对当前目录的 enter() —— 这点和普通浏览不同，[CloudState.Loaded] 里
     * 用 [CloudState.Loaded.isSearchResult] 标记出来给界面区分。
     */
    val searchResult: StateFlow<CloudState.Loaded?> get() = _searchResult.asStateFlow()
    private val _searchResult = MutableStateFlow<CloudState.Loaded?>(null)

    /** 当前搜索关键词（空串表示没在搜索） */
    var currentKeyword: String = ""
        private set

    suspend fun search(keyword: String) = withContext(Dispatchers.IO) {
        val a = api
        val q = keyword.trim()
        currentKeyword = q
        if (q.isEmpty()) {
            _searchResult.value = null
            return@withContext
        }
        if (a == null || !a.supportsSearch) {
            _message.value = "${PanHub.platformName(platform)} 暂不支持全盘搜索"
            return@withContext
        }
        _searching.value = true
        val r = runCatching { a.search(q, credential) }
        _searching.value = false
        r.fold(
            onSuccess = { files ->
                if (files == null) {
                    _searchResult.value = null
                    _message.value = "${PanHub.platformName(platform)} 暂不支持全盘搜索"
                } else {
                    _searchResult.value = CloudState.Loaded(
                        files = files.sortedWith(fileOrder),
                        pathNames = listOf("搜索：$q"),
                        pathIds = emptyList(),
                        isSearchResult = true,
                    )
                    if (files.isEmpty()) _message.value = "没有找到含「$q」的文件"
                }
            },
            onFailure = { e ->
                _searchResult.value = null
                _message.value = "搜索失败：${e.message ?: "未知错误"}"
            },
        )
    }

    fun clearSearch() {
        currentKeyword = ""
        _searchResult.value = null
    }

    /** 从搜索结果里进一个目录：搜到的目录不属于当前路径栈，用 fid 直接列。 */
    suspend fun openSearchDir(dir: ShareFile) = withContext(Dispatchers.IO) {
        val dirId = api?.dirIdOf(dir) ?: dir.fid
        pathStack = mutableListOf(dirId to dir.fname)
        currentDirId = dirId
        _searchResult.value = null
        currentKeyword = ""
        selected.clear()
        bumpSelection()
        loadCurrent(firstLoad = true)
    }

    // ---------------------------------------------------------------- 上传

    /** 这家网盘支不支持上传 */
    val supportsUpload: Boolean get() = api?.supportsUpload == true

    /** 上传进行中的百分比（-1 = 没在上传）。0..100 */
    val uploadProgress: StateFlow<Int> get() = _uploadProgress.asStateFlow()
    private val _uploadProgress = MutableStateFlow(-1)

    /**
     * 上传一批本地文件到**当前目录**。
     *
     * @param files 已经解析好元数据的本地文件（名 + 大小 + 每次打开输入流的工厂）
     */
    suspend fun uploadFiles(files: List<LocalUpload>) = withContext(Dispatchers.IO) {
        val a = api
        if (a == null || !a.supportsUpload) {
            _message.value = "${PanHub.platformName(platform)} 暂不支持上传"
            return@withContext
        }
        if (files.isEmpty()) return@withContext
        var ok = 0
        files.forEachIndexed { i, f ->
            val r = runCatching {
                a.upload(
                    dirId = currentDirId,
                    fileName = f.name,
                    size = f.size,
                    openStream = f.openStream,
                    credential = credential,
                ) { sent, total ->
                    // 进度按「第 i 个文件」折算到整批
                    val base = i * 100f / files.size
                    val part = if (total > 0) sent.toFloat() / total * 100f / files.size else 0f
                    _uploadProgress.value = (base + part).toInt().coerceIn(0, 100)
                }
            }
            if (r.getOrDefault(false)) ok++
        }
        _uploadProgress.value = -1
        _message.value = if (ok == files.size) {
            "已上传 ${files.size} 个文件"
        } else {
            "上传完成 $ok/${files.size}，失败的可以重试"
        }
        if (ok > 0) loadCurrent(firstLoad = false)
    }

    /** 一个待上传的本地文件（把 Android Uri 的读取封装在界面层，VM 只认这三样）。 */
    data class LocalUpload(
        val name: String,
        val size: Long,
        val openStream: () -> java.io.InputStream,
    )

    /** 需要切到「下载」段的信号（下载入队后由页面消费） */
    val downloadTriggered: StateFlow<Int> get() = _downloadTriggered.asStateFlow()
    private val _downloadTriggered = MutableStateFlow(0)

    fun consumeDownloadTriggered() {
        _downloadTriggered.value = 0
    }

    // ---------------------------------------------------------------- 多选

    val multiSelectMode: StateFlow<Boolean> get() = _multiSelectMode.asStateFlow()
    private val _multiSelectMode = MutableStateFlow(false)

    /** 已选中的文件（按 fid），进目录/刷新会自动清空 */
    private val selected = linkedMapOf<String, ShareFile>()

    val selectedFiles: List<ShareFile> get() = selected.values.toList()

    fun enterMultiSelect(file: ShareFile? = null) {
        _multiSelectMode.value = true
        if (file != null) selected[file.fid] = file
        bumpSelection()
    }

    fun exitMultiSelect() {
        _multiSelectMode.value = false
        selected.clear()
        bumpSelection()
    }

    /** 点一个文件行：多选模式下切换选中，否则交给调用方处理（进目录 / 打开）。 */
    fun toggleSelect(file: ShareFile) {
        if (selected.containsKey(file.fid)) selected.remove(file.fid) else selected[file.fid] = file
        bumpSelection()
    }

    fun isSelected(fid: String): Boolean = selected.containsKey(fid)

    fun selectAll(files: List<ShareFile>) {
        files.forEach { selected[it.fid] = it }
        bumpSelection()
    }

    fun clearSelection() {
        selected.clear()
        bumpSelection()
    }

    /** 选中集变化时推进一个版本号，让 Compose 重新读 [selectedFiles]。 */
    private val _selectionRev = MutableStateFlow(0)
    val selectionRev: StateFlow<Int> get() = _selectionRev.asStateFlow()

    private fun bumpSelection() {
        _selectionRev.value = _selectionRev.value + 1
    }

    // ---------------------------------------------------------------- 下载确认弹窗

    val downloadLink: StateFlow<PendingDownload?> get() = _downloadLink.asStateFlow()
    private val _downloadLink = MutableStateFlow<PendingDownload?>(null)

    fun dismissDownloadDialog() {
        _downloadLink.value = null
    }

    data class PendingDownload(
        val file: ShareFile,
        val url: String,
        val headers: Map<String, String>,
        /** 是否为 HLS（m3u8）转码流 —— 决定走 HLS 分段下载还是普通分片下载 */
        val isHls: Boolean = false,
    )

    // ---------------------------------------------------------------- 加载

    private var credential: String = ""

    /** 当前目录路径：从根到当前目录的 (fid, name) 栈。根目录为空列表。 */
    private var pathStack: MutableList<Pair<String, String>> = mutableListOf()
    private var currentDirId: String = ""

    suspend fun loadRoot() = withContext(Dispatchers.IO) {
        val a = api
        if (a == null) {
            _state.value = CloudState.Error("${PanHub.platformName(platform)} 暂不支持文件浏览")
            return@withContext
        }
        credential = runCatching { PanHub.credential(ctx, platform) }.getOrNull().orEmpty()
        if (credential.isBlank()) {
            _state.value = CloudState.Error("${PanHub.platformName(platform)} 还没登录")
            return@withContext
        }
        // 凭据不齐（139 缺 authorization 那种）时，别等到接口报错再抛抽象的半句话，
        // 这里就把它缺什么、怎么补讲清楚
        val warn = runCatching { a.credentialWarning(credential) }.getOrNull()
        if (!warn.isNullOrBlank()) {
            _state.value = CloudState.Error(warn)
            return@withContext
        }
        pathStack = mutableListOf()
        currentDirId = a.rootId
        selected.clear()
        bumpSelection()
        loadCurrent(firstLoad = true)
    }

    /** 重新读当前目录（保路径）。 */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _refreshing.value = true
        loadCurrent(firstLoad = false)
        _refreshing.value = false
    }

    private suspend fun loadCurrent(firstLoad: Boolean) {
        val a = api ?: return
        if (firstLoad) _state.value = CloudState.Loading
        val r = runCatching { a.list(currentDirId, credential) }
        r.fold(
            onSuccess = { files ->
                _state.value = CloudState.Loaded(
                    files = files.sortedWith(fileOrder),
                    pathNames = pathStack.map { it.second },
                    pathIds = pathStack.map { it.first },
                )
            },
            onFailure = { e ->
                _state.value = CloudState.Error(e.message ?: "加载失败")
            },
        )
    }

    /** 进入子目录。 */
    suspend fun enter(dir: ShareFile) = withContext(Dispatchers.IO) {
        // ⚠️ 必须走 api.dirIdOf：百度要的是绝对路径而不是 fs_id（默认实现就是 fid）
        val dirId = api?.dirIdOf(dir) ?: dir.fid
        pathStack.add(dirId to dir.fname)
        currentDirId = dirId
        selected.clear()
        bumpSelection()
        loadCurrent(firstLoad = true)
    }

    /** 返回上一级目录；已在根返回 false，由调用方决定是否退出页面。 */
    suspend fun back(): Boolean = withContext(Dispatchers.IO) {
        if (pathStack.isEmpty()) return@withContext false
        pathStack.removeAt(pathStack.lastIndex)
        currentDirId = pathStack.lastOrNull()?.first ?: (api?.rootId ?: "")
        selected.clear()
        bumpSelection()
        loadCurrent(firstLoad = true)
        true
    }

    // ---------------------------------------------------------------- 下载

    /** 单文件下载：取直链 → 弹确认（对齐解析页，展示直链可复制） */
    suspend fun prepareDownload(file: ShareFile) = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext
        val link = runCatching { a.downloadLink(file, credential) }.getOrNull()
        if (link == null || link.downloadUrl.isBlank()) {
            _message.value = "取不到下载直链，可能文件已被删除或需要会员"
            return@withContext
        }
        val url = runCatching { a.refineDownloadUrl(link.downloadUrl, credential) }
            .getOrDefault(link.downloadUrl)
        _downloadLink.value = PendingDownload(
            file = file,
            url = url,
            headers = a.downloadHeaders(credential),
            isHls = link.isHls,
        )
    }

    /** 确认下载：真正入队。 */
    fun startDownload() {
        val p = _downloadLink.value ?: return
        org.linbaogu.romhub.download.DownloadManager.add(
            url = p.url,
            fileName = p.file.fname,
            headers = p.headers,
            subDir = platform.name.lowercase(),
            isHls = p.isHls,
        )
        _downloadLink.value = null
        _message.value = "已开始下载：${p.file.fname}"
        _downloadTriggered.value = _downloadTriggered.value + 1
    }

    /** 批量下载：逐个取链入队。 */
    suspend fun downloadFiles(files: List<ShareFile>) = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext
        var ok = 0
        files.filterNot { it.isdir }.forEach { f ->
            val link = runCatching { a.downloadLink(f, credential) }.getOrNull()
            if (link != null && link.downloadUrl.isNotBlank()) {
                val url = runCatching { a.refineDownloadUrl(link.downloadUrl, credential) }
                    .getOrDefault(link.downloadUrl)
                org.linbaogu.romhub.download.DownloadManager.add(
                    url = url,
                    fileName = f.fname,
                    headers = a.downloadHeaders(credential),
                    subDir = platform.name.lowercase(),
                    isHls = link.isHls,
                )
                ok++
            }
        }
        _message.value = if (ok > 0) "已开始下载 $ok 个文件" else "没有可下载的文件"
        if (ok > 0) _downloadTriggered.value = _downloadTriggered.value + 1
        exitMultiSelect()
    }

    // ---------------------------------------------------------------- 文件操作

    suspend fun rename(file: ShareFile, newName: String) = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext
        if (newName.isBlank() || newName == file.fname) return@withContext
        val ok = runCatching { a.rename(file, newName, credential) }.getOrDefault(false)
        _message.value = if (ok) "已重命名" else "重命名失败"
        if (ok) loadCurrent(firstLoad = false)
    }

    suspend fun createFolder(name: String) = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext
        val id = runCatching { a.createFolder(name, currentDirId, credential) }.getOrNull()
        _message.value = if (id != null) "已创建文件夹：$name" else "创建失败"
        if (id != null) loadCurrent(firstLoad = false)
    }

    suspend fun delete(files: List<ShareFile>) = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext
        val ok = runCatching { a.delete(files, credential) }.getOrDefault(false)
        _message.value = if (ok) "已删除 ${files.size} 项" else "删除失败"
        if (ok) {
            exitMultiSelect()
            loadCurrent(firstLoad = false)
        }
    }

    suspend fun move(files: List<ShareFile>, toDirId: String) = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext
        val ok = runCatching { a.move(files, toDirId, credential) }.getOrDefault(false)
        _message.value = if (ok) "已移动 ${files.size} 项" else "移动失败"
        if (ok) {
            exitMultiSelect()
            loadCurrent(firstLoad = false)
        }
    }

    /** 列出某个目录的子文件夹（移动/分享选目录用）。 */
    suspend fun listDirs(dirId: String): List<ShareFile> = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext emptyList()
        runCatching { a.list(dirId, credential).filter { it.isdir } }.getOrDefault(emptyList())
    }

    suspend fun createShare(
        files: List<ShareFile>,
        urlType: Int,
        passcode: String,
        expiredType: Int,
    ): ShareInfo? = withContext(Dispatchers.IO) {
        val a = api ?: return@withContext null
        val r = runCatching { a.createShare(files, urlType, passcode, expiredType, credential) }
            .getOrNull()
        if (r == null) _message.value = "创建分享失败" else exitMultiSelect()
        r
    }

    val expireOptions: List<Pair<String, Int>>
        get() = api?.expireOptions ?: defaultExpireOptions

    /** 排序：文件夹在前，然后按名字（忽略大小写）。 */
    private val fileOrder = compareByDescending<ShareFile> { it.isdir }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fname }
}

/** 浏览页的三种状态。 */
sealed interface CloudState {
    data object Loading : CloudState

    data class Error(val message: String) : CloudState

    data class Loaded(
        val files: List<ShareFile>,
        val pathNames: List<String>,
        val pathIds: List<String>,
        /** true = 这是全盘搜索结果（不是某个目录的内容），界面据此改标题、隐藏面包屑 */
        val isSearchResult: Boolean = false,
    ) : CloudState
}
