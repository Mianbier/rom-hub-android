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
)

@Serializable
data class RomVersionListResp(val total: Int = 0, val items: List<RomVersion> = emptyList())

@Serializable
data class RomUpdate(
    val id: Long = 0,
    val codename: String = "",
    val region: String = "",
    val branch: String = "",
    val oldVersion: String = "",
    val newVersion: String = "",
    val versionId: Long = 0,
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
    val username: String,
    val password: String,
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
)
