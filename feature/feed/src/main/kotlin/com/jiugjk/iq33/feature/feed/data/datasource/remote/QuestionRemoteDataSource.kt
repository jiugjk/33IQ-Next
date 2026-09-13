package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.CategorySource
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.model.QuestionPage
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/**
 * Fetches question lists, search results and question details.
 *
 * [IqHtmlClient] already moves the network I/O off the caller's thread, but the CPU work that
 * follows - Jsoup selector traversal, JSON parsing, rich-text conversion - would otherwise resume on
 * the caller's (main) thread and stall input and drawing on a large response. Every parse therefore
 * runs on [parsingDispatcher], making these suspend functions main-safe end to end.
 */
internal class QuestionRemoteDataSource(
    private val htmlClient: IqHtmlClient,
    private val htmlParser: QuestionHtmlParser,
    private val jsonParser: QuestionJsonParser,
    private val parsingDispatcher: CoroutineDispatcher,
) {
    suspend fun fetchQuestionList(
        category: Category,
        nextPageUrl: String?,
    ): QuestionPage {
        require(nextPageUrl == null || QuestionListLinks.isSafeListUrl(nextPageUrl)) { "Invalid question list cursor" }
        val url = nextPageUrl ?: buildListUrl(category)
        val document = htmlClient.get(url)

        return withContext(parsingDispatcher) {
            QuestionPage(htmlParser.parseQuestionSummaries(document), QuestionListLinks.nextPage(document))
        }
    }

    suspend fun fetchSearchResults(
        keyword: String,
        page: Int,
    ): List<QuestionSummary> {
        val encodedKeyword = URLEncoder.encode(keyword, IqConstants.PAGE_CHARSET)
        val pageParam = if (page > 1) "&page=$page" else ""
        val document = htmlClient.get("${IqConstants.SEARCH_URL}?k=$encodedKeyword&type=question$pageParam")

        return withContext(parsingDispatcher) { htmlParser.parseQuestionSummaries(document) }
    }

    /**
     * Fetches the app-facing JSON variant of the question detail page (see [QuestionJsonParser]),
     * which carries real choices/stats rather than what HTML scraping alone could offer.
     */
    suspend fun fetchQuestionDetail(id: Long): QuestionDetail {
        // The `?p=3` switch is what makes this address serve JSON; it belongs to the fetch only.
        // The model carries the public page URL instead - see QuestionJsonParser.
        val apiUrl = "${IqConstants.QUESTION_DETAIL_URL}/$id.html?p=${IqConstants.QUESTION_DETAIL_APP_P_PARAM}"
        val rawJson = htmlClient.getText(apiUrl)

        return withContext(parsingDispatcher) {
            jsonParser.parseQuestionDetail(rawJson, id)
                ?: throw IOException("Unexpected question detail response shape for id=$id")
        }
    }

    private fun buildListUrl(category: Category): String {
        val base =
            when (val source = category.source) {
                CategorySource.QuestionList -> {
                    IqConstants.QUESTION_LIST_URL
                }
                is CategorySource.Tag -> {
                    val encodedTag = URLEncoder.encode(source.path, IqConstants.PAGE_CHARSET)
                    "${IqConstants.BASE_URL}/tag/$encodedTag.html"
                }
            }

        return base
    }
}
