/*
 * 搬运自 YunX (云析) - Copyright (C) 2026 CYQawa
 * 原文件：app/src/main/kotlin/com/yunx/app/data/download/DownloadPlatform.kt
 * 许可：GNU Affero General Public License v3.0（见项目根 LICENSE）
 */

package org.linbaogu.romhub.download

/**
 * 下载来源平台标识 —— 用来按来源独立设置线程数（比如百度限速就得少开几条）。
 *
 * 用字符串常量而不是枚举：直接持久化进 JSON 就行，不用写序列化适配器。
 */
object DownloadPlatform {
    const val QUARK = "quark"
    const val UC = "uc"
    const val XUNLEI = "xunlei"
    const val BAIDU = "baidu"
    const val C139 = "c139"
    const val PAN123 = "pan123"
    const val PAN115 = "pan115"

    /** GitHub 仓库 / Release 下载 */
    const val GITHUB = "github"

    /** 通用/未知来源：手动粘贴的直链、ROM 官方包、App 自身更新等 */
    const val GENERIC = "generic"
}
