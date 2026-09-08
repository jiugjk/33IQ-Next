package com.jiugjk.iq33.feature.feed.data.datasource.remote

import org.jsoup.Jsoup

/**
 * Converts 33IQ's arbitrary rich-text HTML (question bodies, answer explanations) to plain text,
 * preserving paragraph breaks. Walks all "leaf" block-level elements (ones with no nested block
 * child, to avoid emitting a parent's text twice) so list items, table cells and unwrapped `<div>`
 * text all survive - not just `<p>` content, which previously left content silently truncated.
 */
internal fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""

    val document = Jsoup.parse(html)
    document.select("br").before("\n")

    val blocks =
        document
            .select(BLOCK_SELECTOR)
            .filter { element -> element.select(BLOCK_SELECTOR).isEmpty() }
            .map { it.text() }
            .filter { it.isNotBlank() }

    return blocks.joinToString("\n\n").ifBlank { document.text() }
}

private const val BLOCK_SELECTOR = "p, li, div, td, h1, h2, h3, h4, h5, h6"
