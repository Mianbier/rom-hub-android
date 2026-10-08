package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.hc.HcHomeTip
import org.linbaogu.romhub.pan.store.BookmarkStore
import org.linbaogu.romhub.ui.AppViewModel
import org.linbaogu.romhub.ui.common.HcDivider
import org.linbaogu.romhub.ui.common.HcGroup
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.nav.Screen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「主页」——仿 HyperCeiler 的首页：**一个大标题 + 一条 tips + 一组功能入口卡片**。
 *
 * ⚠️ 点进去的子页**走正常页面栈**（[Screen.Firmware] / [Screen.Feed] / [Screen.PanHub] /
 * [Screen.Upload]），不再在首页内联换内容。这样：
 *   · 进入时有 HyperOS 弹簧滑入 + 下层页面视差（和 HyperCeiler 的
 *     `provision_slide_in_right` 一个观感）；
 *   · 返回时是滑出动画，返回逻辑统一由 `AppNav.back()` 处理（深层页 pop → tab 根 → 系统）。
 */
@Composable
fun HcHomePage(
    vm: AppViewModel,
    bookmarks: BookmarkStore,
    bottomInnerPadding: Dp,
) {
    val stats = vm.stats
    val subtitle = stats?.let { "${it.devices} 款机型 · ${it.versions} 个版本" }.orEmpty()

    ListScreen(
        title = "主页",
        largeTitle = "HyperRomhub",
        subtitle = subtitle,
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        // 顶部：HyperCeiler 式的随机提示条（五种样式轮换）
        item { HcHomeTip() }

        item { SectionLabel("功能") }
        item {
            HcGroup {
                val entries = buildList {
                    add(HomeEntry.FIRMWARE)
                    add(HomeEntry.FEED)
                    add(HomeEntry.PAN)
                    if (vm.session.role == Role.DEV) add(HomeEntry.UPLOAD)
                }
                entries.forEachIndexed { i, e ->
                    HomeRow(e) { vm.nav.push(e.screen) }
                    if (i != entries.lastIndex) HcDivider()
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** 主页上的功能入口。 */
private enum class HomeEntry(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    FIRMWARE("固件下载", "小米 / OPPO / vivo 等 10 个品牌", MiuixIcons.GridView, Screen.Firmware),
    FEED("动态", "订阅机型的最新更新", MiuixIcons.Update, Screen.Feed),
    PAN("网盘下载器", "解析网盘分享链接并下载", MiuixIcons.Download, Screen.PanHub),
    UPLOAD("包上传", "上传固件包到服务器", MiuixIcons.UploadCloud, Screen.Upload),
}

@Composable
private fun HomeRow(entry: HomeEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(entry.icon, contentDescription = entry.title, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                entry.summary,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
