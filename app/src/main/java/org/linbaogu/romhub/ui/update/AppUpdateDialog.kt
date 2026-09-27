package org.linbaogu.romhub.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.linbaogu.romhub.data.AppUpdateInfo
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
 * 下载走浏览器：点「下载更新」 → 打开 [AppUpdateInfo.url]（GitHub Releases 的固定地址）
 * → 浏览器下载到「下载」目录 → 用户点一下安装。
 * 没走 DownloadManager + PackageInstaller，是因为那样要额外申请
 * `REQUEST_INSTALL_PACKAGES` 权限，并且要自己处理「未知来源」提示 ——
 * 对一个几天才发一次的小工具不值得，而且用户要求的是「提供下载链接」。
 */
@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    currentName: String,
    onDownload: (String) -> Unit,
    onLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!info.force) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !info.force,
            dismissOnClickOutside = !info.force,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Card {
            Column(
                Modifier
                    .fillMaxWidth()
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
                    "发现新版本",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前 v$currentName → 新版本 v${info.versionName}",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )

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

                Spacer(Modifier.height(10.dp))
                Text(
                    "点下面的按钮会用浏览器打开下载地址，装完后覆盖安装即可，数据不会丢。",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(18.dp))
                PrimaryButton("下载 v${info.versionName}") { onDownload(info.url) }
                if (!info.force) {
                    Spacer(Modifier.height(6.dp))
                    GhostButton("以后再说") { onLater() }
                }
            }
        }
    }
}
