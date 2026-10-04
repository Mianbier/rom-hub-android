/*
 * 网盘 WebView 登录页（夸克 / UC / 百度 / 115 / 123云盘 / 移动云盘 / 迅雷）
 *
 * 结构搬运自 YunX (云析) - Copyright (C) 2026 CYQawa
 * 原目录：app/src/main/kotlin/com/yunx/app/ui/login/
 * 许可：GNU Affero General Public License v3.0（见项目根 LICENSE）
 *
 * 改动说明：
 *   · 包名 com.yunx.* → org.linbaogu.romhub.*
 *   · 统一的 TopAppBar（Miuix 风格）替代各家重复的 Material3 Scaffold
 *   · 六家 WebView 站点的差异抽成 [WebPanLoginSpec]，由下方各自的入口函数传进来
 *   · 依赖的 ViewModel 全部换成 [PanAccountStore] 直连仓库（本项目不引 lifecycle-viewmodel-ktx）
 */

package org.linbaogu.romhub.ui.login

import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.core.Prefs
import kotlinx.coroutines.withTimeoutOrNull
import org.linbaogu.romhub.pan.BaiduConstants
import org.linbaogu.romhub.pan.C139Constants
import org.linbaogu.romhub.pan.Pan115Constants
import org.linbaogu.romhub.pan.Pan123Constants
import org.linbaogu.romhub.pan.QuarkConstants
import org.linbaogu.romhub.pan.SharePlatform
import org.linbaogu.romhub.pan.UCConstants
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.coroutines.resume

// ---------------------------------------------------------------- 规格描述

/**
 * 一家网盘「网页登录」的全部差异点。
 *
 * 六家的流程骨架完全一样（开 WebView → 用户登录 → 轮询抓凭证 → 校验落库），
 * 只有下面这几个地方不同，所以抽成一个数据类，页面逻辑只写一遍。
 */
internal data class WebPanLoginSpec(
    /** 页面标题，如「夸克网盘登录」 */
    val title: String,
    /** 登录页地址 */
    val loginUrl: String,
    /** 抓 Cookie 的域名（CookieManager.getCookie 的参数） */
    val cookieDomain: String,
    /** WebView 伪装用的 UA（[useSystemUserAgent] = true 时忽略此项） */
    val userAgent: String = "",
    /**
     * 用系统默认 UA，而不是 [userAgent]。
     * 移动云盘（139）的移动版页面靠 WebView 环境检测，写死 UA 反而会触发白屏，只能让它自己报。
     */
    val useSystemUserAgent: Boolean = false,
    /** 关键字段预检：挡掉登录前的初始化中间态 */
    val isPlausible: (String) -> Boolean,
    /**
     * 凭证（Cookie 或 Token）的网络校验 + 落库，成功返回 true。
     *
     * 参数是 Context —— 因为这些 spec 是各家入口函数里构造的，那里拿不到登录页内部的
     * LocalContext，而落库要 context。
     */
    val validateAndSave: suspend (Context, String) -> Boolean,
    /** 从 CookieManager 抓凭证；默认抓 [cookieDomain] 的 Cookie。123 云盘走 localStorage 单独覆盖 */
    val sampleCredential: (suspend (WebView) -> String)? = null,
    /** 手动粘贴框的提示文案 */
    val manualHint: String,
    /**
     * 保存失败时**（已确认凭证格式像登录态、但服务端/本地校验没过）**的补充提示。
     *
     * 默认文案是通用的「请重新登录」，对 139 这种「登录态分两种形态、缺一半也能看起来像登录了」
     * 的平台不够用，所以留个口子让各家说清楚到底缺什么。
     */
    val failHint: String? = null,

    /**
     * 失败提示的**兜底来源**（取第一个非空值）：比 [failHint] 优先级低，但可以是动态算出来的。
     *
     * 139 要它：[harvest139Credential] 抓取时会把「网页里到底有哪些存储键」记在
     * [C139Constants.lastProbe]，失败时把这条线索显示出来，比一句干巴巴的「请重新登录」有用。
     */
    val failureInfo: (() -> String?)? = null,
    /**
     * 「粘贴」框内容的规范化钩子：把用户粘进来的各种形态（完整 Cookie / 裸值 / JSON / cURL）
     * 统一整理成各家 `validateAndSave` 认的格式。默认原样透传。
     */
    val normalizeManualInput: ((String) -> String)? = null,
    /** 手动粘贴框标题（各家叫法不同：Cookie / Token / 凭证） */
    val manualTitle: String = "手动输入凭证",
    /** 登录教程的步骤说明 */
    val tutorial: List<String>,
    /** 进入页面时是否先弹一个风险提示（百度风控严重，单独提示） */
    val riskNotice: String? = null,
    /** 网页加载完成后是否强制覆盖 viewport（桌面版页面在手机上缩放别扭时用） */
    val forceViewport: Boolean = false,
    /**
     * 私有 scheme 深链兜底：网页里点了 `weixin://` / `alipays://` / `mcloud://` 这类
     * **只有原生 App 才认识**的链接时，WebView 默认会渲染一整页
     * `net::ERR_UNKNOWN_URL_SCHEME`，页面就废了。
     *
     * 这里把该 URL 交给本函数：
     *  - 返回**非空字符串** → 当作网页地址加载（等于把深链翻译成网页版）；
     *  - 返回 null → 不处理，让 WebView 走默认（会显示错误页）。
     *
     * 移动云盘（139）必须用：手机版首页的「进入中国移动云盘」按钮跳的就是
     * `mcloud://main/tab?...&pullapp=...`（拉起 App 的深链），不翻译就卡死。
     */
    val mapDeepLink: ((url: String, currentUrl: String) -> String?)? = null,
)

// ---------------------------------------------------------------- 页面主体

/**
 * 通用网盘 WebView 登录页。
 *
 * 页面结构：
 *   · 标题栏：返回 + 粘贴（手动输凭证）+ 保存（自动检测没触发时的兜底）
 *   · 主体：WebView 打开官网，用户手动登录
 *   · 自动登录检测：网页里登录完成后自动抓凭证、校验、落库并关闭本页
 */
@Composable
internal fun WebPanLoginScreen(
    spec: WebPanLoginSpec,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showRisk by remember { mutableStateOf(spec.riskNotice != null) }
    // 上一次完成加载的 URL：用来区分「真导航」（该显示进度条）
    // 和 SPA 内部的重复 onPageStarted/onPageFinished（不该闪进度条）
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    // 页面底色（烘焙成不透明）：给 WebView 首帧垫底，避免加载过程中透出背后的流动极光而「闪」
    val backgroundColor = MiuixTheme.colorScheme.surface.copy(alpha = 1f)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            if (spec.forceViewport) {
                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                setInitialScale(0)
            }
            if (!spec.useSystemUserAgent) {
                settings.userAgentString = spec.userAgent
            }
            // ── 关键：WebView 在外层 HorizontalPager 里，双击/双指缩放会被 pager
            //    当成横向拖动抢走（表现为缩放时底栏跟着切页）。
            //    这里主动让父容器别拦截触摸事件，手势全部交给网页自己处理。
            isNestedScrollingEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    // 只在「整页导航」时显示加载条。SPA 的 hash 变化 / 同页 XHR 不该触发，
                    // 否则进度条会反复闪 —— 见 onPageFinished 里对同 URL 的判断。
                    if (url != null && url != lastLoadedUrl) {
                        isLoading = true
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    lastLoadedUrl = url
                    if (spec.forceViewport) {
                        // 桌面版页面没有 viewport 或锁了缩放，这里强制覆盖一次让它适配屏幕
                        view?.evaluateJavascript(
                            "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
                                "var c='width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes';" +
                                "if(m){m.setAttribute('content',c);}else{var n=document.createElement('meta');" +
                                "n.name='viewport';n.content=c;document.head.appendChild(n);}" +
                                "window.dispatchEvent(new Event('resize'));})()",
                            null,
                        )
                    }
                }
            }
            webChromeClient = WebChromeClient()
            // 网页首帧渲染前 WebView 是透明的，背后的流动极光会透上来 → 看起来在闪。
            // 给它一个不透明的主题底色，首屏和页面底色一致，视觉上就没有过渡闪动了。
            setBackgroundColor(backgroundColor.toArgb())
            // 手指一落到网页上就声明「这段手势归我」：父级 HorizontalPager 不能再截走，
            // 双指缩放 / 双击放大才能正常工作（否则会变成横向切 tab）。
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false // 不消费事件，继续交给 WebView 自己处理
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    else -> false
                }
            }
            loadUrl(spec.loginUrl)
        }
    }

    /** 私有 scheme 兜底：在 initial client 外面再包一层 [PrivateSchemeGuard] */
    LaunchedEffect(Unit) {
        val current = webView.webViewClient
        if (current !is PrivateSchemeGuard) {
            webView.webViewClient = PrivateSchemeGuard(current, spec)
        }
    }

    // 自动登录检测：网页内登录完成即自动保存；右上角「保存」保留作手动兜底
    //
    // 注意：默认**关掉**自动保存 —— 139（移动云盘）等平台在登录中间态就会写入
    // authorization，自动检测会误判成已登录直接关页，用户还没点保存页面就没了。
    // 想省一步可以在「网盘下载器 → 设置」里打开。
    val autoSave = remember { Prefs.autoSaveLogin(context) }
    if (autoSave) {
        rememberWebLoginAutoDetect(
            sampleCredential = {
                spec.sampleCredential?.invoke(webView)
                    ?: CookieManager.getInstance().getCookie(spec.cookieDomain).orEmpty()
            },
            isPlausible = spec.isPlausible,
            validateAndSave = { credential -> spec.validateAndSave(context, credential) },
            isPaused = { isSaving || isSavingManual || showCookieDialog || showRisk || showTutorial },
            onInFlightChange = { isSaving = it },
            onAutoSaved = onSaved,
        )
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    LaunchedEffect(Unit) {
        // 没风控提示的，进来直接展示教程
        if (spec.riskNotice == null) showTutorial = true
    }

    Column(
        Modifier
            .fillMaxSize()
            // 不透明底：挡住下层（分段控件 / 全局流动极光），杜绝叠加与亮度波动
            .background(backgroundColor),
    ) {
        // ------------------------------------------------ 标题栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("返回") { if (!isSaving && !isSavingManual) onBack() }
            Spacer(Modifier.width(10.dp))
            Text(
                spec.title,
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GhostButton("粘贴") { if (!isSaving && !isSavingManual) showCookieDialog = true }
            Spacer(Modifier.width(6.dp))
            GhostButton(if (isSaving) "校验中" else "保存") {
                if (isSaving || isSavingManual) return@GhostButton
                scope.launch {
                    isSaving = true
                    val cred = try {
                        spec.sampleCredential?.invoke(webView)
                            ?: CookieManager.getInstance().getCookie(spec.cookieDomain).orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                    val saved = spec.validateAndSave(context, cred)
                    isSaving = false
                    if (saved) {
                        SnackbarController.show("登录成功")
                        onSaved()
                    } else {
                        // 凭证过不了预检 → 提示「还没登录/登录态不完整」；
                        // 凭证看着像登录了但服务端不认 → validateAndSave 内部已经打过具体原因。
                        SnackbarController.show(
                            if (!spec.isPlausible(cred)) {
                                "未检测到登录态，请先在网页里完成登录"
                            } else {
                                spec.failHint
                                    ?: spec.failureInfo?.invoke()
                                    ?: "登录态校验未通过，请重新登录后再保存"
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

        // 自动保存关着的时候，登录完必须手动点右上角「保存」——
        // 不提示的话用户会以为页面卡住了（用移动云盘就踩过这个坑）。
        if (!autoSave) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "登录完成后，请点右上角「保存」把登录态存下来",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        // ------------------------------------------------ 网页主体
        // 进度条用固定高度的容器托住（显隐不改变布局），否则每次显隐都会把下面的
        // WebView 顶一下再弹回去 —— 网页看起来就在抖。宽度用动画过渡，避免硬切。
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isLoading,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(120)
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(220)
                ),
            ) {
                WebLoadingBar()
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }
    }

    // ------------------------------------------------ 风控提示
    spec.riskNotice?.let { notice ->
        if (showRisk) {
            MiuixDialog(
                title = "温馨提示",
                message = notice,
                confirmText = "我知道了",
                onConfirm = {
                    showRisk = false
                    showTutorial = true
                },
                onDismiss = { showRisk = false },
            )
        }
    }

    // ------------------------------------------------ 登录教程
    if (showTutorial) {
        MiuixDialog(
            title = "登录教程",
            message = spec.tutorial.joinToString("\n"),
            confirmText = "知道了",
            onConfirm = { showTutorial = false },
            onDismiss = { showTutorial = false },
        )
    }

    // ------------------------------------------------ 手动粘贴凭证
    if (showCookieDialog) {
        CookiePasteDialog(
            title = spec.manualTitle,
            hint = "从网页登录态复制完整的凭证。${spec.manualHint}",
            value = cookieInput,
            busy = isSavingManual,
            onValueChange = { cookieInput = it },
            onDismiss = { if (!isSavingManual) showCookieDialog = false },
            onConfirm = {
                scope.launch {
                    isSavingManual = true
                    // 粘贴进来的可能是完整 Cookie、裸 authorization 值、JSON 或 cURL，
                    // 交给各家的 normalize 统一整理（默认原样透传）。
                    val cleaned = spec.normalizeManualInput?.invoke(cookieInput) ?: cookieInput.trim()
                    val saved = spec.validateAndSave(context, cleaned)
                    isSavingManual = false
                    if (saved) {
                        SnackbarController.show("登录成功")
                        showCookieDialog = false
                        onSaved()
                    } else {
                        SnackbarController.show("凭证无效，请检查后重试")
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- 私有 scheme 兜底

/**
 * 私有 scheme 兜底代理。
 *
 * WebView 遇到自己不认识的 scheme（`mcloud://`、`weixin://`、`alipays://` …）时，
 * 默认行为是**把整页替换成 `net::ERR_UNKNOWN_URL_SCHEME` 错误页** ——
 * 对登录页来说是致命的：用户点一下按钮，页面就废了，什么也做不了。
 *
 * 这一层做两件事：
 *  1. 拦下**所有非 http(s)** 的跳转，交给 [WebPanLoginSpec.mapDeepLink] 翻译；
 *     翻译出网页地址就加载它，翻译不出就静默吞掉（绝不渲染错误页）。
 *  2. 其余回调**原样转发**给被包裹的 client（`onPageFinished` 里的进度条、
 *     viewport 修正都不会丢）。
 *
 * 做成"可重包"的代理而不是一次性 override：这样无论外面怎么再包一层 client，
 * 兜底逻辑都不会被顶掉。
 */
private class PrivateSchemeGuard(
    private val delegate: WebViewClient?,
    private val spec: WebPanLoginSpec,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: android.webkit.WebResourceRequest?,
    ): Boolean {
        val target = request?.url?.toString() ?: return false
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return delegate?.shouldOverrideUrlLoading(view, request) ?: false
        }
        // 非 http(s)：内部导航也拦（私有 scheme 不可能是应用内页面）
        val mapped = spec.mapDeepLink?.invoke(target, view?.url.orEmpty())
        return if (!mapped.isNullOrBlank()) {
            view?.loadUrl(mapped)
            true
        } else {
            // 认不出来就吞掉，绝不让 WebView 换成一页错误
            true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val mapped = spec.mapDeepLink?.invoke(url, view?.url.orEmpty())
            if (!mapped.isNullOrBlank()) {
                view?.loadUrl(mapped)
                return true
            }
            return true
        }
        @Suppress("DEPRECATION")
        return delegate?.shouldOverrideUrlLoading(view, url) ?: false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        delegate?.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        delegate?.onPageFinished(view, url)
    }
}

// ---------------------------------------------------------------- 通用弹窗

/**
 * Miuix 风格的简单确认弹窗。
 *
 * 说明：Miuix 0.9.4 那个 `SuperDialog` API 变更频繁，为了不让登录流程卡在主题适配细节上，
 * 这里自己画一个 —— 就是个圆角卡片 + 标题 + 正文 + 按钮，够用且不会因为库升级挂掉。
 */
@Composable
internal fun MiuixDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null,
    onDismissButton: (() -> Unit)? = null,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    title,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dismissText != null) {
                        GhostButton(dismissText) { onDismissButton?.invoke() ?: onDismiss() }
                    }
                    Spacer(Modifier.weight(1f))
                    GhostButton(confirmText) { onConfirm() }
                }
            }
        }
    }
}

/** 手动粘贴登录凭证的弹窗（Cookie / Token 共用）。 */
@Composable
private fun CookiePasteDialog(
    title: String,
    hint: String,
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    title,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                ) {
                    if (value.isBlank()) {
                        Text(
                            "粘贴到这里…",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = MiuixTheme.textStyles.footnote1.copy(
                            color = MiuixTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GhostButton("取消") { if (!busy) onDismiss() }
                    Spacer(Modifier.weight(1f))
                    GhostButton(if (busy) "校验中" else "保存") {
                        if (!busy && value.isNotBlank()) onConfirm()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 工具

/**
 * 读取 WebView 当前页面 localStorage 里 [key] 的值（123 云盘把登录态存在 authorToken）。
 *
 * ⚠️ 依赖站点私有实现（键名 authorToken、值是裸 JWT），官网改版可能失效 —— 用户还能走「粘贴 Token」兜底。
 * JS 侧用 encodeURIComponent 包一层，避免 JWT 里的特殊字符把 evaluateJavascript 的 JSON 返回值搞乱。
 */
private suspend fun WebView.readLocalStorageValue(key: String): String =
    withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { cont ->
            try {
                evaluateJavascript(
                    "(function(){try{var v=localStorage.getItem('" + key + "');" +
                        "return v===null?'':encodeURIComponent(v)}catch(e){return ''}})()",
                ) { result ->
                    val raw = result?.trim().orEmpty()
                    val value = when {
                        raw.isEmpty() || raw == "\"\"" || raw == "null" -> ""
                        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                            raw.substring(1, raw.length - 1)
                        else -> raw
                    }
                    if (cont.isActive) {
                        cont.resume(
                            runCatching {
                                java.net.URLDecoder.decode(value, "UTF-8")
                            }.getOrDefault(value)
                        )
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    } ?: ""

/**
 * 139 网盘登录态**深度抓取**：CookieManager + 网页内部（document.cookie / localStorage / sessionStorage）。
 *
 * ⚠️ 为什么非得多这一步：139 手机版是 Vue SPA，登录态不一定落在 Set-Cookie 里 ——
 *    domain / path 对不上、或者只写进了 storage 的情况都有，
 *    只做 `CookieManager.getCookie()`（上游 YunX 就只做这一步）会抓不到 `authorization`，
 *    表现为「网页里明明登录了，App 就是提示没登录」。
 *    这里把网页里能读到的都兜一遍，交给 [C139Constants.buildFromSources] 挑有用的。
 *
 * JS 侧用 encodeURIComponent 包一层：storage 里可能有换行 / 引号 / 中文，
 * 直接 evaluateJavascript 返回会被 JSON 转义搞乱。
 */
private suspend fun harvest139Credential(webView: WebView): String {
    val fromCookieManager = runCatching {
        C139Constants.extractCookies { d -> CookieManager.getInstance().getCookie(d) }
    }.getOrDefault("")
    val dump = withTimeoutOrNull(3_000) {
        suspendCancellableCoroutine { cont ->
            try {
                webView.evaluateJavascript(
                    "(function(){" +
                        "try{" +
                        "var o={};" +
                        "o.__dc=document.cookie||'';" +
                        "var g=function(st,p){try{" +
                        "for(var i=0;i<st.length;i++){" +
                        "var k=st.key(i);if(!k)continue;" +
                        "var v=st.getItem(k);if(v==null)continue;" +
                        "o[p+':'+k]=String(v).slice(0,8192);" +
                        "}}catch(e){}};" +
                        "g(localStorage,'ls');g(sessionStorage,'ss');" +
                        "return encodeURIComponent(JSON.stringify(o));" +
                        "}catch(e){return ''}}" +
                        ")()",
                ) { result ->
                    val raw = result?.trim().orEmpty()
                    val value = when {
                        raw.isEmpty() || raw == "\"\"" || raw == "null" -> ""
                        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                            raw.substring(1, raw.length - 1)
                        else -> raw
                    }
                    if (cont.isActive) {
                        cont.resume(
                            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }
                                .getOrDefault(value)
                        )
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    } ?: ""
    return C139Constants.buildFromSources(dump, fromCookieManager)
}

// ---------------------------------------------------------------- 各家入口

/** 夸克网盘登录 */
@Composable
fun QuarkLoginScreen(onBack: () -> Unit, onSaved: () -> Unit) = WebPanLoginScreen(
    spec = WebPanLoginSpec(
        title = "夸克网盘登录",
        loginUrl = QuarkConstants.LOGIN_URL,
        cookieDomain = QuarkConstants.COOKIE_DOMAIN,
        userAgent = QuarkConstants.USER_AGENT,
        isPlausible = { QuarkConstants.isValidCookie(it) },
        validateAndSave = { c, v -> PanAccounts.save(c, SharePlatform.QUARK, v) },
        manualHint = "需包含 __pus= 与 __puus=",
        tutorial = listOf(
            "1. 在下方网页中登录夸克账号",
            "2. 登录完成后会自动检测并保存；没有自动登录就点右上角「保存」",
            "3. 也可以点「粘贴」，手动填入 Cookie（需含 __pus= 与 __puus=）",
            "4. Cookie 约 30 天有效，失效后重新登录即可",
        ),
    ),
    onBack = onBack,
    onSaved = onSaved,
)

/** UC 网盘登录 */
@Composable
fun UCLoginScreen(onBack: () -> Unit, onSaved: () -> Unit) = WebPanLoginScreen(
    spec = WebPanLoginSpec(
        title = "UC 网盘登录",
        loginUrl = UCConstants.LOGIN_URL,
        cookieDomain = UCConstants.COOKIE_DOMAIN,
        userAgent = UCConstants.USER_AGENT,
        isPlausible = { UCConstants.isValidCookie(it) },
        validateAndSave = { c, v -> PanAccounts.save(c, SharePlatform.UC, v) },
        manualHint = "需包含 __pus= 与 __puus=",
        tutorial = listOf(
            "1. 在下方网页中登录 UC 账号",
            "2. 登录完成后会自动检测并保存；没有自动登录就点右上角「保存」",
            "3. 也可以点「粘贴」，手动填入 Cookie",
        ),
        forceViewport = true,
    ),
    onBack = onBack,
    onSaved = onSaved,
)

/** 百度网盘登录 */
@Composable
fun BaiduLoginScreen(onBack: () -> Unit, onSaved: () -> Unit) = WebPanLoginScreen(
    spec = WebPanLoginSpec(
        title = "百度网盘登录",
        loginUrl = BaiduConstants.LOGIN_URL,
        cookieDomain = BaiduConstants.COOKIE_DOMAIN,
        userAgent = BaiduConstants.UA_WEB,
        isPlausible = { BaiduConstants.isValidCookie(it) },
        validateAndSave = { c, v -> PanAccounts.save(c, SharePlatform.BAIDU, v) },
        manualHint = "需包含 BDUSS=",
        tutorial = listOf(
            "1. 在下方网页中登录百度账号",
            "2. 登录完成后会自动检测并保存；没有自动登录就点右上角「保存」",
            "3. 也可以点「粘贴」，手动填入 Cookie（需含 BDUSS=）",
            "4. Cookie 长期有效，失效后重新登录即可",
        ),
        riskNotice = "百度网盘风控较严，频繁解析/转存/下载可能导致账号被风控。\n" +
            "建议降低操作频率，遇到异常提示时稍后再试。",
        forceViewport = true,
    ),
    onBack = onBack,
    onSaved = onSaved,
)

/** 115 网盘登录 */
@Composable
fun Pan115LoginScreen(onBack: () -> Unit, onSaved: () -> Unit) = WebPanLoginScreen(
    spec = WebPanLoginSpec(
        title = "115 网盘登录",
        loginUrl = Pan115Constants.WEB_LOGIN_URL,
        cookieDomain = Pan115Constants.COOKIE_DOMAIN,
        // 115 网页版是桌面站点，接口请求头也用同一 UA，避免风控
        userAgent = Pan115Constants.WEB_UA,
        isPlausible = { Pan115Constants.isValidCookie(it) },
        validateAndSave = { c, v -> PanAccounts.save(c, SharePlatform.PAN115, v) },
        sampleCredential = { webView ->
            withContext(Dispatchers.Main) {
                Pan115Constants.extractCookies { CookieManager.getInstance().getCookie(it) }
            }
        },
        manualHint = "需包含 UID、CID、SEID、KID 四项",
        tutorial = listOf(
            "1. 在下方网页中登录 115 账号（扫码 / 短信 / 密码都由官网处理）",
            "2. 登录完成后会自动检测并保存；没有自动登录就点右上角「保存」",
            "3. 也可以点「粘贴」，手动填入 Cookie",
        ),
        forceViewport = true,
    ),
    onBack = onBack,
    onSaved = onSaved,
)

/** 123 云盘登录（登录态在 localStorage 里，不是 Cookie） */
@Composable
fun Pan123LoginScreen(onBack: () -> Unit, onSaved: () -> Unit) = WebPanLoginScreen(
    spec = WebPanLoginSpec(
        title = "123 云盘登录",
        loginUrl = Pan123Constants.WEB_LOGIN_URL,
        cookieDomain = Pan123Constants.WEB_LOGIN_URL,
        // yun.123pan.cn 个人盘是桌面 SPA；移动 UA 会跳到不完整的移动版页面
        userAgent = Pan123Constants.WEB_UA,
        isPlausible = { it.isNotBlank() },
        validateAndSave = { c, v -> PanAccounts.save(c, SharePlatform.PAN123, v) },
        sampleCredential = { webView ->
            withContext(Dispatchers.Main) {
                webView.readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY)
            }
        },
        manualHint = "即 authorToken（一串 JWT）",
        tutorial = listOf(
            "1. 在下方网页中登录 123 云盘（验证码 / 扫码由官网处理）",
            "2. 登录完成后会自动检测并保存；没有自动登录就点右上角「保存」",
            "3. 也可以点「粘贴」，手动填入 Token",
            "4. 登录态存在浏览器 localStorage 的 authorToken 里，官网改版可能失效",
        ),
        forceViewport = true,
    ),
    onBack = onBack,
    onSaved = onSaved,
)

/** 移动云盘（139）登录 */
@Composable
fun C139LoginScreen(onBack: () -> Unit, onSaved: () -> Unit) = WebPanLoginScreen(
    spec = WebPanLoginSpec(
        title = "移动云盘登录",
        // 手机版登录页（对齐上游 YunX）：电脑版 SPA 在手机 WebView 里必白屏，不能用。
        // 手机版登录接口返回后同样会写 authorization Cookie，凭证不缺。
        loginUrl = C139Constants.MOBILE_LOGIN_URL,
        cookieDomain = C139Constants.COOKIE_DOMAIN,
        // 手机版页面靠 WebView 环境检测，写死桌面 UA 会白屏 → 让系统自己报
        useSystemUserAgent = true,
        // 预检与落库门槛保持一致：光有 Os_SSo_Sid + RMKEY（路径 A）不够，
        // 个人网盘接口要的是 authorization —— 这里一起卡掉，免得「看着登录了却用不了」。
        isPlausible = { C139Constants.isValidCookie(it) && C139Constants.hasAuthorization(it) },
        validateAndSave = { c, v -> PanAccounts.save(c, SharePlatform.C139, v) },
        failHint = "登录态里没有 authorization：请确认已在网页里登录成功（不是停在首页），" +
            "稍等 1~2 秒再点「保存」；也可以用「粘贴」手动填入完整 Cookie",
        // 抓取过程中挖到的线索（网页里有哪些存储键），失败时显示
        failureInfo = { C139Constants.lastProbe },
        // 手机版首页的「进入中国移动云盘」按钮跳的是 mcloud://（拉起 App 的私有 scheme），
        // WebView 收到会渲染 ERR_UNKNOWN_URL_SCHEME 整页错误 —— 翻译成网页版盘列表。
        mapDeepLink = { url, current ->
            C139Constants.rewriteDeepLink(url, current.ifBlank { C139Constants.MOBILE_LOGIN_URL })
        },
        // 不能只看 CookieManager：139 手机版的 authorization 也可能只落在 storage / 别的域，
        // 所以连网页内部（document.cookie + localStorage + sessionStorage）一起兜一遍。
        sampleCredential = { webView ->
            withContext(Dispatchers.Main) { harvest139Credential(webView) }
        },
        // 上游同款：强制覆盖 viewport（139 手机版页面 viewport 会限制缩放）
        forceViewport = true,
        manualTitle = "手动输入 authorization",
        manualHint = "可直接粘贴 authorization 的值（Basic 开头），或完整 Cookie。",
        // 用户从电脑浏览器 / 抓包工具复制过来的东西形态各异（裸值 / 完整 Cookie / JSON / cURL），
        // 统一整理成 authorization=xxx 再走校验。
        normalizeManualInput = { C139Constants.normalizeCookieInput(it) },
        tutorial = listOf(
            "1. 在下方网页中登录中国移动账号（已直达手机版登录页）",
            "2. 登录成功后，回到本页点右上角「保存」",
            "3. 若提示「缺少 authorization」：说明网页没把登录态写进 Cookie，走第 4 步",
            "4. 用电脑浏览器打开 yun.139.com 登录 → F12 → Application → Cookie → " +
                "复制 authorization 的值 → 回到本页点「粘贴」填入（直接粘值即可）",
        ),
    ),
    onBack = onBack,
    onSaved = onSaved,
)
