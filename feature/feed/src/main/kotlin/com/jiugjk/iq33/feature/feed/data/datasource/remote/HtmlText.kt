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

/**
 * Extracts the absolute `src` of every image in [html], in document order, each upgraded to the
 * original upload by [fullSizeImageUrl].
 */
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
        .map(::fullSizeImageUrl)
        .distinct()
}

/**
 * Rewrites a 33IQ image URL to the original upload.
 *
 * A question's body HTML never embeds its own picture: it embeds a *thumbnail* of it. For questions
 * whose picture is the puzzle (a crime scene, a diagram) that thumbnail is unreadable, which is what
 * made images look blurry in this client while the official app showed them sharp.
 *
 * Two independent downscalers are applied by the site, and both are undone here. Measured live on
 * one question's single image (`.../upload/26/09/03/<name>.jpg`):
 *
 * | URL form                     | Size      |
 * |------------------------------|-----------|
 * | `_thumbs/<name>.jpg!33.jpg`  | 120x89    | <- what `qc_context` embeds
 * | `_thumbs/<name>.jpg`         | 120x89    |
 * | `_thumbs/big/<name>.jpg`     | 550x410   |
 * | `images/<name>.jpg!33.jpg`   | 550x411   |
 * | `images/<name>.jpg`          | 1776x1327 | <- the original, and what the JSON's `pic` points at
 *
 * So the `_thumbs[/big]` directory picks a stored rendition and the `!<style>` suffix picks a
 * CDN-resized one; dropping both yields the original. A URL matching neither is returned untouched,
 * so a differently-shaped (or already full-size) address is never mangled.
 */
internal fun fullSizeImageUrl(url: String): String =
    url
        .replace(RENDITION_STYLE_SUFFIX, "")
        .replace(BIG_THUMBS_SEGMENT, ORIGINALS_SEGMENT)
        .replace(THUMBS_SEGMENT, ORIGINALS_SEGMENT)

/**
 * The CDN's "render this at style X" suffix, e.g. `...jpg!33.jpg`. Anchored past the last `/` so a
 * `!` inside a directory name - or a query string - is left alone.
 */
private val RENDITION_STYLE_SUFFIX = Regex("""![^/?#]*$""")

private const val THUMBS_SEGMENT = "/_thumbs/"
private const val BIG_THUMBS_SEGMENT = "/_thumbs/big/"
private const val ORIGINALS_SEGMENT = "/images/"

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
