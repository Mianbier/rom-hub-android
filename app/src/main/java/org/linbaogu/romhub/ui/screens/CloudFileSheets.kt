package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.linbaogu.romhub.cloud.CloudBrowserViewModel
import org.linbaogu.romhub.pan.model.ShareFile
import org.linbaogu.romhub.ui.common.SnackbarController
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 云盘文件操作的弹窗 / 面板集合。
 *
 * 对齐云析的 `CloudFileSheets.kt`（1238 行，每家一份）+ `SaveToCloudSheet.kt`。
 * 这里把共性抽出来做成 7 家共用的组件。
 *
 * 搬运自云析（CYQawa/YunX，AGPL-3.0）。
 */

/** 通用弹窗外壳：标题 + 内容 + 关闭。 */
@Composable
fun CloudDialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    title,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** 文本输入对话框（新建文件夹 / 重命名共用）。 */
@Composable
fun CloudInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    var text by remember { mutableStateOf(initial) }
    CloudDialogScaffold(title = title, onDismiss = onDismiss) {
        Text(label, fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = cs.onSurfaceVariantSummary)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("取消") { onDismiss() }
            PrimaryButton(confirmText) { if (text.isNotBlank()) onConfirm(text.trim()) }
        }
    }
}

/** 二次确认对话框（删除等危险操作）。 */
@Composable
fun CloudConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    CloudDialogScaffold(title = title, onDismiss = onDismiss) {
        Text(message, fontSize = MiuixTheme.textStyles.body2.fontSize, color = cs.onSurfaceVariantSummary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("取消") { onDismiss() }
            PrimaryButton(confirmText) { onConfirm() }
        }
    }
}

/** 「+」菜单：目前只有新建文件夹（上传文件后续再补）。 */
@Composable
fun CloudAddMenu(
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    /** 上传本地文件到当前目录；为 null 表示这家网盘不支持上传（菜单里不显示该项） */
    onUpload: (() -> Unit)? = null,
) {
    val cs = MiuixTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 8.dp).width(200.dp)) {
                MenuItem("新建文件夹") { onCreateFolder() }
                if (onUpload != null) {
                    MenuItem("上传本地文件") { onUpload() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(text, fontSize = MiuixTheme.textStyles.body1.fontSize, color = cs.onSurface)
    }
}

/** 分享结果弹窗（展示链接 + 提取码，可复制）。 */
@Composable
fun ShareResultDialog(info: org.linbaogu.romhub.pan.model.ShareInfo, onDismiss: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val text = buildString {
        append("链接：${info.shareUrl}")
        if (info.passcode.isNotBlank()) {
            append("\n提取码：${info.passcode}")
        }
        info.warning?.let { append("\n\n提示：$it") }
    }
    CloudDialogScaffold(title = "分享已创建", onDismiss = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cs.surfaceContainerHigh)
                .padding(10.dp),
        ) {
            Text(text, fontSize = MiuixTheme.textStyles.body2.fontSize, color = cs.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("关闭") { onDismiss() }
            PrimaryButton("复制链接") {
                clipboard.setText(AnnotatedString(text))
                SnackbarController.show("已复制分享信息")
            }
        }
    }
}

/**
 * 批量操作面板：分享（可选有效期/提取码）+ 移动到目录。
 * 对齐云析的 `BatchStep` 状态机，这里简化为「先选动作，再补参数」两步。
 */
@Composable
fun CloudBatchActionSheet(
    vm: CloudBrowserViewModel,
    onDismiss: () -> Unit,
    onShareCreated: (org.linbaogu.romhub.pan.model.ShareInfo) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 0 = 选动作，1 = 分享设置，2 = 选移动目录
    var step by remember { mutableStateOf(0) }

    val files = vm.selectedFiles
    var urlType by remember { mutableStateOf(1) }        // 0=私密 1=公开
    var passcode by remember { mutableStateOf("") }
    var expireIdx by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    var dirs by remember { mutableStateOf<List<ShareFile>>(emptyList()) }
    var currentMoveDir by remember { mutableStateOf(vm.let { "" }) }

    // 进入「选移动目录」时列出根目录的子文件夹
    LaunchedEffect(step, currentMoveDir) {
        if (step == 2) {
            dirs = vm.listDirs(currentMoveDir)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                Modifier
                    .padding(18.dp)
                    .heightIn(max = 460.dp),
            ) {
                Text(
                    when (step) {
                        0 -> "批量操作（${files.size} 项）"
                        1 -> "创建分享"
                        else -> "移动到…"
                    },
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                when (step) {
                    0 -> {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            SheetAction("创建分享链接") { step = 1 }
                            SheetAction("移动到其他文件夹") { step = 2 }
                        }
                        Spacer(Modifier.height(10.dp))
                        GhostButton("取消") { onDismiss() }
                    }

                    1 -> {
                        Text("分享方式", fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = cs.onSurfaceVariantSummary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("公开", urlType == 1) { urlType = 1 }
                            ChoiceChip("私密", urlType == 0) { urlType = 0 }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("提取码（留空为不设密码）", fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = cs.onSurfaceVariantSummary)
                        Spacer(Modifier.height(6.dp))
                        InputBox(passcode) { passcode = it }
                        Spacer(Modifier.height(12.dp))
                        Text("有效期", fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = cs.onSurfaceVariantSummary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            vm.expireOptions.forEachIndexed { i, (label, _) ->
                                ChoiceChip(label, expireIdx == i) { expireIdx = i }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("上一步") { step = 0 }
                            PrimaryButton(if (busy) "创建中…" else "创建分享") {
                                if (busy) return@PrimaryButton
                                busy = true
                                scope.launch {
                                    val expired = vm.expireOptions[expireIdx].second
                                    val r = vm.createShare(files, urlType, passcode, expired)
                                    busy = false
                                    if (r != null) onShareCreated(r) else onDismiss()
                                }
                            }
                        }
                    }

                    else -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.surfaceContainerHigh)
                                .clickable { currentMoveDir = "" }
                                .padding(12.dp),
                        ) {
                            Text("回根目录", fontSize = MiuixTheme.textStyles.body2.fontSize, color = cs.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            if (dirs.isEmpty()) {
                                Text(
                                    "没有子文件夹",
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    color = cs.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(8.dp),
                                )
                            } else {
                                dirs.forEach { d ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { currentMoveDir = d.fid }
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            d.fname,
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = cs.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("上一步") { step = 0 }
                            PrimaryButton("移动到此处") {
                                scope.launch {
                                    vm.move(files, currentMoveDir)
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(text, fontSize = MiuixTheme.textStyles.body1.fontSize, color = cs.onSurface)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) cs.primary else cs.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = if (selected) Color.White else cs.onSurface,
        )
    }
}

@Composable
private fun InputBox(value: String, onChange: (String) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
