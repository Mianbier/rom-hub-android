package org.linbaogu.romhub.ui.pan

import androidx.compose.foundation.layout.Arrangement
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
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import org.linbaogu.romhub.ui.component.AppLogo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 存储权限说明弹窗（首次启动时弹一次）。
 *
 * 为什么需要它：Android 11+ 要把文件写进公共的 `Download/rom-hub/`，
 * 必须拿「所有文件访问」（MANAGE_EXTERNAL_STORAGE），而这个权限**不能用运行时弹窗申请**，
 * 只能跳去系统设置页手动开。直接跳设置用户会懵，所以先在这儿用大白话讲清楚。
 *
 * 不开也不影响用：下载会自动退回应用私有目录（卸载即清），只是文件管理器里找不到。
 */
@Composable
fun StoragePermissionDialog(
    onAllow: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
                        MiuixIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "允许写入下载目录",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "下载的 ROM 包需要存到手机里。给这个权限后，文件会直接放进 " +
                        "Download/rom-hub/，你在任意文件管理器里都能找到。",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Bullet("文件存到 Download/rom-hub/，卸载 App 也还在")
                    Bullet("支持多线程分片下载、断点续传")
                    Bullet("只用来管理自己下载的包，不读其他文件")
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "点「去开启」会跳到系统设置，找到本 App 打开「允许管理所有文件」即可。\n" +
                        "不开也行 —— 文件会存到 App 私有目录，只是重装后会丢。",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(18.dp))
                PrimaryButton("去开启") { onAllow() }
                Spacer(Modifier.height(6.dp))
                GhostButton("暂不开启") { onLater() }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "·",
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}
