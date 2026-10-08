package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 极简 Markdown 渲染器（只覆盖 README 里真正会出现的语法）。
 *
 * 为什么不引 mikepenz/multiplatform-markdown-renderer：
 *   · 那个库要额外拉一堆传递依赖，还要跟 Kotlin 版本对齐（0.35.0 只适配 Kotlin 2.1），
 *     本项目是 Kotlin 2.4 —— 引进来大概率是版本地狱；
 *   · README 里真正需要渲染的就那么几种：标题、列表、代码块、行内代码、引用、分割线、链接。
 *     自己写 200 行搞定，还不会被库升级搞坏。
 *
 * 不支持表格、嵌套列表、HTML —— 遇到就按普通文本展示，不会崩。
 */
@Composable
fun SimpleMarkdown(
    source: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(source) { source.replace("\r\n", "\n").split('\n') }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

            when {
                // 代码块 ```
                line.trimStart().startsWith("```") -> {
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.appendLine(lines[i])
                        i++
                    }
                    i++ // 跳过收尾的 ```
                    CodeBlock(code.toString().trimEnd())
                    continue
                }

                // 空行
                line.isBlank() -> Spacer(Modifier.height(2.dp))

                // 分割线
                line.trim() == "---" || line.trim() == "***" || line.trim() == "___" -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.dividerLine),
                    )
                }

                // 标题
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(4)
                    val text = line.drop(level).trim()
                    if (text.isNotBlank()) MarkdownHeading(level, text)
                }

                // 引用
                line.trimStart().startsWith(">") -> {
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        InlineMarkdown(line.trimStart().removePrefix(">").trim(), footnote = true)
                    }
                }

                // 无序列表
                line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") || it.startsWith("+ ") } -> {
                    val text = line.trimStart().drop(2)
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "•",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        InlineMarkdown(text)
                    }
                }

                // 有序列表
                Regex("""^\s*(\d+)\.\s+(.*)$""").find(line) != null -> {
                    val m = Regex("""^\s*(\d+)\.\s+(.*)$""").find(line)!!
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${m.groupValues[1]}.",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        InlineMarkdown(m.groupValues[2])
                    }
                }

                // 普通段落
                else -> InlineMarkdown(line)
            }
            i++
        }
    }
}

@Composable
private fun MarkdownHeading(level: Int, text: String) {
    val cs = MiuixTheme.colorScheme
    val size = when (level) {
        1 -> 20.sp
        2 -> 17.sp
        3 -> 15.sp
        else -> 14.sp
    }
    Text(
        text = stripInline(text),
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        color = cs.onSurface,
        modifier = Modifier.padding(top = if (level <= 2) 8.dp else 4.dp),
    )
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
    ) {
        Text(
            code,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 正文行：处理 **粗体**、`行内代码`、[文字](链接)、~~删除线~~。 */
@Composable
private fun InlineMarkdown(text: String, footnote: Boolean = false) {
    if (text.isBlank()) return
    Text(
        text = renderInline(text),
        fontSize = if (footnote) MiuixTheme.textStyles.footnote2.fontSize
        else MiuixTheme.textStyles.footnote1.fontSize,
        color = if (footnote) MiuixTheme.colorScheme.onSurfaceVariantSummary
        else MiuixTheme.colorScheme.onSurface,
        lineHeight = if (footnote) 17.sp else 19.sp,
    )
}

/** 把一行 Markdown 转成 AnnotatedString（只处理行内语法）。 */
private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    // 先把 [文字](链接) 收敛成「文字」，markdown 里的相对链接在这没法点，去掉 URL 更清爽
    val src = Regex("""\[([^\]]*)]\((?:[^)]*)\)""").replace(text) { it.groupValues[1] }

    var i = 0
    while (i < src.length) {
        when {
            // 行内代码
            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(src[i]); i++
                }
            }

            // 粗体 **text**
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(src[i]); i++
                }
            }

            // 斜体 *text* 或 _text_
            src[i] == '*' || src[i] == '_' -> {
                val marker = src[i]
                val end = src.indexOf(marker, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(src[i]); i++
                }
            }

            // 删除线 ~~text~~
            src.startsWith("~~", i) -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(src[i]); i++
                }
            }

            else -> {
                append(src[i]); i++
            }
        }
    }
}

/** 不需要标注的纯文本（标题用）。 */
private fun stripInline(text: String): String =
    text.replace(Regex("""[*_~`]"""), "").trim()
