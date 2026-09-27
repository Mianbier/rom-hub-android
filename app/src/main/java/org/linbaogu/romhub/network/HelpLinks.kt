package org.linbaogu.romhub.network

/**
 * 外链与开源鸣谢数据。
 *
 * 许可证是从各项目的 LICENSE 文件 / Maven POM 里逐条实读的，不是猜的：
 *  · Miuix              — Apache-2.0（POM 的 <licenses> 明确写着）
 *  · AndroidLiquidGlass — Apache-2.0
 *  · KernelSU           — GPL-3.0（LICENSE 首行 "GNU GENERAL PUBLIC LICENSE Version 3"）
 *  · HyperCeiler        — AGPL-3.0（LICENSE 首行 "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"）
 */
object HelpLinks {

    const val HOME = "https://rom.linbaogu.dpdns.org/"

    /**
     * 开发者账号审核后台 —— **只给站长自己用**。
     *
     * 刻意不在 App 里放任何入口：这是后台管理页，不该出现在用户端。
     * 站长自己记这个短地址就行（服务端已加别名，比 /api/dev/console 好输）：
     *   https://rom.linbaogu.dpdns.org/dev-console
     */
    const val DEV_CONSOLE = "https://rom.linbaogu.dpdns.org/dev-console"

    /** 本站源码仓库 —— 用户之后把地址给我，替换这一行即可 */
    const val SOURCE_REPO = "https://github.com/Mianbier/rom-hub-android"

    const val LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html"

    /** 开源许可与鸣谢全文（线上版；App 的「关于」页里也有一份） */
    val NOTICE: String get() = "$SOURCE_REPO/blob/main/NOTICE.md"
}

data class OssItem(
    val name: String,
    val license: String,
    val url: String,
    val author: String = "",
    val note: String = "",
    /** true = 只作设计参照，未直接链接其代码 */
    val referenceOnly: Boolean = false,
)

object OssCredits {

    /** 许可为 Apache-2.0、被直接依赖的第三方库 */
    val libraries: List<OssItem> = listOf(
        OssItem(
            name = "Miuix (compose-miuix-ui)",
            license = "Apache License 2.0",
            url = "https://github.com/compose-miuix-ui/miuix",
            author = "compose-miuix-ui",
            note = "整套 HyerOS 风格组件：主题、卡片、按钮、输入框、图标、模糊（miuix-blur）；" +
                    "底栏胶囊与极光背景的原始实现也来自其示例（IosLiquidGlassNavigationBar / OS3 背景特效）",
        ),
        OssItem(
            name = "AndroidLiquidGlass",
            license = "Apache License 2.0",
            url = "https://github.com/Kyant0/AndroidLiquidGlass",
            author = "Kyant0",
            note = "液态玻璃折射的 SDF 位移算法（Lens 着色器）、薄玻璃高光、内阴影、vibrancy",
        ),
        OssItem(
            name = "AndroidX / Jetpack Compose",
            license = "Apache License 2.0",
            url = "https://cs.android.com/androidx/platform/frameworks/support",
            author = "The Android Open Source Project",
            note = "Compose UI、Foundation、Material3、Activity、Lifecycle、WorkManager、WebKit",
        ),
        OssItem(
            name = "Kotlin & kotlinx",
            license = "Apache License 2.0",
            url = "https://github.com/JetBrains/kotlin",
            author = "JetBrains",
            note = "Kotlin 语言、kotlinx.coroutines、kotlinx.serialization",
        ),
        OssItem(
            name = "OkHttp",
            license = "Apache License 2.0",
            url = "https://github.com/square/okhttp",
            author = "Square, Inc.",
            note = "HTTP 客户端",
        ),
        OssItem(
            name = "Coil",
            license = "Apache License 2.0",
            url = "https://github.com/coil-kt/coil",
            author = "Coil Contributors",
            note = "机型图片加载",
        ),
        OssItem(
            name = "Android Open Source Project",
            license = "Apache License 2.0",
            url = "https://source.android.com/",
            author = "The Android Open Source Project",
            note = "Android 平台本身（RuntimeShader / AGSL 图形着色语言）",
        ),
    )

    /** 许可为 GPL / AGPL、被移植或作为直接来源的项目 */
    val gplSources: List<OssItem> = listOf(
        OssItem(
            name = "KernelSU",
            license = "GNU General Public License v3.0",
            url = "https://github.com/tiann/KernelSU",
            author = "weishu (tiann) 及 KernelSU 贡献者",
            note = "底栏 FloatingBottomBar、DampedDragAnimation、InteractiveHighlight、" +
                    "DragGestureInspector、liquid 模糊包 与 极光背景 effect 包，" +
                    "均由本项目的 KernelSU 实现移植而来",
        ),
        OssItem(
            name = "HyperCeiler",
            license = "GNU Affero General Public License v3.0",
            url = "https://github.com/ReChronoRain/HyperCeiler",
            author = "ReChronoRain 及 HyperCeiler 贡献者",
            note = "「关于」页面的整体布局与背景特效（bg_frag.glsl 及其全套参数）以 HyperCeiler 的关于页为蓝本",
        ),
    )

    /** 数据来源 */
    val dataSources: List<OssItem> = listOf(
        OssItem(
            name = "hyperos.fans",
            license = "第三方站点数据",
            url = "https://hyperos.fans/",
            note = "机型库与机型图片",
        ),
        OssItem(
            name = "xiaomirom.com",
            license = "第三方站点数据",
            url = "https://xiaomirom.com/",
            note = "ROM 版本索引",
        ),
        OssItem(
            name = "HyperOS 更新 / MiROMs HUB / Mi Firmware",
            license = "第三方站点数据",
            url = "https://rom.linbaogu.dpdns.org/",
            note = "版本号、直链与发布时间交叉校验",
        ),
        OssItem(
            name = "小米官方 OTA / 小米社区",
            license = "权利人所有",
            url = "https://www.mi.com/",
            note = "官方固件直链与官方公告",
        ),
    )

    val all: List<OssItem> get() = libraries + gplSources + dataSources
}
