package org.linbaogu.romhub.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 公告富文本：一段纯文本公告，渲染成「可复制 + 链接可点 + 图片可见」。
 *
 * 作者公告是自由文本，里面经常会有下载地址、截图链接。原来只是干巴巴一段 Text，
 * 链接点不了、图片看不了、也不能复制。这里统一处理：
 *
 *   1. https://...  渲染成蓝色下划线，点击直接用浏览器打开
 *   2. 图片链接（png/jpg/gif/webp/bmp）渲染成图片
 *   3. 整段文字长按可选中复制，另外还给了「复制公告」按钮
 *
 * App（移植包详情）和以后其它要显示公告的地方都用它，保证多端表现一致。
 */

/** 匹配 http/https 链接，遇到空白和常见中文标点就停，避免把后面的正文吞进来。 */
private val URL_RE = Regex("""https?://[^\s　，。；、）】"'<>`]+""")

private val IMG_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private fun isImageUrl(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#')
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in IMG_EXTS
}

@Composable
fun NoticeRichText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cs = MiuixTheme.colorScheme

    // 图片单独抽出来显示在文字下方（AnnotatedString 里塞不进图片）
    val images = remember(text) {
        URL_RE.findAll(text).map { it.value }.filter(::isImageUrl).toList()
    }

    // 正文：普通文字保持原样，链接加蓝色下划线，并挂上可点击的注解
    val annotated: AnnotatedString = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            for (m in URL_RE.findAll(text)) {
                if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
                pushStringAnnotation("URL", m.value)
                pushStyle(
                    SpanStyle(
                        color = cs.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                )
                append(m.value)
                pop()   // 弹出样式
                pop()   // 弹出链接注解
                cursor = m.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Column(modifier.fillMaxWidth()) {
        // 长按可以选中复制；单击仍然走链接点击
        SelectionContainer {
            ClickableText(
                text = annotated,
                style = MiuixTheme.textStyles.body1.copy(color = cs.onSurface),
                onClick = { offset ->
                    annotated.getStringAnnotations("URL", offset, offset)
                        .firstOrNull()
                        ?.let { openUrl(ctx, it.item) }
                },
            )
        }

        images.forEach { url ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = url,
                contentDescription = "公告图片",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        Spacer(Modifier.height(8.dp))
        GhostButton("复制公告") {
            clipboard.setText(AnnotatedString(text))
        }
    }
}
