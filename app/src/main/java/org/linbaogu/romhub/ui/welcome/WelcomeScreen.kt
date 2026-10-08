package org.linbaogu.romhub.ui.welcome

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动引导（HyperCeiler 式）：欢迎 → 功能一览 → 使用条款。
 *
 * 与 HyperCeiler 的 WelComeActivity 同构：
 *  - 全屏流动光效背景
 *  - HorizontalPager 翻页（弹簧手感），底部胶囊指示器（当前页拉长）
 *  - 最后一页「同意并继续」后写 Prefs，永不再弹
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val pages = 3
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages })
    val scope = rememberCoroutineScope()

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        isFullSize = true,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = true,
                beyondViewportPageCount = 0,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = spring(stiffness = 380f, dampingRatio = 0.9f),
                ),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeaturePage()
                    else -> ClausePage()
                }
            }

            // 底部：胶囊指示器 + 主按钮（HyperOS 风格：点状页标，当前页拉长）
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages) { i ->
                        val active = pagerState.currentPage == i
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .height(6.dp)
                                .width(if (active) 22.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (active) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                val isLast = pagerState.currentPage == pages - 1
                Button(
                    onClick = {
                        if (isLast) onFinished()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 14.dp,
                    ),
                ) {
                    Text(
                        if (isLast) "同意并继续" else "下一步",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!isLast) {
                    TextButton(
                        text = "跳过",
                        onClick = onFinished,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 第 1 页：欢迎

@Composable
private fun WelcomePage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(112.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            "ROM Hub",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "小米 ROM 索引 · 官方包与社区移植包",
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(
                "汇聚小米 / 红米 / POCO 全机型固件信息，" +
                        "多镜像高速下载，社区移植包一站式获取。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 第 2 页：功能

private data class Feature(val title: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun FeaturePage() {
    val cs = MiuixTheme.colorScheme
    val features = listOf(
        Feature("全机型索引", "小米 / 红米 / POCO 全机型固件信息，按系列浏览", MiuixIcons.GridView),
        Feature("版本动态", "官方版本与移植包更新实时推送", MiuixIcons.Update),
        Feature("多镜像下载", "阿里云 OSS / cdnorg / bn 等 5 个镜像免签名直链", MiuixIcons.Link),
        Feature("移植包广场", "社区开发者的第三方 ROM 移植包", MiuixIcons.Refresh),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "为你准备好的",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))
        features.forEach { f ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(f.icon, null, Modifier.size(22.dp), tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(f.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            f.desc,
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------- 第 3 页：条款

@Composable
private fun ClausePage() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "使用条款",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                ClauseItem("资源性质", "本应用仅索引小米官方公开发布的固件与社区开发者的第三方移植包，不存储、不修改任何 ROM 文件，下载均来自官方或镜像站点。")
                Spacer(Modifier.height(12.dp))
                ClauseItem("刷机风险", "刷写第三方 ROM 有变砖、丢失数据、失去保修等风险。请在操作前自行备份，并确认自己了解每一步的含义。")
                Spacer(Modifier.height(12.dp))
                ClauseItem("账号说明", "游客身份即可浏览全部内容；上传移植包需要开发者账号，提交的包由站长审核后展示。")
                Spacer(Modifier.height(12.dp))
                ClauseItem("免责声明", "本应用按「现状」提供，作者不对因使用本应用或刷机造成的任何损失承担责任。继续使用即表示你已阅读并同意以上条款。")
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ClauseItem(title: String, body: String) {
    val cs = MiuixTheme.colorScheme
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.primary)
    Spacer(Modifier.height(4.dp))
    Text(
        body,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        color = cs.onSurfaceVariantSummary,
    )
}
