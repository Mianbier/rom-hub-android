/*
 * 搬运自 YunX (云析) - Copyright (C) 2026 CYQawa
 * 原文件：app/src/main/kotlin/com/yunx/app/data/download/HlsRequestPolicy.kt
 * 许可：GNU Affero General Public License v3.0（见项目根 LICENSE）
 */

package org.linbaogu.romhub.download

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * HLS 各请求（播放列表 / 重定向 / 初始分片 / 媒体分片）的源与请求头策略。
 *
 * 核心约束：**凭证（Cookie / Authorization）绝不跨源转发**。
 * 分片常常落在 CDN 上（和播放列表不同域），把登录态发过去等于把账号送人。
 */
internal object HlsRequestPolicy {

    private val sensitiveHeaders = setOf(
        "authorization", "cookie", "origin", "proxy-authorization", "referer",
    )

    fun initialUrl(url: String): HttpUrl? = url.toHttpUrlOrNull()?.takeIf { it.isHttps }

    fun resolve(base: HttpUrl, candidate: String): HttpUrl? =
        base.resolve(candidate)?.takeIf { it.isHttps }

    fun headersFor(
        target: HttpUrl,
        credentialOrigin: HttpUrl,
        headers: Map<String, String>,
    ): Map<String, String> {
        if (sameOrigin(target, credentialOrigin)) return headers
        return headers.filterKeys { it.lowercase() !in sensitiveHeaders }
    }

    fun sameOrigin(left: HttpUrl, right: HttpUrl): Boolean =
        left.scheme == right.scheme && left.host == right.host && left.port == right.port
}
