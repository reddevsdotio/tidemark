// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding.markdown

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.ui.theme.TidemarkTheme

/** Renders parsed Markdown blocks as a scrolling column. Links open in the browser through ACTION_VIEW. */
@Composable
fun MarkdownDocument(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(blocks) { _, block ->
            MarkdownBlockView(block, linkColor, onOpenLink = { openLink(context, it) })
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, linkColor: Color, onOpenLink: (String) -> Unit) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = rememberAnnotated(block.content, linkColor, onOpenLink),
            style = headingStyle(block.level),
            modifier = Modifier
                .padding(top = if (block.level <= 2) 8.dp else 4.dp)
                .semantics { heading() },
        )

        is MarkdownBlock.Paragraph -> Text(
            text = rememberAnnotated(block.content, linkColor, onOpenLink),
            style = MaterialTheme.typography.bodyLarge,
        )

        is MarkdownBlock.BulletList -> Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = rememberAnnotated(item, linkColor, onOpenLink),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

@Composable
private fun rememberAnnotated(
    spans: List<MarkdownSpan>,
    linkColor: Color,
    onOpenLink: (String) -> Unit,
): AnnotatedString = remember(spans, linkColor) { toAnnotatedString(spans, linkColor, onOpenLink) }

internal fun toAnnotatedString(
    spans: List<MarkdownSpan>,
    linkColor: Color,
    onOpenLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    spans.forEach { span ->
        val weight = if (span.bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()
        withStyle(weight) {
            val url = span.url
            if (url == null) {
                append(span.text)
            } else {
                withLink(LinkAnnotation.Url(url, linkStyles) { onOpenLink(url) }) { append(span.text) }
            }
        }
    }
}

private fun openLink(context: Context, url: String) {
    if (!MarkdownParser.isSafeUrl(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        // No browser or mail app: the link text stays readable on screen.
    }
}

@Preview(showBackground = true)
@Composable
private fun MarkdownDocumentPreview() {
    val sample = """
        # Privacy policy
        Tidemark reads **eight types** of data from Health Connect.

        ## What Tidemark stores
        - Your unit preference
        - The date you first allowed access

        Questions? Open an issue on [GitHub](https://github.com/reddevsdotio/tidemark/issues).
    """.trimIndent()
    TidemarkTheme {
        MarkdownDocument(MarkdownParser.parse(sample), contentPadding = PaddingValues(16.dp))
    }
}
