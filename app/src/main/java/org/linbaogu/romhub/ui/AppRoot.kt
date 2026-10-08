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
import org.linbaogu.romhub.hc.HcBottomBar
import org.linbaogu.romhub.hc.HcNavState
import org.linbaogu.romhub.hc.widget.NavigationStyle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.core.StoragePermission
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.ui.common.GlobalSnackbarHost
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.component.FloatingBottomBar
import org.linbaogu.romhub.ui.component.FloatingBottomBarItem
import org.linbaogu.romhub.ui.effect.BgEffectBackground
import org.linbaogu.romhub.ui.nav.Screen
import org.linbaogu.romhub.ui.notify.NotifyPermissionDialog
import org.linbaogu.romhub.ui.pan.StoragePermissionDialog
import org.linbaogu.romhub.ui.notify.canPostNotifications
import org.linbaogu.romhub.ui.notify.rememberNotifyPermission
import org.linbaogu.romhub.ui.screens.AboutScreen
import org.linbaogu.romhub.ui.screens.HcHomePage
import org.linbaogu.romhub.ui.screens.HcSettingsPage
import org.linbaogu.romhub.ui.screens.BrandDevicesScreen
import org.linbaogu.romhub.ui.screens.BrandPickerScreen
import org.linbaogu.romhub.ui.screens.BrandVersionsScreen
import org.linbaogu.romhub.ui.screens.CloudBrowserScreen
import org.linbaogu.romhub.ui.screens.DeviceDetailScreen
import org.linbaogu.romhub.ui.screens.DownloadScreen
import org.linbaogu.romhub.ui.screens.DevicesScreen
import org.linbaogu.romhub.ui.screens.FeedScreen
import org.linbaogu.romhub.ui.screens.FullScreenLayer
import org.linbaogu.romhub.ui.screens.LoginScreen
import org.linbaogu.romhub.pan.store.BookmarkStore
import org.linbaogu.romhub.ui.screens.PanHubScreen
import org.linbaogu.romhub.ui.screens.PanLoginEntry
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.linbaogu.romhub.ui.common.HyperLoadingPage
import androidx.compose.foundation.layout.Row
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton

/** HyperOS 页面转场的手感（HyperCeiler 同款弹簧参数）。 */
private val PageSpring = spring<Float>(stiffness = 380f, dampingRatio = 0.92f)

/** 被覆盖页面的视差位（往左露出 1/4）。 */
private const val PARALLAX = -0.25f

@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current

    var splashDone by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // 旧的 3 页「欢迎 / 功能 / 使用条款」已经**删掉**了：
        // 它和 HyperCeiler 的引导流程（欢迎→权限→协议→基础设置→登录→开始使用）完全重复，
        // 而且长相就是老版本的界面 —— 用户点完「开始使用」又被它拦一道，观感很割裂。
        // 现在引导统一走 HyperCeiler 那套（登录也已经在里面，排在「开始使用」之前）；
        // 这里只留一个兜底：万一没登录（比如主动登出），仍然弹登录页。
        if (vm.session.role == Role.NONE) {
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

    // 存储权限：下载要落到公共 Download/rom-hub/，Android 11+ 只能去系统设置手动开。
    // 同样是「先讲清楚再跳转」，且只主动弹一次；不开就退回 App 私有目录，功能不中断。
    var askingStorage by remember {
        mutableStateOf(!Prefs.storagePromptShown(ctx) && !StoragePermission.granted(ctx))
    }
    if (askingStorage) {
        StoragePermissionDialog(
            onAllow = {
                Prefs.setStoragePromptShown(ctx, true)
                askingStorage = false
                StoragePermission.openAllFilesSettings(ctx)
            },
            onLater = {
                Prefs.setStoragePromptShown(ctx, true)
                askingStorage = false
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

    // 收藏仓库：整页共用一个实例（内部有 StateFlow，切 tab 不会丢状态）
    val appCtx = LocalContext.current.applicationContext
    val bookmarks = remember(appCtx) { BookmarkStore(appCtx) }

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
    // 注意：全屏覆盖页（登录/云盘浏览）自己带 BackHandler，覆盖页打开时这里必须让路。
    BackHandler(enabled = vm.panOverlay == null && (nav.stack.size > 1 || nav.currentTab != 0)) {
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
        // ⓪ 冷启动加载页：第一批数据还没到之前显示 HyperCeiler 那个加载图标。
        //    以前这段时间是**纯空屏**，看起来像卡死了。
        //    两个约束：等数据最多 3 秒（离线也不能一直转），且至少显示 0.7 秒
        //    （否则缓存命中太快，图标一闪而过反而像闪屏）。
        var bootReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val t0 = System.currentTimeMillis()
            while (vm.stats == null && System.currentTimeMillis() - t0 < 3000) {
                kotlinx.coroutines.delay(60)
            }
            val used = System.currentTimeMillis() - t0
            if (used < 700) kotlinx.coroutines.delay(700 - used)
            bootReady = true
        }
        if (!bootReady) {
            Box(Modifier.fillMaxSize().background(surface)) {
                HyperLoadingPage()
            }
        }

        // ① 全部页面内容 —— 整体被底栏采样
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            // 纯 HyperOS 底色（surface）。原来的流动极光光效已按要求去掉：
            // 整个 App 现在是一块干净的浅色底，和 HyperCeiler 一致。
            Box(Modifier.fillMaxSize().background(surface))

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
                // 只保留当前页 + 直接相邻的（这里 1 已经够用：跳转后 pager 会立刻
                // 把目标页设为 currentPage，不再需要跨多页预渲染）。
                beyondViewportPageCount = 1,
                // HyperOS 底栏切换手感：弹簧回弹吸附，而不是匀速滑到头
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = PageSpring,
                ),
            ) { page ->
                if (page < tabs.size) {
                    TabPage(tabs[page].title, vm, bookmarks, bottomInnerPadding)
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

        // ② 底栏在最外层 —— 不进采样层，否则会自我反馈。
        //
        // 开关逻辑：
        //   · 悬浮底栏 开 → **液体玻璃悬浮胶囊**（本 App 原来那套：折射底下内容 +
        //     内阴影 + 滑动指示器）。「胶囊玻璃」开关控制要不要折射，关掉就是纯色胶囊。
        //   · 悬浮底栏 关 → HyperCeiler 的**贴地标签栏**（不悬浮、贴住屏幕底部，
        //     并留出系统手势条高度，不会像以前那样出现一条白条遮挡内容）。
        LaunchedEffect(Unit) { HcNavState.load(appCtx) }
        if (HcNavState.enabled) {
            FloatingBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 28.dp, end = 28.dp, bottom = barBottom),
                selectedIndex = nav.currentTab,
                onSelected = { nav.switchTab(it) },
                backdrop = backdrop,
                tabsCount = tabs.size,
                isBlurEnabled = HcNavState.glass,
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
        } else {
            HcBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // ⚠ 贴地标签样式必须由**我们这边**让开系统手势条的高度。
                    //   SwitchView 里虽然写了 `mTabContainer.setPadding(0,0,0, mSystemBottomInset)`，
                    //   但那是它从 `ViewCompat.setOnApplyWindowInsetsListener` 拿到的值 ——
                    //   而 Compose 的 AndroidView **不会把 window insets 下发**给宿主 View，
                    //   所以那个值一直是 0，标签就会贴到最底部被小白条压住。
                    //   这里在 Compose 侧把整条底栏抬高 bottomInset，问题就没了。
                    .padding(bottom = bottomInset)
                    .height(56.dp),
                selectedIndex = nav.currentTab,
                style = NavigationStyle.BOTTOM_LABEL,
                onSelected = { nav.switchTab(it) },
            )
        }

        // ③ 全局提示条宿主。
        //    必须挂在最外层：SnackbarController.show() 是全局广播，
        //    之前只有 CloudBrowserScreen 自己挂了宿主，其它页面发出去的提示没人渲染，
        //    表现为「点了按钮没反应」。
        //    放在底栏之后 → 提示条盖在底栏之上；不参与点击（Box 不消费事件）。
        GlobalSnackbarHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = barBottom + 56.dp),
        )

        // ④ 全屏覆盖页（登录 / 云盘浏览）—— 必须挂在 **pager 之外**、最外层。
        //    这两个页面含 WebView：留在 pager 里会被预组合反复重绘 → 「一直闪烁」。
        //    返回键交给页面自己的 BackHandler（云盘浏览页有三级逻辑），这里只兜底关覆盖层。
        vm.panOverlay?.let { ov ->
            FullScreenLayer(onBack = { vm.closePanOverlay() }) {
                when (ov) {
                    is AppViewModel.PanOverlay.Login -> PanLoginEntry(
                        platform = ov.platform,
                        onBack = { vm.closePanOverlay() },
                        onSaved = {
                            // 登录成功：关掉覆盖页 + 催账号列表重读（否则还显示「未登录」）
                            vm.closePanOverlay()
                            vm.bumpAccountRev()
                        },
                    )

                    is AppViewModel.PanOverlay.Browse -> CloudBrowserScreen(
                        platform = ov.platform,
                        bottomInnerPadding = bottomInnerPadding,
                        onExit = { vm.closePanOverlay() },
                        onDownloadStarted = {
                            vm.closePanOverlay()
                            vm.openDownloader(1)
                        },
                    )
                }
            }
        }

        // ④ 轻量提示条：手动「检查更新」等操作的回执（2.6 秒自动消失）
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

        // ④.5 用户协议 / 隐私政策有更新 → 弹一次
        //
        // 站长在管理端改完协议，服务端会发布到 KV；客户端这里比对版本号，
        // 比「用户已同意过的版本」大才弹 —— 所以站长改十次也只打扰一次。
        var legalVer by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            val info = runCatching { org.linbaogu.romhub.core.LegalRepo.fetch(appCtx) }.getOrNull()
            org.linbaogu.romhub.core.LegalRepo.pendingVersion(appCtx, info)?.let { legalVer = it }
        }
        if (legalVer > 0) {
            LegalUpdateDialog(
                version = legalVer,
                updatedAt = org.linbaogu.romhub.core.LegalRepo.updatedAt(appCtx),
                onAgree = {
                    org.linbaogu.romhub.core.LegalRepo.ack(appCtx, legalVer)
                    legalVer = 0
                },
            )
        }

        // ⑤ 发现新版本 → 弹更新提示。挂最外层，和权限弹窗同级，不会被任何页面挡住。
        val up = vm.appUpdate
        if (up != null && !vm.updateDismissed) {
            val actx = LocalContext.current
            AppUpdateDialog(
                info = up,
                currentName = BuildConfig.VERSION_NAME,
                onDownload = { openUrl(actx, it) },
                onDownloadWithApp = {
                    org.linbaogu.romhub.download.DownloadManager.add(
                        url = it,
                        fileName = "ROMHub-v${up.versionName}.apk",
                        subDir = "update",
                    )
                    vm.globalMessage = "已加入下载队列，去「网盘下载器 → 下载」看进度"
                },
                onOpenMirror = { m ->
                    // 网盘镜像：切到网盘下载器的下载段，链接和提取码一起带过去。
                    // 提取码由 DownloadScreen 预填进输入框，用户看得见也能改。
                    vm.dismissUpdate()
                    vm.openDownloaderWithUrl(m.url, m.code)
                },
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
                // 必须完全不透明：这里原来是 surface.copy(alpha = 0.97f)，
                // 留了 3% 透明，新页滑入时会透出下面旧页的字（用户报「微微显示上一页的字」）。
                // 页面本来就不该有透视效果，压成实色即可。
                .background(MiuixTheme.colorScheme.surface)
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
            onGoDownloader = { vm.openDownloader(1) },
        )

        is Screen.PortDetail -> PortDetailScreen(
            id = screen.id,
            bottomInnerPadding = bottomInnerPadding,
            onBack = { vm.nav.back() },
            // 带链接跳过去：用户落地就在「下载」段看到链接已填好并自动解析
            onGoDownloader = { url ->
                if (url.isBlank()) vm.openDownloader(1) else vm.openDownloaderWithUrl(url)
            },
        )

        is Screen.BrandDevices -> BrandDevicesScreen(
            brandKey = screen.brandKey,
            bottomInnerPadding = bottomInnerPadding,
            onBack = { vm.nav.back() },
            onOpenDevice = { d ->
                vm.nav.push(
                    Screen.BrandVersions(
                        brandKey = screen.brandKey,
                        deviceName = d.name,
                        series = d.series,
                    )
                )
            },
        )

        is Screen.BrandVersions -> BrandVersionsScreen(
            brandKey = screen.brandKey,
            deviceName = screen.deviceName,
            series = screen.series,
            bottomInnerPadding = bottomInnerPadding,
            onBack = { vm.nav.back() },
            onGoDownloader = { vm.openDownloader(1) },
        )

        // ── 首页「功能」的四个子页 ──
        // 走的是同一套深层页容器，所以进出场动画 / 返回逻辑和其它页完全一致。
        // 固件下载 → 先到**品牌页**（小米专区 + 各品牌）
        Screen.Firmware -> BrandPickerScreen(
            subtitle = "",
            bottomInnerPadding = bottomInnerPadding,
            onPick = { brand -> vm.nav.push(Screen.BrandDevices(brand.key)) },
            onNotReady = { name -> SnackbarController.show("$name 的数据源还在接入中") },
            onOpenXiaomi = { vm.nav.push(Screen.XiaomiZone) },
        )

        // 小米专区 → 原来那套机型库（服务端数据源）
        Screen.XiaomiZone -> DevicesScreen(
            subtitle = "",
            bottomInnerPadding = bottomInnerPadding,
            onOpenDevice = { vm.nav.push(Screen.DeviceDetail(it)) },
        )

        Screen.Feed -> FeedScreen(
            subtitle = "",
            bottomInnerPadding = bottomInnerPadding,
            onSeen = { vm.markFeedSeen() },
            onOpenVersion = { u ->
                if (u.kind == "port") vm.nav.push(Screen.PortDetail(u.portId))
                else vm.nav.push(
                    Screen.VersionList(
                        code = u.codename,
                        region = u.region,
                        branch = u.branch,
                        highlight = u.newVersion,
                    )
                )
            },
        )

        Screen.PanHub -> {
            val appCtx = LocalContext.current.applicationContext
            val bookmarks = remember(appCtx) { BookmarkStore(appCtx) }
            PanHubScreen(
                bookmarks = bookmarks,
                bottomInnerPadding = bottomInnerPadding,
                onOpenLogin = { vm.openPanLogin(it) },
                onOpenBrowse = { vm.openPanBrowse(it) },
                accountRev = vm.accountRev,
                onAccountChanged = { vm.bumpAccountRev() },
            )
        }

        Screen.Upload -> UploadScreen(
            token = vm.session.token,
            username = vm.session.username,
            bottomInnerPadding = bottomInnerPadding,
            onUploaded = { },
        )
    }
}

@Composable
private fun TabPage(title: String, vm: AppViewModel, bookmarks: BookmarkStore, bottomInnerPadding: Dp) {
    val stats = vm.stats
    val subtitle = stats?.let {
        val base = "${it.devices} 款机型 · ${it.versions} 个版本"
        // 数据来自快照/缓存/内置种子时标出来。省略的后果：用户看到「354 款机型」
        // 会当成实时的，点进去发现没有新机型，就当成数据错了。
        val at = it.lastSyncAt.takeIf { t -> t.isNotBlank() }?.let { t -> " · 更新于 $t" }.orEmpty()
        if (vm.statsOffline) "$base · 离线数据$at" else base + at
    }.orEmpty()

    when (title) {
        // 主页 = 仿 HyperCeiler 的功能列表（含 tips），原来的机型库已并进「固件下载」
        "主页" -> HcHomePage(
            vm = vm,
            bookmarks = bookmarks,
            bottomInnerPadding = bottomInnerPadding,
        )

        "设置" -> HcSettingsPage(
            vm = vm,
            bottomInnerPadding = bottomInnerPadding,
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

        "固件下载" -> BrandPickerScreen(
            subtitle = subtitle,
            bottomInnerPadding = bottomInnerPadding,
            onPick = { brand -> vm.nav.push(Screen.BrandDevices(brand.key)) },
            onNotReady = { name -> SnackbarController.show("$name 的数据源还在接入中") },
            onOpenXiaomi = { vm.nav.push(Screen.XiaomiZone) },
        )

        "网盘下载器" -> PanHubScreen(
            bookmarks = bookmarks,
            bottomInnerPadding = bottomInnerPadding,
            initialUrl = vm.pendingDownloadUrl,
            onConsumed = { vm.consumePendingDownload() },
            initialPwd = vm.pendingDownloadPwd,
            initialSegment = vm.pendingPanSegment,
            onSegmentConsumed = { vm.consumePendingPanSegment() },
            onOpenLogin = { vm.openPanLogin(it) },
            onOpenBrowse = { vm.openPanBrowse(it) },
            accountRev = vm.accountRev,
            onAccountChanged = { vm.bumpAccountRev() },
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


/**
 * 「用户协议 / 隐私政策已更新」弹窗。
 *
 * 内容来自云端（站长在管理端改一次，所有客户端都能拿到）。这里**直接内嵌滚动正文**，
 * 而不是跳去某个 Activity —— 少一层依赖，弹窗自己就能把事说清楚。
 */
@Composable
private fun LegalUpdateDialog(
    version: Int,
    updatedAt: String,
    onAgree: () -> Unit,
) {
    val ctx = LocalContext.current
    var showText by remember { mutableStateOf(false) }
    val terms = remember(showText) {
        if (showText) org.linbaogu.romhub.core.LegalRepo
            .text(ctx, org.linbaogu.romhub.core.LegalRepo.Doc.TERMS) else ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(horizontal = 28.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "用户协议与隐私政策已更新",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("版本 v$version")
                        if (updatedAt.isNotBlank()) append(" · 更新于 $updatedAt")
                        append("\n继续使用即表示你已阅读并同意最新条款。")
                    },
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                if (showText && terms.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                terms,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(if (showText) "收起" else "查看全文") { showText = !showText }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("我知道了") { onAgree() }
                }
            }
        }
    }
}
