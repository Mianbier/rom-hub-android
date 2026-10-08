package org.linbaogu.romhub.ui.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.linbaogu.romhub.ui.component.AppLogo
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 运行时权限 + 渠道开关，两个都满足才认为「能发通知」。 */
fun canPostNotifications(ctx: Context): Boolean {
    val runtime = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    return runtime && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
}

/** 打开系统里的「本应用通知设置」，用于用户之前拒绝过、系统不再弹窗的情况。 */
fun openNotificationSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }.onFailure {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

class NotifyPermissionHandle(
    val granted: Boolean,
    val launch: () -> Unit,
)

@Composable
fun rememberNotifyPermission(): NotifyPermissionHandle {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(canPostNotifications(ctx)) }
    var tick by remember { mutableIntStateOf(0) }

    // 用户可能是去系统设置里手动开的，回到前台要重新查一次
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tick) { granted = canPostNotifications(ctx) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 系统回调只说明「运行时权限」给没给，渠道开关要再查一次
        granted = canPostNotifications(ctx)
    }
    return remember(granted, launcher) {
        NotifyPermissionHandle(granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * 启动时的权限说明弹窗。
 *
 * 先用大白话告诉用户「这个权限是干什么用的」，再交给系统弹窗 ——
 * 直接甩系统弹窗用户往往随手拒绝，之后就再也收不到订阅提醒了。
 */
@Composable
fun NotifyPermissionDialog(
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
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "开启更新提醒",
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "ROM Hub 需要通知权限，用来在**你订阅的机型**有新包时提醒你。",
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
                    Bullet("官方包更新（稳定版 / 开发版 / 内测 / Beta）")
                    Bullet("社区移植包更新")
                    Bullet("点通知直接跳到那个版本")
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "不订阅任何机型就不会收到推送，也可以随时在「关于」页关掉。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Spacer(Modifier.height(18.dp))
                PrimaryButton("允许通知") { onAllow() }
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
