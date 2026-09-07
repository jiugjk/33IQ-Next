package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.Comment
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the server-rendered HTML pages of https://www.33iq.com (there is no public JSON API).
 *
 * Selectors below were derived by inspecting real responses for the question list/search/tag and
 * question detail pages. Anything gated behind login (full answer analysis, the real comment feed on
 * most questions) degrades gracefully to `null`/empty rather than throwing, since 33IQ itself hides
 * this content from guests.
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

        val statsText = element.select(".info span").firstOrNull()?.text().orEmpty()
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

    fun parseQuestionDetail(
        document: Document,
        id: Long,
        sourceUrl: String,
    ): QuestionDetail {
        val breadcrumb =
            document
                .select(".navigation [itemprop=name]")
                .map { it.text() }
                .filter { it.isNotBlank() && it != "首页" }

        val title = document.selectFirst(".q-title")?.text().orEmpty()

        val tags = document.select(".timu .badge-info a").map { it.text() }

        val authorBlock = document.selectFirst(".author")
        val author = authorBlock?.selectFirst("[itemprop=name]")?.text()
        val publishedDate =
            authorBlock
                ?.text()
                ?.let { text -> Regex("""\d{4}-\d{2}-\d{2}""").find(text)?.value }

        val statsText = document.selectFirst(".timu .text-muted.font14")?.text().orEmpty()
        val upvoteCount = extractCount(statsText, "点赞")
        val commentCount = extractCount(statsText, "评论")

        val choices =
            document.select("a.btn-chooseans").map { choiceElement ->
                Choice(id = choiceElement.attr("chooseid"), text = choiceElement.text())
            }

        val questionType = if (choices.isNotEmpty()) QuestionType.CHOICE else QuestionType.OPEN

        val analysis =
            ANALYSIS_SELECTORS
                .firstNotNullOfOrNull { selector -> document.selectFirst(selector) }
                ?.text()
                ?.takeIf { it.isNotBlank() }

        val comments =
            document.select(COMMENT_ITEM_SELECTOR).mapNotNull { commentElement ->
                val content = commentElement.selectFirst(".comment-content, .content")?.text()
                if (content.isNullOrBlank()) {
                    null
                } else {
                    Comment(
                        author = commentElement.selectFirst(".comment-author, .author")?.text().orEmpty(),
                        content = content,
                        time = commentElement.selectFirst(".comment-time, .time")?.text().orEmpty(),
                    )
                }
            }

        return QuestionDetail(
            id = id,
            title = title,
            tags = tags,
            breadcrumb = breadcrumb,
            author = author,
            publishedDate = publishedDate,
            upvoteCount = upvoteCount,
            commentCount = commentCount,
            questionType = questionType,
            choices = choices,
            analysis = analysis,
            comments = comments,
            sourceUrl = sourceUrl,
        )
    }

    private fun questionIdFromHref(href: String): Long? =
        Regex("""/question/(\d+)\.html""").find(href)?.groupValues?.get(1)?.toLongOrNull()

    private fun extractCount(
        text: String,
        label: String,
    ): Int = Regex("""(\d+)\s*$label""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private companion object {
        // Best-effort: 33IQ hides the answer analysis behind login/paid "学识" for most questions, and
        // the exact markup for logged-in users hasn't been confirmed against a real account.
        val ANALYSIS_SELECTORS = listOf(".answer-content", ".jiexi", ".dajianxi", "#analysis", ".analysis")
        const val COMMENT_ITEM_SELECTOR = "#comment_list .comment-item, .comment-list .item"
    }
}
