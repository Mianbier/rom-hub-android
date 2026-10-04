/*
 * 搬运自 YunX (云析) - Copyright (C) 2026 CYQawa
 * 原文件：app/src/main/kotlin/com/yunx/app/data/download/HttpRangePolicy.kt
 * 许可：GNU Affero General Public License v3.0（见项目根 LICENSE）
 */

package org.linbaogu.romhub.download

/**
 * `Content-Range` 响应的解析与匹配。
 *
 * 分片下载必须确认服务端**真的按我们请求的区间返回**（而不是把整个文件吐回来，或者
 * 从一个偏移处开始返回）。返回的 start 对不上就说明这个服务器不可信，得整体退化到单线程。
 */
internal object HttpRangePolicy {

    data class ContentRange(val start: Long, val end: Long, val total: Long?)

    private val pattern = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

    fun parse(value: String?): ContentRange? {
        val match = pattern.matchEntire(value?.trim().orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        if (start < 0 || end < start || (total != null && end >= total)) return null
        return ContentRange(start, end, total)
    }

    fun matches(value: String?, requestedStart: Long, requestedEnd: Long?): Boolean {
        val range = parse(value) ?: return false
        if (range.start != requestedStart) return false
        return requestedEnd == null || range.end == requestedEnd
    }
}
