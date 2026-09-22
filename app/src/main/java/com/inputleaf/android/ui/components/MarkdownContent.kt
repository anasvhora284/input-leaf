package com.inputleaf.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String, val isOrdered: Boolean) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

object MarkdownParser {

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val BULLET_REGEX = Regex("^([*+\\-•])\\s+(.+)$")
    private val ORDERED_REGEX = Regex("^(\\d+\\.)\\s+(.+)$")
    private val HR_REGEX = Regex("^(---|\\*\\*\\*|___)\\s*$")
    private val BLOCKQUOTE_REGEX = Regex("^>\\s*(.*)$")

    // Inline regex tokens
    private val INLINE_TOKEN_REGEX = Regex(
        "(\\[[^\\]]+]\\([^)]+\\))|" + // [text](url)
        "(https?://[^\\s<>\"]+)|" +    // raw url
        "(`[^`]+`)|" +                 // `code`
        "(\\*\\*[^\\*]+\\*\\*)|" +     // **bold**
        "(__[^_]+__)|" +               // __bold__
        "(\\*[^*]+\\*)|" +             // *italic*
        "(_[^_]+_)|" +                 // _italic_
        "(@[a-zA-Z0-9_\\-]+)"          // @mention
    )

    fun parseBlocks(markdown: String): List<MarkdownBlock> {
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()
        var codeBlockLang = ""

        for (line in lines) {
            val trimmed = line.trimEnd()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    blocks.add(MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n"), codeBlockLang))
                    codeBlockLines.clear()
                    inCodeBlock = false
                    codeBlockLang = ""
                } else {
                    inCodeBlock = true
                    codeBlockLang = trimmed.removePrefix("```").trim()
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                continue
            }

            val trimmedLine = trimmed.trim()
            if (trimmedLine.isEmpty()) {
                continue
            }

            val hrMatch = HR_REGEX.matchEntire(trimmedLine)
            if (hrMatch != null) {
                blocks.add(MarkdownBlock.Divider)
                continue
            }

            val headingMatch = HEADING_REGEX.matchEntire(trimmedLine)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.Heading(level, text))
                continue
            }

            val bulletMatch = BULLET_REGEX.matchEntire(trimmedLine)
            if (bulletMatch != null) {
                val marker = bulletMatch.groupValues[1]
                val text = bulletMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.ListItem(marker = marker, text = text, isOrdered = false))
                continue
            }

            val orderedMatch = ORDERED_REGEX.matchEntire(trimmedLine)
            if (orderedMatch != null) {
                val marker = orderedMatch.groupValues[1]
                val text = orderedMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.ListItem(marker = marker, text = text, isOrdered = true))
                continue
            }

            val bqMatch = BLOCKQUOTE_REGEX.matchEntire(trimmedLine)
            if (bqMatch != null) {
                blocks.add(MarkdownBlock.Blockquote(bqMatch.groupValues[1].trim()))
                continue
            }

            blocks.add(MarkdownBlock.Paragraph(trimmedLine))
        }

        if (inCodeBlock) {
            blocks.add(MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n"), codeBlockLang))
        }

        return blocks
    }

    /**
     * Whether a URL out of a release body may be handed to [LinkAnnotation.Url].
     *
     * Changelog text is fetched from the GitHub release API, so a tampered,
     * typosquatted or compromised body can name any scheme. The default
     * `AndroidUriHandler` dispatches whatever it is through `ACTION_VIEW`, which turns
     * one tap in the update dialog into an arbitrary implicit intent. Restricted to
     * http(s), and deliberately fails closed: anything not matching exactly, including
     * odd casing or leading whitespace, renders as plain text instead.
     *
     * Both link branches in [parseInline] go through this so they cannot drift apart
     * again -- the raw-URL branch was already restricted while the Markdown-link
     * branch was not.
     */
    internal fun isDispatchableUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    fun parseInline(
        text: String,
        linkColor: Color = Color(0xFF1E88E5),
        codeBackground: Color = Color.LightGray.copy(alpha = 0.3f),
        onSurfaceColor: Color = Color.Unspecified,
    ): AnnotatedString = buildAnnotatedString {
        var lastIndex = 0
        val matches = INLINE_TOKEN_REGEX.findAll(text)

        for (match in matches) {
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }

            val token = match.value
            when {
                // Markdown link: [text](url)
                token.startsWith("[") && token.contains("](") && token.endsWith(")") -> {
                    val label = token.substringAfter("[").substringBefore("](")
                    val url = token.substringAfter("](").substringBeforeLast(")")
                    if (isDispatchableUrl(url)) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            )
                        ) {
                            append(label)
                        }
                    } else {
                        append(label)
                    }
                }

                // Raw URL
                isDispatchableUrl(token) -> {
                    withLink(
                        LinkAnnotation.Url(
                            url = token,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(token)
                    }
                }

                // Inline code: `code`
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    val codeText = token.substring(1, token.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            fontSize = 12.sp
                        )
                    ) {
                        append(" $codeText ")
                    }
                }

                // Bold: **text** or __text__
                (token.startsWith("**") && token.endsWith("**") && token.length >= 4) ||
                (token.startsWith("__") && token.endsWith("__") && token.length >= 4) -> {
                    val boldText = token.substring(2, token.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                }

                // Italic: *text* or _text_
                (token.startsWith("*") && token.endsWith("*") && token.length >= 2) ||
                (token.startsWith("_") && token.endsWith("_") && token.length >= 2) -> {
                    val italicText = token.substring(1, token.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italicText)
                    }
                }

                // GitHub mention: @user
                token.startsWith("@") -> {
                    val username = token.removePrefix("@")
                    withLink(
                        LinkAnnotation.Url(
                            url = "https://github.com/$username",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        )
                    ) {
                        append(token)
                    }
                }

                else -> {
                    append(token)
                }
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
) {
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (style, topPad) = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium to 8.dp
                        2 -> MaterialTheme.typography.titleSmall to 6.dp
                        else -> MaterialTheme.typography.labelLarge to 4.dp
                    }
                    Text(
                        text = MarkdownParser.parseInline(block.text, linkColor, codeBackground),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = topPad)
                    )
                }

                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (block.isOrdered) {
                            Text(
                                text = block.marker,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 18.sp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            text = MarkdownParser.parseInline(block.text, linkColor, codeBackground),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = MarkdownParser.parseInline(block.text, linkColor, codeBackground),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(codeBackground)
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = MarkdownParser.parseInline(block.text, linkColor, codeBackground),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
