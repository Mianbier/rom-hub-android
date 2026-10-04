package org.linbaogu.romhub.data.brand

/**
 * 品牌归属：决定「版本列表从哪来」和「下载直链怎么拿」。
 *
 * 关键认识（2026-10 调研结论）：
 *  - 列表用 [OpusIndex]（opusrom.top 的静态 js，免签名、免登录、9 品牌 35968 条）
 *  - 直链优先走**厂商官方 OTA 接口**（[ColorOtaApi] / [VivoOtaApi]），
 *    无任何人机验证、不会被封 IP；拿不到时再回落到 Opus 直链解析。
 */
enum class BrandFamily {
    /** 小米 / Redmi —— ROM Hub 自有数据源，走原来的设备页 */
    XIAOMI,

    /** OPPO / 一加 / 真我 —— ColorOS 官方 `component-ota-*` 接口 */
    COLOR_OS,

    /** vivo / iQOO —— OriginOS 官方 `sysupgrade.vivo.com.cn` 接口 */
    ORIGIN_OS,

    /** 魅族 / 红魔 / 联想 —— 列表走 Opus，直链回落到源站 */
    OTHER,
}

/**
 * 一个品牌。
 *
 * @param key      Opus 侧的品牌键（`data/roms_{key}.js`）
 * @param nameZh   展示名
 * @param family   数据源归属
 * @param color    品牌主色，用于品牌卡片
 */
data class Brand(
    val key: String,
    val nameZh: String,
    val family: BrandFamily,
    val color: Long,
) {
    /** 首页是否进 ROM Hub 原有的设备页（只有小米是） */
    val useNativeSource: Boolean get() = family == BrandFamily.XIAOMI
}

/**
 * 品牌清单。
 *
 * 顺序即首页展示顺序：小米打头（用户最常用），其余按 ColorOS → OriginOS → 其他排。
 * 数据量参考 Opus 的 `ROMS_SUMMARY.totals`：9 品牌 / 1159 机型 / 35968 个 ROM。
 */
object BrandCatalog {

    val all: List<Brand> = listOf(
        Brand("xiaomi", "小米", BrandFamily.XIAOMI, 0xFFFF6900),
        Brand("redmi", "Redmi", BrandFamily.XIAOMI, 0xFFFF3B30),
        Brand("oppo", "OPPO", BrandFamily.COLOR_OS, 0xFF0A6CFF),
        Brand("oneplus", "一加", BrandFamily.COLOR_OS, 0xFFEB0028),
        Brand("realme", "真我", BrandFamily.COLOR_OS, 0xFFFFC915),
        Brand("vivo", "vivo", BrandFamily.ORIGIN_OS, 0xFF415FFF),
        Brand("iqoo", "iQOO", BrandFamily.ORIGIN_OS, 0xFF0D9BFF),
        Brand("meizu", "魅族", BrandFamily.OTHER, 0xFF00A0E9),
        Brand("redmagic", "红魔", BrandFamily.OTHER, 0xFFD0021B),
        Brand("lenovo", "联想", BrandFamily.OTHER, 0xFFA51C30),
    )

    /** 首页真正要展示的（小米和 Redmi 共用一个入口，这里只留一个「小米」）。 */
    val homeBrands: List<Brand> get() = all.filter { it.key != "redmi" }

    fun byKey(key: String): Brand? = all.firstOrNull { it.key.equals(key, ignoreCase = true) }
}
