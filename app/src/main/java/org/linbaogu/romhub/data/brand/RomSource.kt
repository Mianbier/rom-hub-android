package org.linbaogu.romhub.data.brand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * 多品牌 ROM 数据源的统一入口。
 *
 * 三种数据来源，各有各的坑，这里负责编排：
 *
 * | 来源 | 用途 | 状态 |
 * |---|---|---|
 * | [OpusIndex] | 机型+ 版本**清单**（1159 机型 / 35968 版本） | 免登录免签名，但要带 Referer |
 * | [OpusResolver] | 清单里**任意历史版本**的直链 | 需签名，一次算对，重试会封 IP |
 * | [ColorOtaApi] / [VivoOtaApi] | 官方 OTA **最新版**直链 | 最稳，实测跑通，直链不过期 |
 *
 * ### 下载一个版本时的决策顺序
 *
 * 1. 版本自带 `recoveryUrl` / `fastbootUrl` → 直接用（ColorOS 系部分历史包）
 * 2. 该条目 `needsResolve` → 调 [OpusResolver] 现取
 * 3. Opus 拿不到 → 退回官方接口查最新版（并明确告诉用户拿到的是最新版，不是他选的那版）
 *
 * 三条路都失败时**如实报错**，不假装成功、不给占位链接。
 */
class RomSource(
    cacheDir: File? = null,
    private val client: OkHttpClient = defaultSharedClient(),
) {
    private val index = OpusIndex(cacheDir, client)
    private val resolver = OpusResolver(client)
    private val colorOta = ColorOtaApi(client)
    private val vivoOta = VivoOtaApi(client)

    /**
     * 最近一次官方接口失败的原因。
     *
     * 官方接口返回 null 时有两种完全不同的含义（没这个机型 / 接口不放行），
     * 不记下来的话上层只能笼统报「没查到」，用户没法判断该不该重试。
     * 只在单次 [resolveLink] 内使用，不是并发安全的 —— resolveLink 整体
     * 挂在 `Dispatchers.IO` 上，但用户不会同时点两个下载，所以够用。
     */
    private var officialError: String = ""

    // ------------------------------------------------------------- 清单

    /**
     * 品牌 → 机型列表。
     *
     * ⚠️ **必须合并，不能二选一**（2026-10-04 用户实测发现）：
     * 原来写成「opusrom 有数据就只用它的，没有才用兜底表」，
     * 结果内置表里新补的 iQOO 15 / 16 / Z11 系列**永远显示不出来** ——
     * 因为 opusrom 正常在线，它就永远赢，兜底表那段代码根本不会执行。
     * 源站本身更新有滞后（新机常常晚一两个月才收录），光靠它永远缺新机。
     *
     * 所以现在的规则是：**在线清单 ∪ 内置表**，按机型名去重，在线的优先。
     * 这样源站更新了会自动覆盖内置的旧记录，源站没更新的新机也不会丢。
     */
    suspend fun devicesOf(brand: Brand): List<DeviceEntry> = withContext(Dispatchers.IO) {
        val online = runCatching { index.brandDevices(brand.key) }.getOrNull()
        mergeDevices(
            online = online.orEmpty(),
            brand = brand,
        )
    }

    /**
     * 强制刷新某品牌的机型列表（跳过 6 小时磁盘缓存，重新拉源站）。
     *
     * 源站新增机型（比如 ColorOS 17 发布后）只能靠这个进来 ——
     * 否则用户看到的永远是 6 小时前那份列表。
     *
     * 拉不到（离线 / 源站抽风）时**不抛异常**，回落到缓存列表：
     * 刷新失败不该让用户从列表页退出去。
     */
    suspend fun refreshDevices(brand: Brand): List<DeviceEntry> = withContext(Dispatchers.IO) {
        val fresh = runCatching { index.brandDevices(brand.key, forceRefresh = true) }.getOrNull()
        val online = fresh?.takeIf { it.isNotEmpty() }
        mergeDevices(
            online = online.orEmpty(),
            brand = brand,
        )
    }

    /**
     * 在线清单与内置兜底表合并。
     *
     * 去重键用「机型名 + 系列」而不是只按名字：同一台机器在源站和内置表里
     * 系列名可能不同（如 `iQOO 15` vs `iQOO 数字`），只按名字去重会漏。
     *
     * 内置表里的条目 `source` 标 [Source.FALLBACK]，界面上要能看出数据来源，
     * 免得用户以为那是源站给的完整版本历史（其实只有占位版本 + 实时解析）。
     */
    private fun mergeDevices(
        online: List<OpusDeviceFull>,
        brand: Brand,
    ): List<DeviceEntry> {
        if (online.isEmpty()) {
            return FallbackCatalog.asOpusDevices(brand.key, brand.nameZh).map {
                it.toEntry(source = Source.FALLBACK)
            }
        }

        val merged = LinkedHashMap<String, DeviceEntry>(online.size + 32)
        online.forEach { merged[it.name + "|" + it.series] = it.toEntry(Source.ONLINE) }

        // 内置表只补在线清单里没有的（PD 号 + 机型名双重判重：
        // 名字相同但 PD 号不同说明是不同机器，都该留着）
        val onlineNames = online.mapTo(HashSet()) { it.name.trim() }
        val onlinePd = online.asSequence()
            .flatMap { it.versions.asSequence() }
            .mapNotNull { Regex("(?:D)?PD\\d+[A-Z]?").find(it.romName)?.value }
            .toHashSet()

        FallbackCatalog.asOpusDevices(brand.key, brand.nameZh).forEach { fb ->
            val fbPd = Regex("(?:D)?PD\\d+[A-Z]?").find(fb.versions.firstOrNull()?.romName.orEmpty())?.value
            val dupByName = fb.versions.any { v -> v.apiDevice in onlineNames }
            val dupByPd = fbPd != null && fbPd in onlinePd
            if (dupByName || dupByPd) return@forEach
            // 一个 FallbackModel 可能有多个机型名，逐个建条目，键用第一个名字
            val entry = fb.toEntry(Source.FALLBACK)
            merged[entry.name + "|" + entry.series] = entry
        }

        return merged.values.toList()
    }

    /** 某个机型的全部版本。 */
    suspend fun versionsOf(brand: Brand, device: DeviceEntry): List<VersionEntry> =
        withContext(Dispatchers.IO) {
            if (device.source == Source.ONLINE) {
                val full = runCatching { index.brandDevices(brand.key) }.getOrNull()
                val hit = full?.firstOrNull {
                    it.name == device.name && it.series == device.series
                }
                if (hit != null) return@withContext hit.versions.map { it.toEntry() }
            }
            // 兜底来源的机型，versions 已经是 VersionEntry 了
            device.versions
        }

    // ------------------------------------------------------------- 取直链

    /**
     * 解析一个版本的下载直链。
     *
     * @param fallbackToOfficial Opus 解析失败时，是否退回官方接口查最新版。
     *   默认 true —— 对用户来说拿到能下的包比严格版本匹配更有用，
     *   但返回结果里的 [ResolvedLink.isVersionExact] 会如实标false，界面要提示。
     */
    suspend fun resolveLink(
        brand: Brand,
        version: VersionEntry,
        fallbackToOfficial: Boolean = true,
    ): ResolvedLink = withContext(Dispatchers.IO) {
        //1) 静态数据里本来就带直链
        version.directUrl?.let {
            return@withContext ResolvedLink(
                url = it,
                via = Via.STATIC,
                isVersionExact = true,
                note = "来自静态数据",
            )
        }

        // 2) opusrom 签名解析（能拿到任意历史版本）
        //
        // ⚠️ 内置机型**必须跳过这一步**（2026-10-04 实测确认）：
        // opusrom 的 /api/resolve 只认它自己静态库里的条目。内置表里的新机
        // （iQOO 15 / 16 等）源站还没收录，拿必 502 `{"error":"解析失败"}`。
        // 硬试不仅必然失败，还会白等一次签名 challenge。
        val isFallback = version.isFallback
        val opusResult = if (isFallback) {
            Result.failure(OpusException("该机型尚未被在线数据源收录"))
        } else {
            runCatching { resolver.resolve(version.opus) }
        }
        opusResult.getOrNull()?.let {
            return@withContext ResolvedLink(
                url = it,
                via = Via.OPUS,
                isVersionExact = true,
                note = "经 opusrom 解析",
            )
        }

        // 3) 官方接口兜底（只能给最新版）
        if (!fallbackToOfficial) {
            throw OpusException(
                opusResult.exceptionOrNull()?.message
                    ?: "静态数据里没有直链，解析接口也不可用",
            )
        }

        // 这个品牌压根没有官方接口就别写「取直链失败」了，
        // 直说是接口不支持，避免用户以为是自己手机的问题。
        if (brand.family != BrandFamily.ORIGIN_OS && brand.family != BrandFamily.COLOR_OS) {
            throw OpusException(
                opusResult.exceptionOrNull()?.message?.let { "$it；" }.orEmpty() +
                    "「${brand.nameZh}」暂时没有可用的官方 OTA 接口，" +
                    "App 无法直接拿到官方直链",
            )
        }

        // 上面的品牌校验已经挡掉了非 OriginOS / ColorOS 的情况，
        // 所以这里 when 是穷尽的，不需要 else。
        val official = when (brand.family) {
            BrandFamily.ORIGIN_OS -> officialForOriginOs(version)
            BrandFamily.COLOR_OS -> officialForColorOs(version)
        }

        if (official == null) {
            // 官方接口存在但没给出包 —— 这是最常见的情况，必须和
            // 「没有接口」区分开，并把接口给的真实原因带出来，
            // 否则报错语会误导用户反复重试。
            throw OpusException(
                buildString {
                    if (isFallback) {
                        append("「${version.opus.apiDevice}」是内置机型，")
                        append("在线数据源尚未收录，官方 OTA 接口也暂未提供该机型的推送包。")
                    } else {
                        append(opusResult.exceptionOrNull()?.message ?: "在线解析接口不可用")
                        append("；")
                    }
                    append(officialError.ifBlank { "官方 OTA 接口未查到该机型的可推送版本" })
                },
            )
        }

        ResolvedLink(
            url = official.url,
            via = Via.OFFICIAL,
            isVersionExact = official.isExact,
            note = official.note,
            sizeText = official.sizeText,
        )
    }

    /**
     * 官方 OriginOS 接口。
     *
     * `swVer` 传**当前装着的版本**，接口返回「能升到哪」。
     * 我们把用户选的那个历史版本传进去，命中就说明服务方还留着这条升级路线。
     *
     * 失败时通过 [officialError] 把真实原因带出去 —— 之前一律 `return null`，
     * 上层只能报「没查到」，用户完全看不到到底是「接口不放行」还是「真没这机型」。
     */
    private suspend fun officialForOriginOs(
        version: VersionEntry,
    ): OfficialHit? {
        val model = originOsModelOf(version)
        if (model.isBlank()) {
            officialError = "这个版本里识别不出 vivo 软件型号（PD 号）"
            return null
        }
        val r = runCatching {
            vivoOta.query(model = model, deviceModel = publicModelOf(model), currentVer = bareVersionOf(version))
        }.getOrElse {
            officialError = "官方接口请求失败：${it.message ?: it.javaClass.simpleName}"
            return null
        }
        if (!r.success || r.downloadUrl.isBlank()) {
            officialError = r.error.ifBlank { "官方接口没返回可下载的包" }
            return null
        }
        officialError = ""
        return OfficialHit(
            url = r.downloadUrl,
            sizeText = r.sizeText,
            isExact = r.version == bareVersionOf(version),
            note = if (r.version == bareVersionOf(version)) "官方接口（版本一致）"
            else "官方接口（返回的是 ${r.version}）",
        )
    }

    /**
     * 取 OriginOS 的软件型号（`PD2415` 这种）。
     *
     * 源站的版本串是 `PD2217_MA_12.1.14.5.W10.V000L1` 整串，PD 号就在里面，
     * 而 `oplusModel` 字段源站是留空的（实测 47/47 条全空）。
     * 所以这里以「从版本串反解」为主、`oplusModel` 为辅，两条路都能拿到。
     */
    private fun originOsModelOf(version: VersionEntry): String {
        Regex("(?:D)?PD\\d+[A-Z]?").find(version.opus.version)?.let { return it.value }
        Regex("(?:D)?PD\\d+[A-Z]?").find(version.opus.romName)?.let { return it.value }
        return version.opus.oplusModel
    }

    /**
     * 官方接口要的 `swVer` 是**裸版本号**（`16.1.16.5.W10`），
     * 不带 `PD2217_MA_` 前缀。源站整串直接塞进去服务端认不出来。
     */
    private fun bareVersionOf(version: VersionEntry): String =
        version.opus.version.substringAfter("_MA_", version.opus.version)

    /** 官方 ColorOS 接口。 */
    private suspend fun officialForColorOs(
        version: VersionEntry,
    ): OfficialHit? {
        val model = version.opus.oplusModel.ifBlank { colorOsModelOf(version) }
        if (model.isBlank()) {
            officialError = "这个版本里识别不出 ColorOS 软件型号"
            return null
        }
        val r = runCatching { colorOta.query(model, version.opus.version) }.getOrElse {
            officialError = "官方接口请求失败：${it.message ?: it.javaClass.simpleName}"
            return null
        }
        if (!r.success) {
            officialError = r.error.ifBlank { "官方接口没返回可推送的版本" }
            return null
        }
        val full = r.fullPackage
        if (full == null || full.link.isBlank()) {
            officialError = "官方接口只返回了升级说明，没有全量包直链"
            return null
        }
        officialError = ""
        return OfficialHit(
            url = full.link,
            sizeText = full.sizeText,
            isExact = full.version.contains(version.opus.version),
            note = if (full.isExpired) "官方接口（链接已过期，建议改用最新版本）"
            else "官方接口（ColorOS 官方 OTA）",
        )
    }

    /**
     * ColorOS 的软件型号（`PJZ110` 这种）从版本串里反解。
     *
     * 源站的 ColorOS 版本串形如 `PJZ110_11.0.6.202...(0)_...`，
     * 第一段就是型号。取不到就返回空，交给上层如实报「拿不到」。
     */
    private fun colorOsModelOf(version: VersionEntry): String {
        val head = version.opus.version.substringBefore("_")
        return if (Regex("^[A-Z]{2,4}\\d{3,5}$").matches(head)) head else ""
    }

    /**
     * OriginOS 接口的 `public_model`（设备型号，形如 `V2418A`）。
     *
     * 规则是从 `model`（软件型号 `PD2415`）推的：vivo 官方一直用
     * `V` + 去掉首位 `P` 后的数字 + `A`。实测 `PD2408` 对应 `V2408A`，
     * 而 `PD2408` 又是官方接口直接认的 model，所以这层转换只需在
     * 接口返回版本与请求不一致时用作退路，主路径仍以 model 为准。
     */
    private fun publicModelOf(model: String): String {
        val digits = model.removePrefix("PD").removePrefix("DPD").filter { it.isDigit() }
        return if (digits.isBlank()) model else "V${digits}A"
    }

    companion object {
        /** 共用一个连接池，省得每次新建。 */
        fun defaultSharedClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

// ------------------------------------------------------------------ 门面模型

/** 数据来源，界面上要标出来，别让用户以为在线数据。 */
enum class Source { ONLINE, FALLBACK }

/** 直链是怎么来的。 */
enum class Via { STATIC, OPUS, OFFICIAL }

data class ResolvedLink(
    val url: String,
    val via: Via,
    /** false = 拿到的是最新版，不是用户选的那一版，界面必须提示。 */
    val isVersionExact: Boolean,
    val note: String = "",
    val sizeText: String = "",
)

private data class OfficialHit(
    val url: String,
    val sizeText: String,
    val isExact: Boolean,
    val note: String,
)

/** 机型条目。字段和 [OpusDeviceFull] 对齐，但额外带来源标记。 */
data class DeviceEntry(
    val name: String,
    val series: String,
    val codename: String,
    val brandKey: String,
    val versionCount: Int,
    val region: String,
    val fileType: String,
    val description: String,
    val source: Source,
    val versions: List<VersionEntry>,
) {
    val displayName: String get() = name.ifBlank { codename }
}

/** 版本条目。[directUrl] 非空表示静态数据里已带直链，可直接下。 */
data class VersionEntry(
    val version: String,
    val romName: String,
    val osFamily: String,
    val flashType: String,
    val size: String,
    val releaseDate: String,
    val region: String,
    val md5: String,
    val needsResolve: Boolean,
    val directUrl: String?,
    val opus: OpusVersion,
    /**
     * true = 来自内置兜底表（在线数据源还没收录）。
     *
     * 这类条目**不能**走 opusrom 解析接口 —— 那个接口只认自己静态库里的条目，
     * 查内置机型必然 502。所以取直链时要跳过它，直走官方接口，
     * 失败也要给用户说人话，而不是丢一个 502 出来。
     */
    val isFallback: Boolean = false,
) {
    val displayName: String get() = version.ifBlank { romName }
    val sizeText: String get() = prettySize(size)
    val canDownloadDirectly: Boolean get() = !directUrl.isNullOrBlank()
}

private fun OpusDeviceFull.toEntry(source: Source) = DeviceEntry(
    name = name,
    series = series,
    codename = codename,
    brandKey = brandKey,
    versionCount = versionCount,
    region = region,
    fileType = fileType,
    description = description,
    source = source,
    versions = versions.map { it.toEntry(isFallback = source == Source.FALLBACK) },
)

private fun OpusVersion.toEntry(isFallback: Boolean = false) = VersionEntry(
    version = version,
    romName = romName,
    osFamily = osFamily,
    flashType = flashType,
    size = size,
    releaseDate = releaseDate,
    region = region,
    md5 = md5,
    needsResolve = needsResolve,
    // 静态数据里直链字段可能叫 recoveryUrl / fastbootUrl，取第一个非空的
    directUrl = this@toEntry.directUrl.ifBlank { null },
    opus = this,
    isFallback = isFallback,
)
