package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the server-rendered HTML question list/search/tag pages of https://www.33iq.com.
 *
 * Question *detail* pages are no longer scraped from HTML here - see [QuestionJsonParser] for the
 * real app-facing JSON endpoint discovered from a captured app session.
 */
internal class QuestionHtmlParser {
    fun parseQuestionSummaries(document: Document): List<QuestionSummary> =
        document
            .select("div.linktopic[itemtype*=Question]")
            .mapNotNull { element -> parseSummary(element) }
            .distinctBy { it.id }

    private fun parseSummary(element: Element): QuestionSummary? {
        val link = element.selectFirst(".title a") ?: return null
        val id = questionIdFromHref(link.attr("href")) ?: return null
        val title = link.text()

        val statsText =
            element
                .select(".info span")
                .firstOrNull()
                ?.text()
                .orEmpty()
        val upvoteCount = extractCount(statsText, "点赞")
        val commentCount = extractCount(statsText, "评论")

        val tags = element.select(".info .pull-right a").map { it.text() }

        return QuestionSummary(
            id = id,
            title = title,
            tags = tags,
            upvoteCount = upvoteCount,
            commentCount = commentCount,
        )
    }

    private fun questionIdFromHref(href: String): Long? =
        Regex("""/question/(\d+)\.html""")
            .find(href)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    private fun extractCount(
        text: String,
        label: String,
    ): Int =
        Regex("""(\d+)\s*$label""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull() ?: 0
}
