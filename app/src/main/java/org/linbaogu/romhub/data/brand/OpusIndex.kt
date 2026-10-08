package org.linbaogu.romhub.data.brand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 「全量包提取」站（`opusrom.top`）的**静态数据**索引。
 *
 * 这个站本身是纯静态的，数据就明摆在 `/data/` 下，**不需要登录、不需要签名**：
 *  - `data/summary.js`      → `window.ROMS_SUMMARY = {brands, totals, devices[]}`
 *    含 **全部 1159 个机型**的摘要（品牌、系列、机型名、代号、版本数），
 *    足够撑起「品牌 → 机型」两级页面。
 *  - `data/roms_{brand}.js` → `window.ROMS_DB = (...).concat([...])`
 *    该品牌全部机型 + **每个机型的完整 versions[]**（版本号 / 大小 / 日期 / 区域）。
 *
 * ⚠️ 两个坑（都踩过）：
 *  1. **必须带 `Referer: https://opusrom.top/`**，否则 403。
 *  2. 品牌文件是 `.concat()` 形式，**一个文件里可能有多条赋值语句**，不能只取第一个数组。
 *
 * 这里的 `versions[]` **只有元信息、没有下载直链**（直链要另外解析，见 OpusResolver）。
 */
class OpusIndex(
    private val cacheDir: File? = null,
    private val client: OkHttpClient = defaultClient(),
) {

    // ------------------------------------------------------------- 对外

    /** 全部品牌 + 全部机型摘要。 */
    suspend fun summary(forceRefresh: Boolean = false): OpusSummary = withContext(Dispatchers.IO) {
        val text = fetchCached(SUMMARY_FILE, "https://$HOST/data/summary.js", forceRefresh)
        val obj = parseSingleObject(text, "ROMS_SUMMARY")
            ?: throw OpusException("机型索引格式变了：认不出 ROMS_SUMMARY")

        val brands = (obj["brands"] as? JsonArray).orEmpty().mapNotNull { el ->
            val b = el as? JsonObject ?: return@mapNotNull null
            OpusBrandStat(
                key = b.str("key"),
                name = b.str("name"),
                file = b.str("file"),
                devices = b.str("devices").toIntOrNull() ?: 0,
                roms = b.str("roms").toIntOrNull() ?: 0,
            )
        }

        val devices = (obj["devices"] as? JsonArray).orEmpty().mapNotNull { el ->
            val d = el as? JsonObject ?: return@mapNotNull null
            OpusDevice(
                brandKey = d.str("brandKey"),
                brand = d.str("brand"),
                series = d.str("series"),
                name = d.str("deviceName"),
                codename = d.str("codename"),
                type = d.str("type"),
                latestRomName = d.str("romName"),
                region = d.str("region"),
                fileType = d.str("fileType"),
                versionCount = d.str("versionCount").toIntOrNull() ?: 0,
                tags = (d["tags"] as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                },
                releaseDate = d.str("releaseDate"),
            )
        }

        OpusSummary(brands = brands, devices = devices)
    }

    /**
     * 某品牌下**全部机型 + 每个机型的完整版本列表**。
     *
     * 文件较大（小米那份含 2.2 万个 ROM，约十几 MB），所以：
     *  - 结果按品牌缓存到磁盘，第二次直接读本地
     *  - 只在真正进入该品牌页面时才拉
     */
    suspend fun brandDevices(
        brandKey: String,
        forceRefresh: Boolean = false,
    ): List<OpusDeviceFull> = withContext(Dispatchers.IO) {
        val stat = summary().brands.firstOrNull { it.key.equals(brandKey, ignoreCase = true) }
            ?: throw OpusException("未知品牌：$brandKey")
        val fileName = stat.file.ifBlank { "roms_$brandKey.js" }

        val text = fetchCached("roms_$brandKey.js", "https://$HOST/data/$fileName", forceRefresh)
        val arrays = parseAllArrays(text, "ROMS_DB")
        if (arrays.isEmpty()) throw OpusException("$fileName 格式变了：认不出 ROMS_DB")

        arrays.asSequence()
            .flatMap { it.asSequence() }
            .mapNotNull { el -> el as? JsonObject }
            .map { d ->
                OpusDeviceFull(
                    brandKey = d.str("brandKey").ifBlank { brandKey },
                    brand = d.str("brand"),
                    series = d.str("series"),
                    name = d.str("deviceName"),
                    codename = d.str("codename"),
                    type = d.str("type"),
                    region = d.str("region"),
                    fileType = d.str("fileType"),
                    versionCount = d.str("versionCount").toIntOrNull() ?: 0,
                    description = d.str("description"),
                    versions = (d["versions"] as? JsonArray).orEmpty().mapNotNull { v ->
                        val o = v as? JsonObject ?: return@mapNotNull null
                        OpusVersion(
                            version = o.str("version").ifBlank { o.str("romName") },
                            romName = o.str("romName"),
                            osFamily = o.str("osFamily"),
                            flashType = o.str("flashType").ifBlank { o.str("recoveryFlash") },
                            size = o.str("size"),
                            releaseDate = o.str("releaseDate"),
                            region = o.str("region"),
                            md5 = o.str("md5"),
                            // 少数 ColorOS 系条目自带直链（阿里云 allawnfs），有就别再走解析接口
                            recoveryUrl = o.str("recoveryUrl"),
                            fastbootUrl = o.str("fastbootUrl"),
                            needsResolve = o["needsResolve"]?.let {
                                (it as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
                            } ?: false,
                            // 直链解析要用的一整套参数，原样带过来
                            apiBrand = o.str("apiBrand"),
                            apiDevice = o.str("apiDevice"),
                            apiMajor = o.str("apiMajor"),
                            apiFlash = o.str("apiFlash"),
                            oplusModel = o.str("oplusModel"),
                        )
                    },
                )
            }
            .toList()
    }

    // ------------------------------------------------------------- 取数 + 缓存

    private fun fetchCached(fileName: String, url: String, forceRefresh: Boolean): String {
        val cache = cacheDir?.let { File(it, "opus").apply { mkdirs() } }?.let { File(it, fileName) }
        if (!forceRefresh && cache != null && cache.isFile && cache.length() > 1024) {
            val age = System.currentTimeMillis() - cache.lastModified()
            // 6 小时内直接用缓存
            if (age < 6 * 3600_000L) return cache.readText()
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://$HOST/")     // ← 少了这行就 403
            .header("Accept", "*/*")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw OpusException("拉取 $fileName 失败：HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) throw OpusException("$fileName 返回空内容")
            runCatching { cache?.writeText(body) }
            return body
        }
    }

    // ------------------------------------------------------------- JS 里抠 JSON

    /**
     * 抠出 `window.<varName> = <单个对象/数组>;` 这种赋值。
     * summary.js 属于这种。
     */
    private fun parseSingleObject(text: String, varName: String): JsonObject? {
        val anchor = text.indexOf("$varName")
        if (anchor < 0) return null
        val eq = text.indexOf('=', anchor)
        if (eq < 0) return null
        val start = text.indexOf('{', eq)
        if (start < 0) return null
        val end = matchBrace(text, start, '{', '}')
        if (end < 0) return null
        return runCatching { JSON.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    /**
     * 抠出**所有** `window.<varName> = (…).concat([...])` / `= [...]` 里的数组。
     *
     * roms_{brand}.js 是分片追加的，一个文件里可能有多条 `.concat()`，
     * 只取第一个会丢掉大半数据 —— 这是实测踩到的。
     *
     * 注意不能直接 `indexOf("ROMS_DB")`：变量名本身也出现在
     * `(window.ROMS_DB || [])` 这个壳里，所以用正则锁定「赋值号 + 可选 concat 前缀 + `[`」。
     *
     * ⚠️ concat 壳里的空数组 `[]` 必须整体写成 `\[\s*\]\s*\)`，
     *    少一个 `\]` 的话整条正则永远匹配不到 —— 实测所有品牌都会解析成 0 段。
     */
    private fun parseAllArrays(text: String, varName: String): List<JsonArray> {
        val pattern = Regex(
            Regex.escape(varName) +
                """\s*=\s*(?:\(\s*[\w.]+\s*\|\|\s*\[\s*\]\s*\)\s*\.\s*concat\s*\(\s*)?\[""",
        )
        val out = mutableListOf<JsonArray>()
        for (m in pattern.findAll(text)) {
            val start = m.range.last          // 匹配串的最后一位就是那个 '['
            val end = matchBrace(text, start, '[', ']')
            if (end < 0) continue
            runCatching { JSON.parseToJsonElement(text.substring(start, end + 1)) as? JsonArray }
                .getOrNull()?.let(out::add)
        }
        return out
    }

    /** 从 [from] 起的括号配对扫描，返回匹配闭合符的下标。 */
    private fun matchBrace(text: String, from: Int, open: Char, close: Char): Int {
        var depth = 0
        var inStr = false
        var escape = false
        for (i in from until text.length) {
            val c = text[i]
            when {
                escape -> escape = false
                c == '\\' && inStr -> escape = true
                c == '"' -> inStr = !inStr
                inStr -> Unit
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    companion object {
        const val HOST = "opusrom.top"
        const val SUMMARY_FILE = "summary.js"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // 小米那份文件很大
            .retryOnConnectionFailure(true)
            .build()

        private fun JsonObject.str(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
    }
}

class OpusException(msg: String) : Exception(msg)

// ------------------------------------------------------------------ 模型

data class OpusBrandStat(
    val key: String,
    val name: String,
    val file: String,
    val devices: Int,
    val roms: Int,
)

/** summary.js 里的机型摘要（没有 versions）。 */
data class OpusDevice(
    val brandKey: String,
    val brand: String,
    val series: String,
    val name: String,
    val codename: String,
    val type: String,
    val latestRomName: String,
    val region: String,
    val fileType: String,
    val versionCount: Int,
    val tags: List<String>,
    val releaseDate: String,
) {
    val displayName: String get() = name.ifBlank { codename }
}

/** roms_{brand}.js 里的完整条目（带 versions）。 */
data class OpusDeviceFull(
    val brandKey: String,
    val brand: String,
    val series: String,
    val name: String,
    val codename: String,
    val type: String,
    val region: String,
    val fileType: String,
    val versionCount: Int,
    val description: String,
    val versions: List<OpusVersion>,
) {
    val displayName: String get() = name.ifBlank { codename }
}

data class OpusVersion(
    val version: String,
    val romName: String,
    val osFamily: String,
    val flashType: String,
    val size: String,
    val releaseDate: String,
    val region: String,
    val md5: String,
    /** 卡刷直链。ColorOS 系部分历史包自带（阿里云 allawnfs），有就不用再解析。 */
    val recoveryUrl: String = "",
    /** 线刷直链。同上。 */
    val fastbootUrl: String = "",
    /** true = 直链不在静态数据里，需要调 Opus 的解析接口现取 */
    val needsResolve: Boolean,
    val apiBrand: String,
    val apiDevice: String,
    val apiMajor: String,
    val apiFlash: String,
    val oplusModel: String,
) {
    val displayName: String get() = version.ifBlank { romName }
    val sizeText: String get() = prettySize(size)

    /** 两个直链字段里第一个非空的。 */
    val directUrl: String get() = recoveryUrl.ifBlank { fastbootUrl }
    val hasDirectUrl: Boolean get() = directUrl.isNotBlank()
}

data class OpusSummary(
    val brands: List<OpusBrandStat>,
    val devices: List<OpusDevice>,
) {
    fun devicesOf(brandKey: String): List<OpusDevice> =
        devices.filter { it.brandKey.equals(brandKey, ignoreCase = true) }

    fun brandsWithDevice(): List<OpusBrandStat> = brands.filter { it.devices > 0 }
}
