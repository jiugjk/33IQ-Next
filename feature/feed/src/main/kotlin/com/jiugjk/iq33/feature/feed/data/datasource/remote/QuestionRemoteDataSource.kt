package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.library.network.IqConstants
import com.jiugjk.iq33.library.network.IqHtmlClient
import java.io.IOException
import java.net.URLEncoder

internal class QuestionRemoteDataSource(
    private val htmlClient: IqHtmlClient,
    private val htmlParser: QuestionHtmlParser,
    private val jsonParser: QuestionJsonParser,
) {
    suspend fun fetchQuestionList(
        category: Category,
        page: Int,
    ): List<QuestionSummary> {
        val document = htmlClient.get(buildListUrl(category, page))

        return htmlParser.parseQuestionSummaries(document)
    }

    suspend fun fetchSearchResults(
        keyword: String,
        page: Int,
    ): List<QuestionSummary> {
        val encodedKeyword = URLEncoder.encode(keyword, IqConstants.PAGE_CHARSET)
        val pageParam = if (page > 1) "&page=$page" else ""
        val document = htmlClient.get("${IqConstants.SEARCH_URL}?k=$encodedKeyword&type=question$pageParam")

        return htmlParser.parseQuestionSummaries(document)
    }

    /**
     * Fetches the app-facing JSON variant of the question detail page (see [QuestionJsonParser]),
     * which carries real choices/stats rather than what HTML scraping alone could offer.
     */
    suspend fun fetchQuestionDetail(id: Long): QuestionDetail {
        val url = "${IqConstants.QUESTION_DETAIL_URL}/$id.html?p=${IqConstants.QUESTION_DETAIL_APP_P_PARAM}"
        val rawJson = htmlClient.getText(url)

        return jsonParser.parseQuestionDetail(rawJson, id, url)
            ?: throw IOException("Unexpected question detail response shape for id=$id")
    }

    // Pagination beyond page 1 is a best-effort `?page=N` guess: the real parameter name used by
    // 33IQ's own pagination hasn't been confirmed. If the site ignores it, callers will simply see
    // the same first-page results again and the UI stops requesting further pages (see
    // QuestionRepositoryImpl / feed list view model paging logic).
    private fun buildListUrl(
        category: Category,
        page: Int,
    ): String {
        val base =
            if (category.tagName.isEmpty()) {
                IqConstants.QUESTION_LIST_URL
            } else {
                val encodedTag = URLEncoder.encode(category.tagName, IqConstants.PAGE_CHARSET)
                "${IqConstants.BASE_URL}/tag/$encodedTag.html"
            }

        return if (page > 1) "$base?page=$page" else base
    }
}
