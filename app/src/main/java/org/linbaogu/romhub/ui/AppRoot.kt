package org.linbaogu.romhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.linbaogu.romhub.BuildConfig
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.ui.common.openUrl
import org.linbaogu.romhub.ui.notify.NotifyPermissionDialog
import org.linbaogu.romhub.ui.notify.canPostNotifications
import org.linbaogu.romhub.ui.notify.rememberNotifyPermission
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.ui.component.FloatingBottomBar
import org.linbaogu.romhub.ui.component.FloatingBottomBarItem
import org.linbaogu.romhub.ui.nav.Screen
import org.linbaogu.romhub.ui.screens.AboutScreen
import org.linbaogu.romhub.ui.screens.DeviceDetailScreen
import org.linbaogu.romhub.ui.screens.DevicesScreen
import org.linbaogu.romhub.ui.screens.FeedScreen
import org.linbaogu.romhub.ui.screens.LoginScreen
import org.linbaogu.romhub.ui.screens.PortDetailScreen
import org.linbaogu.romhub.ui.screens.UploadScreen
import org.linbaogu.romhub.ui.screens.VersionListScreen
import org.linbaogu.romhub.ui.update.AppUpdateDialog
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current

    // 开局就跟用户讲清楚通知权限是干什么用的，再交给系统弹窗。
    // 放在最外层是为了「已经登录过、下次直接进主页」的用户也能看到。
    // 只主动弹一次；之后想开可以从「关于 → 订阅更新提醒」里再进。
    val notifyPerm = rememberNotifyPermission()
    var askingNotify by remember {
        mutableStateOf(!Prefs.notifyPromptShown(ctx) && !canPostNotifications(ctx))
    }

    if (vm.session.role == Role.NONE) {
        LoginScreen(vm)
    } else {
        MainShell(vm)
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

@Composable
private fun MainShell(vm: AppViewModel) {
    val nav = vm.nav
    val tabs = vm.tabs
    val surface = MiuixTheme.colorScheme.surface

    // 从上次的 tab 起步（nav 状态可以跨进程恢复），避免开屏先闪一下首页
    val pagerState = rememberPagerState(initialPage = nav.currentTab, pageCount = { tabs.size })

    // 底栏采样的背景层：整页内容（含深层页面）都在里面。
    // 极光由各页面自己画（见 ListScreen），所以这层里天然含极光 ——
    // 底栏胶囊的 lens 折射吃到的就是流动的色彩。
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
        // ① 全部页面内容（每个页面自带极光）—— 整体被底栏采样
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = nav.isAtRoot,
                beyondViewportPageCount = 1,
            ) { page ->
                if (page < tabs.size) {
                    TabPage(tabs[page].title, vm, bottomInnerPadding)
                }
            }

            // 深层页面：滑入覆盖（在 layerBackdrop 内部，底栏才能正确采样）
            AnimatedContent(
                targetState = nav.stack.size,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 8 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(150)))
                    }
                },
                label = "deep",
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                when (val s = nav.current) {
                    is Screen.Tab -> Box(Modifier.fillMaxSize())

                    is Screen.DeviceDetail -> DeviceDetailScreen(
                        code = s.code,
                        bottomInnerPadding = bottomInnerPadding,
                        onBack = { nav.back() },
                        onOpenVersions = { r ->
                            nav.push(
                                Screen.VersionList(
                                    code = s.code,
                                    region = r.region,
                                    branch = r.branch,
                                )
                            )
                        },
                        onOpenPort = { nav.push(Screen.PortDetail(it)) },
                    )

                    is Screen.VersionList -> VersionListScreen(
                        code = s.code,
                        deviceName = deviceNameOf(s.code),
                        region = s.region,
                        branch = s.branch,
                        highlight = s.highlight,
                        bottomInnerPadding = bottomInnerPadding,
                        onBack = { nav.back() },
                    )

                    is Screen.PortDetail -> PortDetailScreen(
                        id = s.id,
                        bottomInnerPadding = bottomInnerPadding,
                        onBack = { nav.back() },
                    )
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
