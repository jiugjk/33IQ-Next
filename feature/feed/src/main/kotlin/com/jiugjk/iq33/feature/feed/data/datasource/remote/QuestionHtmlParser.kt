package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

/** 33IQ returned HTML that is neither a question list nor a confirmed empty list. */
internal class UnexpectedPageException : IOException("33IQ served a page that is not a question list")

/**
 * Parses the server-rendered HTML question list/search/tag pages of https://www.33iq.com.
 *
 * Question *detail* pages are no longer scraped from HTML here - see [QuestionJsonParser] for the
 * real app-facing JSON endpoint discovered from a captured app session.
 */
internal class QuestionHtmlParser {
    fun parseQuestionSummaries(document: Document): List<QuestionSummary> {
        val nodes = document.select("div.linktopic[itemtype*=Question]")
        val questions = nodes.mapNotNull { element -> parseSummary(element) }.distinctBy { it.id }

        if (questions.isEmpty() && !looksLikeQuestionList(document)) {
            throw UnexpectedPageException()
        }

        return questions
    }

    private fun looksLikeQuestionList(document: Document): Boolean {
        if (document.selectFirst("div.linktopic") != null) return true
        if (document.selectFirst(".pagination") != null) return true

        val title = document.title()

        return title.contains("题目") || title.contains("搜索")
    }

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
        val upvoteCount = extractCount(statsText, UPVOTE_COUNT)
        val commentCount = extractCount(statsText, COMMENT_COUNT)

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
        QUESTION_ID_IN_HREF
            .find(href)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    private fun extractCount(
        text: String,
        pattern: Regex,
    ): Int =
        pattern
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull() ?: 0

    private companion object {
        val QUESTION_ID_IN_HREF = Regex("""/question/(\d+)\.html""")
        val UPVOTE_COUNT = Regex("""(\d+)\s*点赞""")
        val COMMENT_COUNT = Regex("""(\d+)\s*评论""")
    }
}
