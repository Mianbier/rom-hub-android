package org.linbaogu.romhub.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.DeviceItem
import org.linbaogu.romhub.data.PortUploadReq
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.ui.common.Chip
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.component.GhostButton
import org.linbaogu.romhub.ui.component.PrimaryButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val KINDS = listOf("移植", "官改", "类原生编译")

@Composable
fun UploadScreen(
    token: String,
    username: String,
    bottomInnerPadding: Dp,
    onUploaded: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf(Repo.cachedDevices()) }
    var deviceQuery by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<DeviceItem?>(null) }

    var author by remember { mutableStateOf(username) }
    var source by remember { mutableStateOf("酷安") }
    var title by remember { mutableStateOf("") }
    var portType by remember { mutableStateOf("") }
    var portKind by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var shareUrl by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    var publishedAt by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }
    var uploadingImage by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var isErr by remember { mutableStateOf(false) }

    // 公告配图：从相册选一张 -> 传到服务端 -> 把返回的图片地址插进公告文本。
    // 服务端返回的是公网固定域名地址，所以以后换网络、换局域网 IP 都不影响显示。
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingImage = true
            msg = ""
            runCatching {
                val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("读不到这张图片")
                val ext = mime.substringAfter('/', "jpg").substringBefore(';')
                val name = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() && it.contains('.') }
                    ?: "notice_${System.currentTimeMillis()}.$ext"
                Api.uploadImage(ctx, bytes, name, mime, token)
            }.onSuccess { url ->
                uploadingImage = false
                isErr = false
                notice = (notice.trimEnd() + if (notice.isBlank()) "" else "\n") + url
                msg = "图片已上传，地址已插入公告"
            }.onFailure {
                uploadingImage = false
                isErr = true
                msg = it.message ?: "图片上传失败"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (devices.isEmpty()) {
            runCatching { Repo.devices(ctx) }.onSuccess { devices = it }
        }
    }

    val matches = remember(devices, deviceQuery) {
        val q = deviceQuery.trim().lowercase()
        if (q.isBlank()) emptyList()
        else devices.filter {
            it.displayName.lowercase().contains(q) || it.code.lowercase().contains(q)
        }.take(8)
    }

    ListScreen(
        title = "包上传",
        largeTitle = "上传移植包",
        subtitle = "以 ${username.ifBlank { "开发者" }} 身份提交",
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "机型",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (picked == null) {
                        TextField(
                            value = deviceQuery,
                            onValueChange = { deviceQuery = it },
                            label = "搜索机型名或代号",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Chip(
                                text = "${picked!!.displayName}（${picked!!.code}）",
                                selected = true,
                                onClick = { },
                            )
                            Spacer(Modifier.width(8.dp))
                            GhostButton("重选") {
                                picked = null
                                deviceQuery = ""
                            }
                        }
                    }
                    if (picked == null && matches.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        matches.forEach { d ->
                            Card(
                                onClick = {
                                    picked = d
                                    deviceQuery = ""
                                },
                                showIndication = true,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        d.displayName,
                                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        d.code,
                                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "包信息",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "标题（如 官改OS4BY_Tian.zip）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = author,
                        onValueChange = { author = it },
                        label = "作者",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = source,
                        onValueChange = { source = it },
                        label = "来源（酷安 / 论坛 / 自建）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = portType,
                        onValueChange = { portType = it },
                        label = "适配类型（如 colorOS16 / EvolutionX）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = version,
                        onValueChange = { version = it },
                        label = "版本号（可留空）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "包类型",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KINDS.forEach { k ->
                            Chip(text = k, selected = k == portKind, onClick = { portKind = k })
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "分享链接",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持 123 云盘 / 移动云盘 / 百度网盘。填好后可以点「解析」自动带出包名、大小和时间。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = shareUrl,
                        onValueChange = { shareUrl = it },
                        label = "https://…",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(if (parsing) "解析中…" else "解析链接") {
                            if (parsing || shareUrl.isBlank()) return@GhostButton
                            parsing = true
                            msg = ""
                            scope.launch {
                                runCatching { Api.parsePortLink(ctx, shareUrl.trim(), token) }
                                    .onSuccess { r ->
                                        parsing = false
                                        if (r.ok && r.files.isNotEmpty()) {
                                            val f = r.files.first()
                                            fileName = f.name
                                            fileSize = f.sizeHuman
                                            publishedAt = f.createdAt
                                            if (version.isBlank()) version = f.version
                                            isErr = false
                                            msg = "已解析：${r.platformZh} · ${f.name}"
                                        } else {
                                            isErr = true
                                            msg = r.error.ifBlank { "解析失败" }
                                        }
                                    }
                                    .onFailure {
                                        parsing = false
                                        isErr = true
                                        msg = it.message ?: "解析失败"
                                    }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = "文件名",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = fileSize,
                        onValueChange = { fileSize = it },
                        label = "文件大小（如 7.9 GB）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = publishedAt,
                        onValueChange = { publishedAt = it },
                        label = "发布日期（如 2026-09-19）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "作者公告",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "写清刷入方式、注意事项、已知问题。这段会显示在详情页。\n" +
                            "可以直接粘贴 https 链接；也可以点下面按钮上传图片，图片地址会自动插进来。",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = notice,
                        onValueChange = { notice = it },
                        label = "公告内容",
                        singleLine = false,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = if (uploadingImage) "正在上传图片…" else "插入图片（从相册选一张）",
                        enabled = !uploadingImage,
                    ) { pickImage.launch("image/*") }
                }
            }
        }

        if (msg.isNotBlank()) {
            item {
                Text(
                    text = msg,
                    color = if (isErr) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }

        item { SectionLabel("提交") }

        item {
            PrimaryButton(
                text = if (busy) "提交中…" else "提交移植包",
                enabled = !busy,
                onClick = {
                    val d = picked
                    isErr = false
                    msg = when {
                        d == null -> "请先选择机型"
                        portKind.isBlank() -> "请选择包类型"
                        shareUrl.isBlank() -> "请填写分享链接"
                        else -> ""
                    }
                    if (msg.isNotBlank()) {
                        isErr = true
                        return@PrimaryButton
                    }
                    busy = true
                    scope.launch {
                        runCatching {
                            Api.uploadPort(
                                ctx,
                                PortUploadReq(
                                    codename = d!!.code,
                                    deviceName = d.displayName,
                                    author = author.trim(),
                                    source = source.trim(),
                                    title = title.trim(),
                                    portType = portType.trim(),
                                    portKind = portKind,
                                    version = version.trim(),
                                    shareUrl = shareUrl.trim(),
                                    fileName = fileName.trim(),
                                    fileSize = fileSize.trim(),
                                    publishedAt = publishedAt.trim(),
                                    notice = notice.trim(),
                                ),
                                token,
                            )
                        }.onSuccess { r ->
                            busy = false
                            if (r.ok) {
                                isErr = false
                                msg = if (r.duplicated) "这个链接之前已经提交过了（已更新）" else "提交成功，已上线"
                                // 清掉表单，方便接着传下一个
                                title = ""; shareUrl = ""; fileName = ""; fileSize = ""
                                publishedAt = ""; notice = ""; version = ""; portType = ""
                                onUploaded(r.id)
                            } else {
                                isErr = true
                                msg = r.error.ifBlank { "提交失败" }
                            }
                        }.onFailure {
                            busy = false
                            isErr = true
                            msg = it.message ?: "提交失败"
                        }
                    }
                },
            )
        }

        item {
            Text(
                "提交后立刻生效；如果填错了，请联系站长下架后重新提交（开发者不能自行删除）。",
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
