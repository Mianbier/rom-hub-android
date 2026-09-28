package org.linbaogu.romhub.ui.common

import org.linbaogu.romhub.ui.component.GhostButton
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.data.Mirror
import org.linbaogu.romhub.data.RomVersion
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ---------------------------------------------------------------- 镜像
// 五个官方镜像（和 xiaomirom 等索引站一致）：同一路径、不同域名、全部免签名。
// 排在前面的实测永远能下（206），排在后面的 bigota/hugeota 可能 403 —— 按用户要求保留。
val MIRROR_HOSTS: List<Pair<String, String>> = listOf(
    "阿里云 OSS" to "https://bkt-sgp-miui-ota-update-alisgp.oss-ap-southeast-1.aliyuncs.com",
    "cdnorg" to "https://cdnorg.d.miui.com",
    "bn" to "https://bn.d.miui.com",
    "bigota" to "https://bigota.d.miui.com",
    "hugeota" to "https://hugeota.d.miui.com",
)

fun swapHost(url: String, base: String): String {
    if (url.isBlank()) return ""
    return try {
        val u = Uri.parse(url)
        val b = Uri.parse(base)
        b.buildUpon().path(u.path ?: "").query(u.query).fragment(u.fragment).build().toString()
    } catch (_: Exception) {
        url
    }
}

fun mirrorsOf(v: RomVersion): List<Mirror> {
    if (v.mirrors.isNotEmpty()) return v.mirrors
    val rec = v.recoveryUrl
    val fast = v.fastbootUrl
    if (rec.isBlank() && fast.isBlank()) return emptyList()
    return MIRROR_HOSTS.map { (name, base) ->
        Mirror(
            name = name,
            recovery = if (rec.isBlank()) "" else swapHost(rec, base),
            fastboot = if (fast.isBlank()) "" else swapHost(fast, base),
        )
    }
}

fun openUrl(ctx: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ---------------------------------------------------------------- 状态配色

fun stateColor(state: String): Color = when (state) {
    "公开" -> Color(0xFF1BA35A)
    "内测" -> Color(0xFFE08A16)
    "Beta" -> Color(0xFF2B7CD3)
    "撤包" -> Color(0xFFD8443C)
    "更新", "新版本" -> Color(0xFF0E9AA7)
    "移植包" -> Color(0xFF7A5AF8)
    else -> Color(0xFF6B7280)
}

fun kindLabel(kind: String, kindZh: String, state: String): String =
    state.ifBlank { kindZh.ifBlank { if (kind == "port") "移植包" else "更新" } }

// ---------------------------------------------------------------- 小组件

@Composable
fun StateChip(state: String, textOverride: String? = null, modifier: Modifier = Modifier) {
    val c = stateColor(state)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = textOverride ?: state,
            color = c,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) cs.primary else cs.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false) {
    if (value.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(end = 12.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            maxLines = if (mono) 6 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
fun Hint(text: String) {
    CenteredBox {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
        )
    }
}

@Composable
fun ErrorHint(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.error,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
        )
        if (onRetry != null) {
            top.yukonga.miuix.kmp.basic.TextButton(text = "重试", onClick = onRetry)
        }
    }
}

/** 卡片内边距统一走这个，避免各页不一致 */
val CardPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

@Composable
fun SectionLabel(text: String) {
    // HyperCeiler 的分组标题：主色小字，卡片上方左侧
    Text(
        text = text,
        color = MiuixTheme.colorScheme.primary,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

// ---------------------------------------------------------------- HyperCeiler 式分组卡片

/**
 * HyperCeiler 的列表结构：**一张实底大卡片里装多个行**，行与行之间细分割线，
 * 而不是每行一张散卡片。
 *
 * 用法：
 * ```
 * HcGroup {
 *     HcRow(title = "系统框架", subtitle = "system", onClick = { ... })
 *     HcDivider()
 *     HcRow(...)
 * }
 * ```
 */
@Composable
fun HcGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/** 行之间的细分割线（HyperCeiler：左侧缩进对齐文字）。 */
@Composable
fun HcDivider(startIndent: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        color = MiuixTheme.colorScheme.dividerLine,
        thickness = 0.5.dp,
    )
}

/**
 * HyperCeiler 式列表行：标题 + 副标题 + 右侧自定义内容（默认细箭头）。
 * 点击整行生效；不传 onClick 就是纯展示行。
 */
@Composable
fun HcRow(
    title: String,
    subtitle: String = "",
    trailing: @Composable (() -> Unit)? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = 56.dp,
) {
    val cs = MiuixTheme.colorScheme
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = cs.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = cs.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showArrow) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = cs.onSurfaceVariantSummary.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    if (onClick != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .clickable(onClick = onClick),
        ) { row() }
    } else {
        row()
    }
}

/** HyperCeiler 式搜索框：全宽胶囊、浅灰底、左侧放大镜。 */
@Composable
fun HcSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MiuixIcons.Search,
            contentDescription = null,
            tint = cs.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = cs.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = cs.onSurfaceVariantSummary,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
