package org.linbaogu.romhub.download

import android.content.Context
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.pan.model.ShareFile
import org.linbaogu.romhub.pan.model.ShareSession

/**
 * 「点一下就开始下载」的统一入口。
 *
 * 不管是 ROM 官方直链、GitHub 链接，还是夸克/百度/115 的分享链接，
 * 都往这里丢 —— 直链直接进下载队列；网盘链接先解析，**只有一个文件就自动开始下**，
 * 多个文件才把选择权交给界面。
 */
object DownloadEntry {

    sealed interface EntryResult {
        /** 已经开始下了 */
        data class Started(val task: DownloadTask) : EntryResult

        /** 分享里有多个文件，需要用户挑 */
        data class ChooseFiles(
            val platform: SharePlatform,
            val session: ShareSession,
            val files: List<ShareFile>,
            val credential: String,
        ) : EntryResult

        data class NeedLogin(val platform: SharePlatform) : EntryResult
        data class NeedPassword(val platform: SharePlatform, val hint: String) : EntryResult
        data class Failed(val message: String) : EntryResult

        /**
         * 是 GitHub 仓库页（不是某个具体文件）—— 界面应该打开仓库详情页，
         * 让用户从 Release 资产里挑文件下，而不是直接下一个 HTML 页面。
         */
        data class GitHubRepo(val owner: String, val repo: String) : EntryResult
    }

    /**
     * 开始下载一条链接/一段分享文案。
     *
     * @param pwd 提取码（用户在界面上补填的；不填就用链接里带的）
     * @param browseOnly 只浏览不自动下 —— 用户点「浏览文件」时传 true：
     *   哪怕分享里只有一个文件，也把列表交给界面让用户自己点，不替他决定（用户要求）。
     */
    suspend fun start(
        ctx: Context,
        text: String,
        pwd: String? = null,
        browseOnly: Boolean = false,
    ): EntryResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return EntryResult.Failed("链接是空的")

        // GitHub 仓库页要先拦一下：这类链接交给下载器只会下到一个 HTML 页面，
        // 得先把页面打开、列出 Release 资产让用户挑。单文件直链（/releases/download/...）
        // 不走这个分支，照旧直接进队列。
        org.linbaogu.romhub.ui.screens.githubRepoOf(trimmed)?.let { (owner, repo) ->
            return EntryResult.GitHubRepo(owner, repo)
        }

        return when (val r = PanHub.resolve(ctx, trimmed, pwd)) {
            is PanHub.PanResult.Direct -> {
                // 普通直链（含 GitHub / ROM 官方包）：直接进队列
                EntryResult.Started(DownloadManager.add(r.url))
            }

            is PanHub.PanResult.NeedLogin ->
                EntryResult.NeedLogin(r.platform)

            is PanHub.PanResult.Failed -> EntryResult.Failed(r.message)

            // 需要提取码 / 提取码错了：界面显示输入框让用户补填
            is PanHub.PanResult.NeedPassword -> EntryResult.NeedPassword(
                r.platform,
                r.hint.ifBlank { "这个分享需要提取码，请填在下面的输入框" },
            )

            is PanHub.PanResult.Files -> {
                val realFiles = r.files.filter { !it.isdir }
                val dirs = r.files.filter { it.isdir }
                when {
                    // 「浏览文件」：一律把列表交给界面，用户自己点（包含文件夹）
                    browseOnly -> EntryResult.ChooseFiles(
                        r.platform, r.session, r.files, r.credential,
                    )

                    // 根目录一个文件都没有、却只有文件夹 —— 不能直接报「没有文件」，
                    // 得把整个列表（含文件夹）交给界面，让用户点进去逐层找
                    // （用户反馈：分享的文件都在子文件夹里，一输入就提示「没有文件」）。
                    realFiles.isEmpty() && dirs.isNotEmpty() ->
                        EntryResult.ChooseFiles(r.platform, r.session, r.files, r.credential)

                    realFiles.isEmpty() ->
                        EntryResult.Failed("这个分享里没有可下载的文件")

                    // 只有一个文件、且没有子文件夹 → 直接开始下，不问用户
                    realFiles.size == 1 && dirs.isEmpty() -> startFile(
                        ctx, r.platform, r.session, realFiles.first(), r.credential,
                    )

                    // 有文件夹就交给界面浏览（哪怕只有一个文件，也让用户能看见目录结构）
                    else -> EntryResult.ChooseFiles(r.platform, r.session, r.files, r.credential)
                }
            }
        }
    }

    /** 取直链并加入下载队列。 */
    suspend fun startFile(
        ctx: Context,
        platform: SharePlatform,
        session: ShareSession,
        file: ShareFile,
        credential: String,
    ): EntryResult {
        val link = PanHub.pickDownloadLink(ctx, platform, session, file, credential)
        return link.fold(
            onSuccess = { dl ->
                val task = DownloadManager.add(
                    url = dl.downloadUrl,
                    fileName = dl.filename.ifBlank { file.fname },
                    headers = emptyMap(),
                    isHls = dl.isHls,
                )
                if (dl.size > 0) task.totalBytes = dl.size
                EntryResult.Started(task)
            },
            onFailure = { e -> EntryResult.Failed(e.message ?: "取下载地址失败") },
        )
    }
}
