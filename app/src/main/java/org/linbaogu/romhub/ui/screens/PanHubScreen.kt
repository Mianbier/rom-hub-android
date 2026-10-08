package org.linbaogu.romhub.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.pan.PanHub
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.pan.store.BookmarkStore
import org.linbaogu.romhub.ui.login.BaiduLoginScreen
import org.linbaogu.romhub.ui.login.C139LoginScreen
import org.linbaogu.romhub.ui.login.Pan115LoginScreen
import org.linbaogu.romhub.ui.login.Pan123LoginScreen
import org.linbaogu.romhub.ui.login.QuarkLoginScreen
import org.linbaogu.romhub.ui.login.UCLoginScreen
import org.linbaogu.romhub.ui.login.XunleiLoginScreen
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底栏「网盘下载器」的落地页 —— 云析的全部功能都在这里：账号 / 下载 / 收藏 / 设置。
 *
 * 为什么合成一页：这几件事本来就是一体的 —— 没登录解析不了分享链接，
 * 解析完才有东西可下，下得不顺就去设置里调线程/限速。
 * 拆成多个平级 tab 只会让用户在几个地方来回跳。
 *
 * 层级结构（重要）：登录页 / 云盘浏览页**不再在这里覆盖** —— 它们含 WebView，
 * 而本页在 `HorizontalPager` 内部，pager 的预组合会让 WebView 反复重绘（「一直闪烁」）。
 * 现在由 `AppRoot` 在 pager **之外**整页覆盖（见 `AppViewModel.panOverlay`），
 * 本页只负责把点击事件上抛。
 */
@Composable
fun PanHubScreen(
    bookmarks: BookmarkStore,
    bottomInnerPadding: Dp = 0.dp,
    /** 从外部点进来的链接：进来就直接跳「下载」段并自动开始下 */
    initialUrl: String? = null,
    onConsumed: () -> Unit = {},
    /** 跟着 [initialUrl] 一起带过来的提取码（更新弹窗的网盘镜像用） */
    initialPwd: String? = null,
    /** 外部指定的落地分段（0 账号 / 1 下载 / 2 收藏 / 3 设置），消费一次就清掉 */
    initialSegment: Int? = null,
    onSegmentConsumed: () -> Unit = {},
    /** 点「登录」→ 上抛给 AppRoot 做 pager 外的全屏覆盖 */
    onOpenLogin: (SharePlatform) -> Unit = {},
    /** 点已登录的网盘 → 上抛给 AppRoot 做 pager 外的全屏覆盖 */
    onOpenBrowse: (SharePlatform) -> Unit = {},
    /** 账号状态版本号：登录 / 退出后变化，用来催账号列表重新读一次 */
    accountRev: Int = 0,
    /** 退出登录成功后回调（让外层 +1 版本号） */
    onAccountChanged: () -> Unit = {},
) {
    var segment by remember { mutableIntStateOf(if (initialUrl.isNullOrBlank()) 0 else 1) }
    // GitHub 仓库页：整页盖在下载段上面（从 Release 里挑文件下）
    var repo by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 下载段发现「未登录」时把平台带过来，切到账号段并高亮那一行
    var highlight by remember { mutableStateOf<SharePlatform?>(null) }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) segment = 1
    }

    // 外部指定落地分段（例如详情页点「用下载器下载」→ 直接落在「下载」段）
    LaunchedEffect(initialSegment) {
        initialSegment?.let {
            segment = it.coerceIn(0, SEGMENTS.lastIndex)
            onSegmentConsumed()
        }
    }

    // ---------------- GitHub 仓库页（纯 Compose，留在本层没问题）----------------

    repo?.let { (owner, name) ->
        FullScreenLayer(onBack = { repo = null }) {
            GitHubRepoScreen(owner = owner, repo = name, bottomInnerPadding = bottomInnerPadding)
        }
        return
    }

    // ---------------- 正常四段内容 ----------------

    Column(
        Modifier
            .fillMaxSize()
            // 状态栏高度：分段控件是裸放在 Column 顶部的（外层没有 Scaffold 托管），
            // 不加这段 padding 就会被状态栏压住。
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // 只有分段控件这条自己收边距；下面各子页的顶栏要铺满整宽
        // （整条 Column 收边距会把子页 TopAppBar 也挤窄，两侧露出底色 → 就是之前那块「白底」）
        SegmentBar(
            index = segment,
            onSelect = { segment = it },
            labels = SEGMENTS,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(Modifier.fillMaxSize()) {
            when (segment) {
                0 -> PanAccountScreen(
                    bottomInnerPadding = bottomInnerPadding,
                    highlight = highlight,
                    onOpenLogin = onOpenLogin,
                    onOpenCloud = onOpenBrowse,
                    refreshKey = accountRev,
                    onLoggedOut = {
                        segment = 0
                        onAccountChanged()
                    },
                )

                1 -> DownloadScreen(
                    bottomInnerPadding = bottomInnerPadding,
                    initialUrl = initialUrl,
                    onConsumed = onConsumed,
                    initialPwd = initialPwd,
                    onNeedLogin = { platform ->
                        highlight = platform
                        segment = 0
                    },
                    // 「下载设置」不再整页覆盖，而是切到「设置」段
                    onOpenSettings = { segment = 3 },
                    onOpenGitHubRepo = { owner, name -> repo = owner to name },
                )

                2 -> BookmarkScreen(
                    store = bookmarks,
                    bottomInnerPadding = bottomInnerPadding,
                )

                else -> DownloadSettingsScreen(bottomInnerPadding = bottomInnerPadding)
            }
        }
    }
}

/**
 * 全屏覆盖层容器：铺满整屏 + **完全不透明**实底 + 状态栏 insets。
 *
 * 为什么必须完全不透明（两个 bug 都出在这）：
 *  ① 下层（分段控件 + 内容页）还在 composition 里活着，半透明会让两层文字穿帮
 *     —— 用户截图里那两块叠在一起的顶栏。
 *  ② 外层 `AppRoot` 挂着一个**一直在动的全局极光**（`BgEffectBackground(dynamicBackground = true)`）。
 *     Miuix 的 `surface` 自带一点透明，极光一晃就透上来 → 整页亮度持续波动 = 「一直闪烁」。
 *     所以这里不能直接用 `surface`，要用**烘焙成不透明的实色**。
 *
 * `windowInsetsPadding(statusBars)` 是给页面自己的标题栏让位 —— 之前漏了这层，
 * 登录页的「返回/粘贴/保存」被状态栏压住点不到。
 *
 * @param onBack 系统返回键的处理。
 *   ⚠️ 这里以前是个 `BackHandler(enabled = true) { }` **空实现**，它抢在所有内层
 *   BackHandler 之前把返回键吃掉了 —— 于是云盘浏览页里那套
 *   「多选→退多选 / 子目录→上一级 / 根目录→退出页面」的三级逻辑**永远收不到返回键**，
 *   用户只能去点左上角的返回箭头。现在改成显式传入，谁盖在最上面谁负责。
 */
@Composable
fun FullScreenLayer(
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // toOpaque()：把可能带 alpha 的主题色烘焙成实色，挡住背后流动的极光
    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface.toOpaque()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            content()
        }
        // 只有调用方明确给了回调才注册；否则让返回键继续往下传给内层页面
        if (onBack != null) {
            androidx.activity.compose.BackHandler { onBack() }
        }
    }
}

/**
 * 平台 → 对应登录页的分派。
 *
 * 公开给 `AppRoot` 用：登录页要挂在 pager 之外的全屏层里渲染。
 */
@Composable
fun PanLoginEntry(
    platform: SharePlatform,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    when (platform) {
        SharePlatform.QUARK -> QuarkLoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.UC -> UCLoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.BAIDU -> BaiduLoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.PAN115 -> Pan115LoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.PAN123 -> Pan123LoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.C139 -> C139LoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.XUNLEI -> XunleiLoginScreen(onBack = onBack, onSaved = onSaved)
        SharePlatform.GITHUB -> Unit
    }
}

// ---------------------------------------------------------------- 分段控件

/**
 * 把可能带 alpha 的主题色烘焙成不透明实色。
 *
 * Miuix 的 `surface` 为了透出极光带了一点透明；覆盖层要挡住背后**一直在动的**极光，
 * 就必须用实色 —— 否则整页亮度会随光效波动（用户说的「一直闪烁」）。
 * 这里直接在原色上重设 alpha = 1，不改色相，浅色/深色主题都不会偏色。
 */
private fun Color.toOpaque(): Color = copy(alpha = 1f)

private val SEGMENTS = listOf("账号", "下载", "收藏", "设置")
private val BAR_HEIGHT = 38.dp
private val PADDING = 3.dp

/**
 * HyperOS 式分段控件：一个浅底胶囊里滑动着主色指示器。
 *
 * 实现要点：指示器宽度用 `fillMaxWidth(1f / 段数)` 算，位移用 graphicsLayer 施加，
 * 避免在 Layout 阶段反复测量 —— 动画才能跑满 60/120 帧。
 */
@Composable
private fun SegmentBar(
    index: Int,
    onSelect: (Int) -> Unit,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    // 记录内层可滑动区域宽度（已经扣掉左右 padding）
    var trackWidth by remember { mutableIntStateOf(1) }
    val offset = remember { Animatable(index.toFloat()) }

    LaunchedEffect(index) { offset.animateTo(index.toFloat(), tween(200)) }

    Box(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHigh)
            .padding(PADDING),
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { trackWidth = it.width.coerceAtLeast(1) }) {
            // 滑动指示器
            Box(
                Modifier
                    .fillMaxWidth(1f / labels.size)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = offset.value * trackWidth / labels.size }
                    .padding(0.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(cs.primary),
            )
            // 文字层
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            fontWeight = if (i == index) FontWeight.Medium else FontWeight.Normal,
                            color = if (i == index) Color.White else cs.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
