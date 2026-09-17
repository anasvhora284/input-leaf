package com.inputleaf.android.ui

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.ui.components.MarkdownBlock
import com.inputleaf.android.ui.components.MarkdownParser
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parseBlocks_extractsHeadingsCorrectly() {
        val markdown = """
            # Title 1
            ## Title 2
            ### Title 3
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertThat(blocks).hasSize(3)

        val h1 = blocks[0] as MarkdownBlock.Heading
        assertThat(h1.level).isEqualTo(1)
        assertThat(h1.text).isEqualTo("Title 1")

        val h2 = blocks[1] as MarkdownBlock.Heading
        assertThat(h2.level).isEqualTo(2)
        assertThat(h2.text).isEqualTo("Title 2")

        val h3 = blocks[2] as MarkdownBlock.Heading
        assertThat(h3.level).isEqualTo(3)
        assertThat(h3.text).isEqualTo("Title 3")
    }

    @Test
    fun parseBlocks_extractsBulletListItems() {
        val markdown = """
            * Star bullet
            - Dash bullet
            + Plus bullet
            • Unicode bullet
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertThat(blocks).hasSize(4)

        for (b in blocks) {
            assertThat(b).isInstanceOf(MarkdownBlock.ListItem::class.java)
            val item = b as MarkdownBlock.ListItem
            assertThat(item.isOrdered).isFalse()
        }

        assertThat((blocks[0] as MarkdownBlock.ListItem).text).isEqualTo("Star bullet")
        assertThat((blocks[1] as MarkdownBlock.ListItem).text).isEqualTo("Dash bullet")
        assertThat((blocks[2] as MarkdownBlock.ListItem).text).isEqualTo("Plus bullet")
        assertThat((blocks[3] as MarkdownBlock.ListItem).text).isEqualTo("Unicode bullet")
    }

    @Test
    fun parseBlocks_extractsOrderedListItems() {
        val markdown = """
            1. First item
            2. Second item
            10. Tenth item
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertThat(blocks).hasSize(3)

        val item1 = blocks[0] as MarkdownBlock.ListItem
        assertThat(item1.isOrdered).isTrue()
        assertThat(item1.marker).isEqualTo("1.")
        assertThat(item1.text).isEqualTo("First item")

        val item2 = blocks[1] as MarkdownBlock.ListItem
        assertThat(item2.isOrdered).isTrue()
        assertThat(item2.marker).isEqualTo("2.")
        assertThat(item2.text).isEqualTo("Second item")

        val item3 = blocks[2] as MarkdownBlock.ListItem
        assertThat(item3.isOrdered).isTrue()
        assertThat(item3.marker).isEqualTo("10.")
        assertThat(item3.text).isEqualTo("Tenth item")
    }

    @Test
    fun parseBlocks_extractsCodeBlock() {
        val markdown = """
            ```kotlin
            val x = 42
            println(x)
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertThat(blocks).hasSize(1)

        val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
        assertThat(codeBlock.language).isEqualTo("kotlin")
        assertThat(codeBlock.code).isEqualTo("val x = 42\nprintln(x)")
    }

    @Test
    fun parseBlocks_extractsBlockquote() {
        val markdown = "> This is a blockquote note"

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertThat(blocks).hasSize(1)

        val bq = blocks[0] as MarkdownBlock.Blockquote
        assertThat(bq.text).isEqualTo("This is a blockquote note")
    }

    @Test
    fun parseBlocks_extractsDivider() {
        val markdown = """
            Paragraph before
            ---
            Paragraph after
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertThat(blocks).hasSize(3)
        assertThat(blocks[0]).isInstanceOf(MarkdownBlock.Paragraph::class.java)
        assertThat(blocks[1]).isInstanceOf(MarkdownBlock.Divider::class.java)
        assertThat(blocks[2]).isInstanceOf(MarkdownBlock.Paragraph::class.java)
    }

    @Test
    fun parseBlocks_handlesRealGitHubRelease() {
        val releaseNotes = """
            ## What's Changed
            * Show mouse cursor in accessibility service by @anasvhora284 in https://github.com/anasvhora284/input-leaf/pull/41
            * Use real HID keyboard in Shizuku mode by @anasvhora284 in https://github.com/anasvhora284/input-leaf/pull/42

            **Full Changelog**: https://github.com/anasvhora284/input-leaf/compare/1.4.1...v1.4.2
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(releaseNotes)
        assertThat(blocks).hasSize(4)

        assertThat(blocks[0]).isInstanceOf(MarkdownBlock.Heading::class.java)
        assertThat((blocks[0] as MarkdownBlock.Heading).text).isEqualTo("What's Changed")

        assertThat(blocks[1]).isInstanceOf(MarkdownBlock.ListItem::class.java)
        assertThat((blocks[1] as MarkdownBlock.ListItem).text).contains("Show mouse cursor")

        assertThat(blocks[2]).isInstanceOf(MarkdownBlock.ListItem::class.java)
        assertThat((blocks[2] as MarkdownBlock.ListItem).text).contains("Use real HID keyboard")

        assertThat(blocks[3]).isInstanceOf(MarkdownBlock.Paragraph::class.java)
        assertThat((blocks[3] as MarkdownBlock.Paragraph).text).contains("Full Changelog")
    }

    @Test
    fun parseInline_extractsBoldText() {
        val annotated = MarkdownParser.parseInline("Hello **World** and __Universe__")
        assertThat(annotated.text).isEqualTo("Hello World and Universe")
        assertThat(annotated.spanStyles).isNotEmpty()
    }

    @Test
    fun parseInline_extractsItalicText() {
        val annotated = MarkdownParser.parseInline("Hello *World* and _Universe_")
        assertThat(annotated.text).isEqualTo("Hello World and Universe")
        assertThat(annotated.spanStyles).isNotEmpty()
    }

    @Test
    fun parseInline_extractsInlineCode() {
        val annotated = MarkdownParser.parseInline("Use `input tap` to click")
        assertThat(annotated.text).isEqualTo("Use  input tap  to click")
        assertThat(annotated.spanStyles).isNotEmpty()
    }

    @Test
    fun parseInline_extractsMarkdownLink() {
        val annotated = MarkdownParser.parseInline("Check [Input Leaf](https://inputleaf.com) today")
        assertThat(annotated.text).isEqualTo("Check Input Leaf today")
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertThat(links).isNotEmpty()
    }

    @Test
    fun parseInline_extractsRawUrl() {
        val annotated = MarkdownParser.parseInline("Visit https://github.com/anasvhora284 now")
        assertThat(annotated.text).isEqualTo("Visit https://github.com/anasvhora284 now")
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertThat(links).isNotEmpty()
    }

    @Test
    fun parseInline_extractsGitHubMention() {
        val annotated = MarkdownParser.parseInline("Contributed by @anasvhora284")
        assertThat(annotated.text).isEqualTo("Contributed by @anasvhora284")
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertThat(links).isNotEmpty()
    }

    @Test
    fun parseInline_plainTextWithoutTokens() {
        val raw = "Plain text without any formatting."
        val annotated = MarkdownParser.parseInline(raw)
        assertThat(annotated.text).isEqualTo(raw)
        assertThat(annotated.spanStyles).isEmpty()
    }
}
