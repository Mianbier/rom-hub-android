package org.linbaogu.romhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.linbaogu.romhub.BuildConfig
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.component.FloatingBottomBar
import org.linbaogu.romhub.ui.component.FloatingBottomBarItem
import org.linbaogu.romhub.ui.effect.BgEffectBackground
import org.linbaogu.romhub.ui.nav.Screen
import org.linbaogu.romhub.ui.notify.NotifyPermissionDialog
import org.linbaogu.romhub.ui.notify.canPostNotifications
import org.linbaogu.romhub.ui.notify.rememberNotifyPermission
import org.linbaogu.romhub.ui.screens.AboutScreen
import org.linbaogu.romhub.ui.screens.DeviceDetailScreen
import org.linbaogu.romhub.ui.screens.DevicesScreen
import org.linbaogu.romhub.ui.screens.FeedScreen
import org.linbaogu.romhub.ui.screens.LoginScreen
import org.linbaogu.romhub.ui.screens.PortDetailScreen
import org.linbaogu.romhub.ui.screens.UploadScreen
import org.linbaogu.romhub.ui.screens.VersionListScreen
import org.linbaogu.romhub.ui.update.AppUpdateDialog
import org.linbaogu.romhub.ui.welcome.WelcomeScreen
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** HyperOS 页面转场的手感（HyperCeiler 同款弹簧参数）。 */
private val PageSpring = spring<Float>(stiffness = 380f, dampingRatio = 0.92f)

/** 被覆盖页面的视差位（往左露出 1/4）。 */
private const val PARALLAX = -0.25f

@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current

    var splashDone by remember { mutableStateOf(false) }
    // HyperCeiler 式首次启动：欢迎 → 功能 → 使用条款，同意后不再出现。
    var welcomeDone by remember { mutableStateOf(Prefs.welcomeDone(ctx)) }

    Box(Modifier.fillMaxSize()) {
        if (!welcomeDone) {
            WelcomeScreen(onFinished = {
                Prefs.setWelcomeDone(ctx, true)
                welcomeDone = true
            })
        } else if (vm.session.role == Role.NONE) {
            LoginScreen(vm)
        } else {
            MainShell(vm)
        }

        // 开屏动画：logo 弹性放大后整体淡出（HyperCeiler 式），只出现一次
        if (!splashDone) {
            SplashOverlay(onDone = { splashDone = true })
        }
    }

    // 开局就跟用户讲清楚通知权限是干什么用的，再交给系统弹窗。
    // 只主动弹一次；之后想开可以从「关于 → 订阅更新提醒」里再进。
    val notifyPerm = rememberNotifyPermission()
    var askingNotify by remember {
        mutableStateOf(!Prefs.notifyPromptShown(ctx) && !canPostNotifications(ctx))
    }
    if (askingNotify) {
        NotifyPermissionDialog(
            onAllow = {
                Prefs.setNotifyPromptShown(ctx, true)
                askingNotify = false
                notifyPerm.launch()
            },
            onLater = {
                Prefs.setNotifyPromptShown(ctx, true)
                askingNotify = false
            },
        )
    }
}

// ---------------------------------------------------------------- 开屏动画

/** HyperCeiler 式全屏开屏：surface 底 + 居中 logo 弹性放大，停留后整体淡出。 */
@Composable
private fun SplashOverlay(onDone: () -> Unit) {
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(0.86f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 180f))
        delay(420)
        alpha.animateTo(0f, tween(300))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        AppLogo(
            116.dp,
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
        )
    }
}

// ---------------------------------------------------------------- 主外壳

@Composable
private fun MainShell(vm: AppViewModel) {
    val nav = vm.nav
    val tabs = vm.tabs
    val surface = MiuixTheme.colorScheme.surface

    // 从上次的 tab 起步（nav 状态可以跨进程恢复），避免开屏先闪一下首页
    val pagerState = rememberPagerState(initialPage = nav.currentTab, pageCount = { tabs.size })

    // 底栏采样的背景层：整页内容（含深层页面）都在里面。
    // 光效只有根层这一份（固定不动），底栏胶囊的 lens 折射吃到的就是流动的色彩。
    val backdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }

    // 返回：KSU 的①②③ —— 只在「有更深的页」或「不在第 0 个 tab」时拦截，
    // 其余情况交给系统（等于 KSU 的规则③）
    BackHandler(enabled = nav.stack.size > 1 || nav.currentTab != 0) {
        nav.back()
    }

    // 底栏 → 分页
    LaunchedEffect(nav.currentTab, tabs.size) {
        if (nav.isAtRoot && nav.currentTab < tabs.size && pagerState.currentPage != nav.currentTab) {
            pagerState.animateScrollToPage(nav.currentTab)
        }
    }
    // 分页 → 底栏
    LaunchedEffect(pagerState, tabs.size) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { p ->
                if (nav.isAtRoot && p < tabs.size && nav.currentTab != p) nav.switchTab(p)
            }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBottom = if (bottomInset != 0.dp) 8.dp + bottomInset else 28.dp
    val bottomInnerPadding = 64.dp + barBottom

    Box(Modifier.fillMaxSize()) {
        // ① 全部页面内容 —— 整体被底栏采样
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            // 全局唯一的流动光效背景：固定在这一层，不随 tab / 页面滑动。
            // 底栏切换滑块时光效纹丝不动，只有界面在滑（HyperOS 的分层观感）。
            BgEffectBackground(
                dynamicBackground = true,
                modifier = Modifier.fillMaxSize(),
                isFullSize = true,
            ) {
            }

            // ── 第 0 层：Tab 分页（常驻）。有深层页时整体视差左移 1/4。
            var pagerW by remember { mutableStateOf(1f) }
            val pagerShift = remember { Animatable(0f) }
            LaunchedEffect(nav.stack.size) {
                pagerShift.animateTo(if (nav.stack.size > 1) PARALLAX else 0f, PageSpring)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { pagerW = it.width.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer { translationX = pagerShift.value * pagerW },
                userScrollEnabled = nav.isAtRoot,
                beyondViewportPageCount = 1,
                // HyperOS 底栏切换手感：弹簧回弹吸附，而不是匀速滑到头
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = PageSpring,
                ),
            ) { page ->
                if (page < tabs.size) {
                    TabPage(tabs[page].title, vm, bottomInnerPadding)
                }
            }

            // ── 深层页面：**常驻 composition**（栈里的页面从不销毁）。
            //    返回时数据 / 滚动位置 / 输入内容原样还在 —— 零重新加载。
            nav.stack.drop(1).forEachIndexed { i, screen ->
                val index = i + 1
                key(nav.uidAt(index)) {
                    DeepLayer(isTop = index == nav.stack.lastIndex, isGhost = false) {
                        DeepScreenContent(screen, vm, bottomInnerPadding)
                    }
                }
            }

            // ── 幽灵层：刚 pop 出去的页面，在最上层播放滑出动画，结束后销毁
            nav.ghost?.let { g ->
                key(nav.ghostUid) {
                    DeepLayer(isTop = false, isGhost = true) {
                        DeepScreenContent(g, vm, bottomInnerPadding)
                    }
                }
            }

            LaunchedEffect(nav.ghostUid) {
                if (nav.ghost != null) {
                    delay(520)
                    nav.clearGhost()
                }
            }
        }

        // ② 底栏在最外层 —— 不进采样层，否则会自我反馈
        FloatingBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = barBottom),
            selectedIndex = nav.currentTab,
            onSelected = { nav.switchTab(it) },
            backdrop = backdrop,
            tabsCount = tabs.size,
            isBlurEnabled = true,
        ) { activateTab ->
            tabs.forEachIndexed { index, spec ->
                FloatingBottomBarItem(
                    selected = nav.currentTab == index,
                    onClick = { activateTab(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                ) {
                    val unread = vm.unread
                    val icon: @Composable () -> Unit = {
                        Icon(spec.icon, contentDescription = spec.title)
                    }
                    if (spec.title == "动态" && unread > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = Color(0xFFD8443C),
                                    contentColor = Color.White,
                                ) {
                                    Text(
                                        text = if (unread > 99) "99+" else unread.toString(),
                                        fontSize = 10.sp,
                                    )
                                }
                            },
                        ) { icon() }
                    } else {
                        icon()
                    }
                    Text(
                        text = spec.title,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }

        // ③ 轻量提示条：手动「检查更新」等操作的回执（2.6 秒自动消失）
        val msg = vm.globalMessage
        if (msg != null) {
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2600)
                vm.clearMessage()
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Card(modifier = Modifier.padding(bottom = barBottom + 76.dp)) {
                    Text(
                        msg,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // ④ 发现新版本 → 弹更新提示。挂最外层，和权限弹窗同级，不会被任何页面挡住。
        val up = vm.appUpdate
        if (up != null && !vm.updateDismissed) {
            val actx = LocalContext.current
            AppUpdateDialog(
                info = up,
                currentName = BuildConfig.VERSION_NAME,
                onDownload = { openUrl(actx, it) },
                onLater = { vm.dismissUpdate() },
            )
        }
    }
}

// ---------------------------------------------------------------- 深层页面层

/**
 * 单个深层页面的容器：
 *  - 实底背景（surface 微透 0.97）：转场时新旧页面不再互相透视穿帮，
 *    光效又能隐约透出来；
 *  - progress 驱动位移：0 = 就位 / 1 = 屏幕右侧外 / -0.25 = 视差位；
 *  - 新页从右滑入、被覆盖页滑到视差位、pop 的幽灵层滑出 —— HyperOS 转场。
 */
@Composable
private fun DeepLayer(isTop: Boolean, isGhost: Boolean, content: @Composable () -> Unit) {
    val target = when {
        isGhost -> 1f
        isTop -> 0f
        else -> PARALLAX
    }
    // 初值：新 push 的页（首次组合且是顶层）从屏幕右侧开始；幽灵层从就位开始
    val progress = remember { Animatable(if (isGhost) 0f else if (isTop) 1f else PARALLAX) }
    LaunchedEffect(target) {
        progress.animateTo(target, PageSpring)
    }
    var w by remember { mutableStateOf(1f) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = progress.value * w
                if (isGhost) alpha = 1f - progress.value.coerceIn(0f, 1f) * 0.35f
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.97f))
        )
        content()
    }
}

@Composable
private fun DeepScreenContent(screen: Screen, vm: AppViewModel, bottomInnerPadding: Dp) {
    when (screen) {
        is Screen.Tab -> Box(Modifier.fillMaxSize())

        is Screen.DeviceDetail -> DeviceDetailScreen(
            code = screen.code,
            bottomInnerPadding = bottomInnerPadding,
            onBack = { vm.nav.back() },
            onOpenVersions = { r ->
                vm.nav.push(
                    Screen.VersionList(
                        code = screen.code,
                        region = r.region,
                        branch = r.branch,
                    )
                )
            },
            onOpenPort = { vm.nav.push(Screen.PortDetail(it)) },
        )

        is Screen.VersionList -> VersionListScreen(
            code = screen.code,
            deviceName = deviceNameOf(screen.code),
            region = screen.region,
            branch = screen.branch,
            highlight = screen.highlight,
            bottomInnerPadding = bottomInnerPadding,
            onBack = { vm.nav.back() },
        )

        is Screen.PortDetail -> PortDetailScreen(
            id = screen.id,
            bottomInnerPadding = bottomInnerPadding,
            onBack = { vm.nav.back() },
        )
    }
}

@Composable
private fun TabPage(title: String, vm: AppViewModel, bottomInnerPadding: Dp) {
    val stats = vm.stats
    val subtitle = stats?.let {
        "${it.devices} 款机型 · ${it.versions} 个版本"
    }.orEmpty()

    when (title) {
        "主页" -> DevicesScreen(
            subtitle = subtitle,
            bottomInnerPadding = bottomInnerPadding,
            onOpenDevice = { vm.nav.push(Screen.DeviceDetail(it)) },
        )

        "动态" -> FeedScreen(
            subtitle = subtitle,
            bottomInnerPadding = bottomInnerPadding,
            onSeen = { vm.markFeedSeen() },
            onOpenVersion = { u ->
                // 移植包事件跳移植包详情；官方版本事件才跳版本列表
                if (u.kind == "port") {
                    vm.nav.push(Screen.PortDetail(u.portId))
                } else {
                    vm.nav.push(
                        Screen.VersionList(
                            code = u.codename,
                            region = u.region,
                            branch = u.branch,
                            highlight = u.newVersion,
                        )
                    )
                }
            },
        )

        "包上传" -> UploadScreen(
            token = vm.session.token,
            username = vm.session.username,
            bottomInnerPadding = bottomInnerPadding,
            onUploaded = { },
        )

        "关于" -> AboutScreen(vm = vm, bottomInnerPadding = bottomInnerPadding)

        else -> Box(Modifier.fillMaxSize())
    }
}

private fun deviceNameOf(code: String): String =
    Repo.cachedDevices().firstOrNull { it.code == code }?.displayName ?: code
