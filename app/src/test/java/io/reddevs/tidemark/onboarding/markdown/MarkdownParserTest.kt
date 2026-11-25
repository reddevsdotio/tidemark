// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding.markdown

import io.reddevs.tidemark.onboarding.markdown.MarkdownBlock.BulletList
import io.reddevs.tidemark.onboarding.markdown.MarkdownBlock.Heading
import io.reddevs.tidemark.onboarding.markdown.MarkdownBlock.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {
    private fun text(value: String) = MarkdownSpan(value)

    @Test
    fun headingsOfEveryLevel() {
        val blocks = MarkdownParser.parse("# One\n## Two\n### Three ###\n###### Six")
        assertEquals(
            listOf(
                Heading(1, listOf(text("One"))),
                Heading(2, listOf(text("Two"))),
                Heading(3, listOf(text("Three"))),
                Heading(6, listOf(text("Six"))),
            ),
            blocks,
        )
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        assertEquals(listOf(Paragraph(listOf(text("#hashtag")))), MarkdownParser.parse("#hashtag"))
    }

    @Test
    fun paragraphsJoinLinesAndSplitOnBlankLines() {
        val blocks = MarkdownParser.parse("First line\nsecond line.\n\nNext paragraph.")
        assertEquals(
            listOf(Paragraph(listOf(text("First line second line."))), Paragraph(listOf(text("Next paragraph.")))),
            blocks,
        )
    }

    @Test
    fun windowsLineEndingsAreHandled() {
        assertEquals(
            listOf(Heading(1, listOf(text("Title"))), Paragraph(listOf(text("Body")))),
            MarkdownParser.parse("# Title\r\n\r\nBody\r\n"),
        )
    }

    @Test
    fun bulletListsWithEveryMarkerAndContinuationLines() {
        val blocks = MarkdownParser.parse("Intro\n- one\n* two\n  continued\n+ three\n\nAfter")
        assertEquals(
            listOf(
                Paragraph(listOf(text("Intro"))),
                BulletList(listOf(listOf(text("one")), listOf(text("two continued")), listOf(text("three")))),
                Paragraph(listOf(text("After"))),
            ),
            blocks,
        )
    }

    @Test
    fun boldWithBothMarkers() {
        assertEquals(
            listOf(text("We "), MarkdownSpan("never", bold = true), text(" sell "), MarkdownSpan("data", bold = true), text(".")),
            MarkdownParser.parseInline("We **never** sell __data__."),
        )
    }

    @Test
    fun boldInsideHeadingsAndBullets() {
        val blocks = MarkdownParser.parse("## The **short** version\n- **No** network")
        assertEquals(
            listOf(
                Heading(2, listOf(text("The "), MarkdownSpan("short", bold = true), text(" version"))),
                BulletList(listOf(listOf(MarkdownSpan("No", bold = true), text(" network")))),
            ),
            blocks,
        )
    }

    @Test
    fun inlineLinks() {
        assertEquals(
            listOf(
                text("Open an "),
                MarkdownSpan("issue", url = "https://github.com/reddevsdotio/tidemark/issues"),
                text("."),
            ),
            MarkdownParser.parseInline("Open an [issue](https://github.com/reddevsdotio/tidemark/issues)."),
        )
    }

    @Test
    fun boldLinkAndLinkInsideBold() {
        assertEquals(
            listOf(MarkdownSpan("site", bold = true, url = "https://tidemark.reddevs.io/")),
            MarkdownParser.parseInline("[**site**](https://tidemark.reddevs.io/)"),
        )
        assertEquals(
            listOf(MarkdownSpan("see ", bold = true), MarkdownSpan("here", bold = true, url = "mailto:a@b.c")),
            MarkdownParser.parseInline("**see [here](mailto:a@b.c)**"),
        )
    }

    @Test
    fun autolinksAndBareUrls() {
        assertEquals(
            listOf(text("At "), MarkdownSpan("https://tidemark.reddevs.io/privacy/", url = "https://tidemark.reddevs.io/privacy/")),
            MarkdownParser.parseInline("At <https://tidemark.reddevs.io/privacy/>"),
        )
        assertEquals(
            listOf(text("See "), MarkdownSpan("https://tidemark.reddevs.io/fr/privacy/", url = "https://tidemark.reddevs.io/fr/privacy/"), text(".")),
            MarkdownParser.parseInline("See https://tidemark.reddevs.io/fr/privacy/."),
        )
    }

    @Test
    fun unsafeLinkSchemesBecomePlainText() {
        assertEquals(listOf(text("click")), MarkdownParser.parseInline("[click](javascript:alert)"))
        assertEquals(listOf(text("app")), MarkdownParser.parseInline("[app](intent://x)"))
        assertEquals(listOf(text("<file:///etc/passwd>")), MarkdownParser.parseInline("<file:///etc/passwd>"))
    }

    @Test
    fun unclosedMarkersStayLiteral() {
        assertEquals(listOf(text("a ** b [c] (d) [e](")), MarkdownParser.parseInline("a ** b [c] (d) [e]("))
    }

    @Test
    fun backslashEscapes() {
        assertEquals(listOf(text("**not bold** [x]")), MarkdownParser.parseInline("\\*\\*not bold\\*\\* \\[x\\]"))
    }

    @Test
    fun unsupportedInlineSyntaxFallsBackToPlainText() {
        // Italics, inline code and images are not supported: their characters stay visible.
        assertEquals(listOf(text("*italic* `code` !")), MarkdownParser.parseInline("*italic* `code` !"))
    }

    @Test
    fun unsupportedBlocksFallBackToPlainTextLines() {
        val blocks = MarkdownParser.parse("1. first\n2. second\n> quoted\n```\n| a | b |\n---\n* * *")
        assertEquals(
            listOf("1. first", "2. second", "> quoted", "```", "| a | b |", "---", "* * *")
                .map { Paragraph(listOf(text(it))) },
            blocks,
        )
    }

    @Test
    fun emptyInputHasNoBlocks() {
        assertEquals(emptyList<MarkdownBlock>(), MarkdownParser.parse("\n \n"))
    }

    @Test
    fun aRealisticPolicy() {
        val policy = """
            # Privacy policy

            **Effective date:** 19 November 2026

            ## What Tidemark reads
            Tidemark reads these types from Health Connect:
            - Steps
            - Sleep

            Questions: [GitHub issues](https://github.com/reddevsdotio/tidemark/issues).
        """.trimIndent()
        val blocks = MarkdownParser.parse(policy)
        assertEquals(6, blocks.size)
        assertEquals(Heading(1, listOf(text("Privacy policy"))), blocks[0])
        assertEquals(Paragraph(listOf(MarkdownSpan("Effective date:", bold = true), text(" 19 November 2026"))), blocks[1])
        assertEquals(BulletList(listOf(listOf(text("Steps")), listOf(text("Sleep")))), blocks[4])
    }
}
