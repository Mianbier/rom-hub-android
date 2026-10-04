package org.linbaogu.romhub.pan.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 网盘分享链接收藏。
 *
 * 云析那边是 Room 表；这里同样换成 JSON —— 收藏量级就是几十上百条，
 * 全量读写完全够用，还省掉 KSP + Room 两个构建环节。
 *
 * 对外保留和原 Dao 一样的方法名，界面层照搬过来几乎不用改。
 */
@Serializable
data class BookmarkEntity(
    val id: Long = 0,
    /** 完整分享链接 / 分享文案（再次解析用，原样保存） */
    val link: String,
    /** 分享标题（解析后回填；手动添加可为空，展示时回退成链接） */
    val title: String = "",
    /** 平台名（QUARK/UC/XUNLEI/BAIDU/C139/PAN123/PAN115），未知为空串 */
    val platform: String = "",
    /** 提取码 */
    val pwd: String = "",
    val category: String = DEFAULT_CATEGORY,
    /** 是否固定到首页快捷方式 */
    val homePinned: Boolean = false,
    /** 首页快捷方式的自定义文字（空 = 自动取标题前几个字） */
    val homeLabel: String = "",
    val createTime: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_CATEGORY = "未分类"

        val PRESET_CATEGORIES = listOf(
            DEFAULT_CATEGORY, "视频", "文档", "软件", "音乐", "图片", "压缩包", "其他",
        )
    }
}

/**
 * 书签仓库（原 BookmarkDao 的替代品）。
 *
 * 内部维护一份内存列表 + StateFlow，写入时整体落盘。收藏这种量级，
 * 全量写 JSON 比增量 SQL 简单得多，也不会有并发写坏文件的问题（文件小，写完即返回）。
 */
class BookmarkStore(private val ctx: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(ctx.filesDir, "romhub_bookmarks.json")
    private val serializer = ListSerializer(BookmarkEntity.serializer())

    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<BookmarkEntity>> = _all.asStateFlow()

    private fun load(): List<BookmarkEntity> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val text = file.readText()
        if (text.isBlank()) emptyList() else json.decodeFromString(serializer, text)
    }.getOrDefault(emptyList())

    private fun persist(list: List<BookmarkEntity>) {
        runCatching {
            val tmp = File(ctx.filesDir, "romhub_bookmarks.json.tmp")
            tmp.writeText(json.encodeToString(serializer, list))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private fun mutate(block: (List<BookmarkEntity>) -> List<BookmarkEntity>) {
        val next = block(_all.value)
        _all.value = next
        persist(next)
    }

    /** 新增；返回新条目的 id */
    fun insert(bookmark: BookmarkEntity): Long {
        val id = if (bookmark.id > 0L) bookmark.id else (nextId())
        mutate { list -> list + bookmark.copy(id = id) }
        return id
    }

    /** 同一个链接已经收藏过？返回已有条目（用来做「已收藏」标记 / 避免重复收藏）。 */
    fun findByLink(link: String): BookmarkEntity? =
        _all.value.firstOrNull { it.link.trim() == link.trim() }

    fun updateCategory(id: Long, category: String) =
        mutate { list -> list.map { if (it.id == id) it.copy(category = category) else it } }

    fun updateHomePinned(id: Long, pinned: Boolean) =
        mutate { list -> list.map { if (it.id == id) it.copy(homePinned = pinned) else it } }

    fun updateHomeLabel(id: Long, label: String) =
        mutate { list -> list.map { if (it.id == id) it.copy(homeLabel = label) else it } }

    /** 解析成功后回填标题 / 平台（标题为空时才写，避免覆盖用户改过的名字）。 */
    fun backfillTitle(id: Long, title: String, platform: String) =
        mutate { list ->
            list.map {
                if (it.id == id) it.copy(
                    title = it.title.ifBlank { title },
                    platform = it.platform.ifBlank { platform },
                ) else it
            }
        }

    fun delete(id: Long) = mutate { list -> list.filterNot { it.id == id } }

    /** 现有分类（含自定义的），去重后按预设顺序排前面。 */
    fun categories(): List<String> {
        val used = _all.value.map { it.category }.filter { it.isNotBlank() }.toSet()
        val preset = BookmarkEntity.PRESET_CATEGORIES.filter { it in used }
        val custom = (used - preset.toSet()).sorted()
        return preset + custom
    }

    private fun nextId(): Long = (_all.value.maxOfOrNull { it.id } ?: 0L) + 1L
}
