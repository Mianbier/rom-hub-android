package org.linbaogu.romhub.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 所有列表页的统一外壳 —— 结构与 KernelSU 的 `HomeMiuix.kt` / `AboutMiuix.kt` 一致。
 *
 * 三层结构（顺序不能反）：
 * ① `Scaffold(containerColor = Transparent)` —— 默认的 containerColor 是不透明 surface，
 *    会把底下的极光整块盖住（这正是「只有个别页面看得到光效」的原因）。
 * ② `Modifier.layerBackdrop(barBackdrop)` 的 Box 里放 **极光 + 列表** —— 这是顶栏要采样的层。
 * ③ 顶栏在 `topBar` 槽里（采样层**外面**），全透明 + 25px 背部模糊。
 *    放进采样层会让顶栏采到自己，越糊越实。
 *
 * 极光**由每个页面自己画**（而不是 App 外壳统一画）：这样它才落在顶栏的采样层里，
 * 顶栏的玻璃才能透出流动的色彩；同时又被 App 外壳的采样层包含，底栏胶囊的折射也能吃到。
 */
@Composable
fun ListScreen(
    title: String,
    subtitle: String = "",
    largeTitle: String = title,
    onBack: (() -> Unit)? = null,
    topBarColor: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    bottomInnerPadding: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberBarBackdrop()

    Scaffold(
        // ① 透明底：让极光透上来
        containerColor = Color.Transparent,
        topBar = {
            // ③ 顶栏：全透明 + 背部模糊（采样层在外面，不会自反馈）
            BlurredBar(barBackdrop) {
                TopAppBar(
                    title = title,
                    largeTitle = largeTitle,
                    subtitle = subtitle,
                    color = topBarColor ?: Color.Transparent,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(MiuixIcons.Back, contentDescription = "返回")
                            }
                        }
                    },
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        },
        popupHost = { },
        // 这里只让出左右（刘海/挖孔），上下必须丢掉：
        //   · 顶部由 Miuix 的 TopAppBar 自己吃 statusBars
        //   · 底部由 AppRoot 的底栏统一处理
        // 实测：加了 Bottom 会和底栏的 navigationBarsPadding 叠成双份，
        // 列表底部凭空多出一截空白。所以这里维持 .only(Horizontal)。
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        // ② 采样层：列表内容（光效已提升到 AppRoot 全局层，页面保持透明，
        //    顶栏玻璃这里采样到的是列表滚动内容）
        Box(modifier = if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier) {
            LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(contentPadding),
                    contentPadding = innerPadding,
                    overscrollEffect = null,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content()
                    item {
                        Box(Modifier.padding(top = bottomInnerPadding + 12.dp))
                    }
                }
        }
    }
}
