package org.linbaogu.romhub.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.linbaogu.romhub.data.AppUpdateInfo
import org.linbaogu.romhub.data.UpdateMirror
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 发现新版本的提示弹窗。样式与启动时的通知权限弹窗保持一致（照 KernelSU 的卡片式 Dialog）。
 *
 * ## 下载方式有三条，按「快 → 慢」排
 *
 * 1. **内置下载器直链** —— App 内多线程、可续传，下完能直接装。
 * 2. **网盘镜像** —— 站长传到夸克/蓝奏/123 的分享链接。点一下唤起
 *    App 自带的网盘解析（主 APP 认得这些域名），不用把 APK 传到自己服务器。
 * 3. **浏览器直链** —— 兜底，走系统浏览器。
 *
 * 直链和网盘镜像都没有时，只显示公告（比如「v1.2.0 已下架，请回退」），
 * 这时不显示任何下载按钮。
 *
 * 没走 PackageInstaller 自动安装，是因为那要额外申请
 * `REQUEST_INSTALL_PACKAGES` 权限并处理「未知来源」提示 ——
 * 对一个几天才发一次的小工具不值得，交给系统安装器更省事也更安全。
 */
@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    currentName: String,
    onDownload: (String) -> Unit,
    onLater: () -> Unit,
    /** 用内置多线程下载器下（比浏览器快、能断点续传）；为空则不显示该按钮 */
    onDownloadWithApp: ((String) -> Unit)? = null,
    /** 点网盘镜像：交给 App 内置的网盘解析流程 */
    onOpenMirror: ((UpdateMirror) -> Unit)? = null,
) {
    // 公告级别 critical 时同样不可跳过
    val blocking = info.force || info.noticeBlocking
    val hasDirect = info.url.isNotBlank()
    val hasMirror = info.mirrors.isNotEmpty() && onOpenMirror != null

    Dialog(
        onDismissRequest = { if (!blocking) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !blocking,
            dismissOnClickOutside = !blocking,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Card {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        MiuixIcons.Update,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    if (info.versionName.isNotBlank()) "发现新版本" else "更新公告",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                if (info.versionName.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "当前 v$currentName → 新版本 v${info.versionName}",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // ---- 更新公告（优先显示，因为可能是必须知道的事） ----
                if (info.notice.title.isNotBlank() || info.notice.body.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    NoticeBlock(info)
                }

                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        info.notes,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ---- 下载区 ----
                if (hasDirect || hasMirror) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "点下面的按钮会开始下载，装完后覆盖安装即可，数据不会丢。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    Spacer(Modifier.height(18.dp))
                    if (hasDirect && onDownloadWithApp != null) {
                        PrimaryButton("用下载器下载 v${info.versionName}") {
                            onDownloadWithApp(info.url)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "内置多线程下载器：快、能续传，下完在「网盘下载器 → 下载」里能看到进度。",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        GhostButton("用浏览器下载") { onDownload(info.url) }
                    } else if (hasDirect) {
                        PrimaryButton("下载 v${info.versionName}") { onDownload(info.url) }
                    }

                    if (hasMirror) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "网盘下载",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "从分享链接下载，速度通常比直链更稳。",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        info.mirrors.forEach { m ->
                            MirrorButton(m) { onOpenMirror!!(m) }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                if (!blocking) {
                    Spacer(Modifier.height(6.dp))
                    GhostButton("以后再说") { onLater() }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这条更新必须处理，无法跳过。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = Color(0xFFD93025),
                    )
                }
            }
        }
    }
}

/** 公告块。按级别配色，critical 用红底。 */
@Composable
private fun NoticeBlock(info: AppUpdateInfo) {
    val level = info.notice.level
    val (bg, fg) = when (level) {
        "critical" -> Color(0x1AFF3B30) to Color(0xFFD93025)
        "warn" -> Color(0x1AFF9500) to Color(0xFFC77700)
        else -> Color(0x1A34C759) to Color(0xFF248A3D)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val label = when (level) {
            "critical" -> "重要公告"
            "warn" -> "注意"
            else -> "公告"
        }
        Text(
            label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        )
        if (info.notice.title.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                info.notice.title,
                color = fg,
                fontWeight = FontWeight.Medium,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }
        if (info.notice.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                info.notice.body,
                color = fg,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            )
        }
        if (info.notice.at.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                info.notice.at,
                color = fg.copy(alpha = 0.7f),
                // Miuix 0.9.4 的 TextStyles 没有 caption，这里用已确认存在的
                // footnote2（最小号正文），视觉上等价于「小字时间戳」
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            )
        }
    }
}

/** 一个网盘镜像按钮。提取码非空时一并显示，省得用户去别处找。 */
@Composable
private fun MirrorButton(m: UpdateMirror, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.name.ifBlank { "网盘" },
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (m.code.isNotBlank()) "提取码 ${m.code}" else "点此解析下载",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text("›", color = MiuixTheme.colorScheme.primary)
        }
    }
}
