package com.jiugjk.iq33.feature.feed.data.datasource.remote

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Converts 33IQ's arbitrary rich-text HTML (question bodies, answer explanations) to plain text,
 * preserving line and paragraph breaks.
 *
 * The document is walked in DOM order rather than by selecting "leaf" block elements: a `<div>` that
 * mixes its own text with a nested `<p>` has no leaf to select, and selecting only leaves drops that
 * text. Walking emits a hard break for each `<br>` and a paragraph break at every block boundary,
 * which is then normalised - so text is never squashed into one line by whitespace normalisation.
 */
internal fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""

    val builder = StringBuilder()

    NodeTraversor.traverse(
        object : NodeVisitor {
            override fun head(
                node: Node,
                depth: Int,
            ) {
                when {
                    node is TextNode -> builder.append(node.text())
                    node is Element && node.normalName() == "br" -> builder.append('\n')
                    node is Element && node.isBlock -> builder.append(BLOCK_BREAK)
                }
            }

            override fun tail(
                node: Node,
                depth: Int,
            ) {
                if (node is Element && node.isBlock) builder.append(BLOCK_BREAK)
            }
        },
        Jsoup.parse(html).body(),
    )

    return builder.toString().toParagraphs()
}

/** Extracts the absolute `src` of every image in [html], in document order. */
internal fun htmlImageUrls(
    html: String,
    baseUrl: String,
): List<String> {
    if (html.isBlank()) return emptyList()

    return Jsoup
        .parse(html, baseUrl)
        .select("img[src]")
        .map { image -> image.absUrl("src").ifBlank { image.attr("src") } }
        .filter { it.isNotBlank() }
        .distinct()
}

/**
 * Turns the walker's break markers into text: blocks become blank-line-separated paragraphs, `<br>`
 * breaks stay single newlines, and runs of horizontal whitespace collapse to one space.
 */
private fun String.toParagraphs(): String =
    split(BLOCK_BREAK)
        .map { block ->
            block
                .lines()
                .joinToString("\n") { line -> line.replace(HORIZONTAL_WHITESPACE, " ").trim() }
                .trim('\n')
        }.filter { it.isNotBlank() }
        .joinToString("\n\n")

/** Marks a block-element boundary while walking; never appears in HTML text itself. */
private const val BLOCK_BREAK = '\u0000'

private val HORIZONTAL_WHITESPACE = Regex("""[^\S\n]+""")
