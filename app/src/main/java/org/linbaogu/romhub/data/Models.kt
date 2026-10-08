package org.linbaogu.romhub.data

import kotlinx.serialization.Serializable

// 说明：JSON 键是下划线风格，客户端统一用 JsonNamingStrategy.SnakeCase 映射，
// 所以这里写驼峰即可（见 Api.kt 的 RomJson）。

@Serializable
data class DeviceItem(
    val code: String = "",
    val nameZh: String = "",
    val nameEn: String = "",
    val series: String = "",
    val brand: String = "",
    val brandZh: String = "",
    val supports: String = "",
    val android: String = "",
    val image: String = "",
    val updatedAt: String = "",
    val romCount: Int = 0,
    val lastDate: String = "",
    val latestVersion: String = "",
    val latestRegion: String = "",
    val latestBranch: String = "",
) {
    val displayName: String get() = nameZh.ifBlank { nameEn.ifBlank { code } }

    /**
     * 关键词匹配。
     *
     * 接口挂掉时用 CDN 快照在本地筛（快照是全量表），字段范围和服务端一致：
     * 代号、中英文名、系列、品牌。
     */
    fun matches(keyword: String): Boolean {
        val k = keyword.trim().lowercase()
        if (k.isBlank()) return true
        return code.lowercase().contains(k) ||
            nameZh.lowercase().contains(k) ||
            nameEn.lowercase().contains(k) ||
            series.lowercase().contains(k) ||
            brand.lowercase().contains(k) ||
            brandZh.lowercase().contains(k)
    }
}

@Serializable
data class DeviceListResp(val total: Int = 0, val items: List<DeviceItem> = emptyList())

@Serializable
data class RomItem(
    val id: Long = 0,
    val codename: String = "",
    val region: String = "",
    val branch: String = "",
    val name: String = "",
    val pageUrl: String = "",
    val latestVersion: String = "",
    val latestDate: String = "",
    val android: String = "",
    val osType: String = "",
    val enabled: Int = 1,
    val updatedAt: String = "",
    val regionZh: String = "",
    val branchZh: String = "",
    val versionCount: Int = 0,
)

@Serializable
data class DeviceDetailResp(
    val device: DeviceItem = DeviceItem(),
    val roms: List<RomItem> = emptyList(),
)

@Serializable
data class Mirror(
    val name: String = "",
    val recovery: String = "",
    val fastboot: String = "",
) {
    val hasAny: Boolean get() = recovery.isNotBlank() || fastboot.isNotBlank()
}

@Serializable
data class RomVersion(
    val id: Long = 0,
    val codename: String = "",
    val region: String = "",
    val branch: String = "",
    val version: String = "",
    val downloadPage: String = "",
    val fastbootUrl: String = "",
    val recoveryUrl: String = "",
    val filenameFast: String = "",
    val filenameRec: String = "",
    val android: String = "",
    val osType: String = "",
    val sizeText: String = "",
    val romDate: String = "",
    val resolved: Int = 0,
    val resolvedAt: String = "",
    val detectedAt: String = "",
    val firstSeen: String = "",
    val lastSeen: String = "",
    val goneAt: String? = null,
    val publicAt: String? = null,
    val resumedAt: String? = null,
    val regionZh: String = "",
    val branchZh: String = "",
    val mirrors: List<Mirror> = emptyList(),
    val speedSig: String = "",
)

@Serializable
data class RomVersionListResp(val total: Int = 0, val items: List<RomVersion> = emptyList())

@Serializable
data class FastLinkResp(val url: String = "")

@Serializable
data class RomUpdate(
    val id: Long = 0,
    val codename: String = "",
    val region: String = "",
    val branch: String = "",
    val oldVersion: String = "",
    val newVersion: String = "",
    val versionId: Long = 0,
    val portId: Long = 0,
    val detectedAt: String = "",
    val kind: String = "",
    val deviceName: String = "",
    val state: String = "",
    val stateText: String = "",
    val regionZh: String = "",
    val branchZh: String = "",
    val shortDate: String = "",
    val kindZh: String = "",
    val desc: String = "",
)

@Serializable
data class RomUpdateListResp(
    val total: Int = 0,
    val items: List<RomUpdate> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val serverTime: String = "",
)

@Serializable
data class PortPackage(
    val id: Long = 0,
    val codename: String = "",
    val deviceName: String = "",
    val author: String = "",
    val source: String = "",
    val title: String = "",
    val portType: String = "",
    val portKind: String = "",
    val version: String = "",
    val shareUrl: String = "",
    val platform: String = "",
    val platformZh: String = "",
    val fileName: String = "",
    val fileSize: String = "",
    val publishedAt: String = "",
    val notice: String = "",
    val submittedBy: String = "",
    val enabled: Int = 1,
    val createdAt: String = "",
    val shortDate: String = "",
)

@Serializable
data class PortListResp(val items: List<PortPackage> = emptyList())

@Serializable
data class PortDetailResp(val item: PortPackage = PortPackage())

@Serializable
data class Notice(
    val id: Long = 0,
    val source: String = "",
    val externalId: String = "",
    val title: String = "",
    val summary: String = "",
    val author: String = "",
    val url: String = "",
    val comments: Int = 0,
    val likes: Int = 0,
    val publishedAt: String = "",
    val fetchedAt: String = "",
)

@Serializable
data class NoticeListResp(val total: Int = 0, val items: List<Notice> = emptyList())

@Serializable
data class RomStats(
    val devices: Int = 0,
    val roms: Int = 0,
    val versions: Int = 0,
    val resolved: Int = 0,
    val updates: Int = 0,
    val lastSyncAt: String = "",
)

// ------------------------------------------------------------------ 开发者

@Serializable
data class DevApplyReq(
    /** 账号 —— 站长要求填**邮箱**。 */
    val username: String,
    val password: String,
    /** 名称。 */
    val name: String = "",
    /** 酷安名 —— 站长主要靠这个认人。 */
    val coolapk: String = "",
    /** 旧的「联系方式 / 说明」，保留兼容老服务端。 */
    val contact: String = "",
    val note: String = "",
)

@Serializable
data class DevLoginReq(val username: String, val password: String)

@Serializable
data class DevLoginResp(
    val ok: Boolean = false,
    val token: String = "",
    val username: String = "",
    val role: String = "dev",
    val error: String = "",
)

@Serializable
data class DevApplyResp(
    val ok: Boolean = false,
    val id: Long = 0,
    val status: String = "",
    val error: String = "",
)

@Serializable
data class DevMeResp(
    val ok: Boolean = false,
    val username: String = "",
    val status: String = "",
    val error: String = "",
)

@Serializable
data class PortUploadReq(
    val codename: String = "",
    val deviceName: String = "",
    val author: String = "",
    val source: String = "",
    val title: String = "",
    val portType: String = "",
    val portKind: String = "",
    val version: String = "",
    val shareUrl: String,
    val fileName: String = "",
    val fileSize: String = "",
    val publishedAt: String = "",
    val notice: String = "",
)

@Serializable
data class PortUploadResp(
    val ok: Boolean = false,
    val id: Long = 0,
    val duplicated: Boolean = false,
    val error: String = "",
)

@Serializable
data class PortParseFile(
    val name: String = "",
    val sizeHuman: String = "",
    val createdAt: String = "",
    val version: String = "",
)

@Serializable
data class PortParseResp(
    val ok: Boolean = false,
    val platform: String = "",
    val platformZh: String = "",
    val files: List<PortParseFile> = emptyList(),
    val error: String = "",
)

/** 网盘分享镜像（App 自检更新里可选的下载方式之一）。 */
@Serializable
data class UpdateMirror(
    val name: String = "",
    val url: String = "",
    val code: String = "",
)

/** 更新公告：强制弹窗、不可跳过（例如下架某版本、要求重装）。 */
@Serializable
data class UpdateNotice(
    val title: String = "",
    val body: String = "",
    /** info / warn / critical —— critical 视为不可跳过。 */
    val level: String = "info",
    val at: String = "",
)

/** 服务端 `server/data/app_update.json` 的内容（GET /api/app/update）。 */
@Serializable
data class AppUpdateInfo(
    val ok: Boolean = false,
    val hasUpdate: Boolean = false,
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String = "",
    val notes: String = "",
    val force: Boolean = false,
    val updatedAt: String = "",
    /** 网盘分享：夸克/蓝奏/123 等，App 认得这些链接，点开直接进内置下载器。 */
    val mirrors: List<UpdateMirror> = emptyList(),
    /** 更新公告。 */
    val notice: UpdateNotice = UpdateNotice(),
    /**
     * 灰度发布：站长可以把这次推送限定在某个 versionCode 区间。
     * 不在区间内时**服务端**会把 has_update 压成 false 并带上 out_of_range = true。
     * 客户端据此把「已是最新版本」换成「本次推送不适用于当前版本」，
     * 否则用户会得到一个假结论（明明有新版，却告诉他已是最新）。
     */
    val outOfRange: Boolean = false,
) {
    /** 有网盘镜像也算有更新（可能压根没直链）。 */
    val effectiveHasUpdate: Boolean
        get() = hasUpdate || mirrors.isNotEmpty() || notice.title.isNotBlank() || notice.body.isNotBlank()

    /** 公告级别到 critical 时不可跳过，和 force 一样强。 */
    val noticeBlocking: Boolean
        get() = notice.level == "critical" && (notice.title.isNotBlank() || notice.body.isNotBlank())
}


/**
 * 云端下发的用户协议 / 隐私政策。
 *
 * 内容存在服务端并发布到 KV（`data/legal.json`），客户端**优先读 KV** ——
 * 走 CDN，手机源站没开也能读到，还不用每次回源。
 */
@Serializable
data class LegalInfo(
    val ok: Boolean = false,
    /** 每次站长保存 +1。客户端拿它和自己「已同意」的版本比，变大了才弹窗。 */
    val version: Int = 0,
    val updatedAt: String = "",
    val terms: String = "",
    val privacy: String = "",
)
