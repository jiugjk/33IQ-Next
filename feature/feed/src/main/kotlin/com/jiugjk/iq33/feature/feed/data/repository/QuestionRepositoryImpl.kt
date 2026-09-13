package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.domain.result.resultOf
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.model.QuestionPage
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository

internal class QuestionRepositoryImpl(
    private val remoteDataSource: QuestionRemoteDataSource,
    private val progressRepository: QuestionProgressRepository,
) : QuestionRepository {
    override suspend fun getQuestionList(
        category: Category,
        nextPageUrl: String?,
    ): Result<QuestionPage> =
        resultOf(TimberLogTags.NETWORK, "Failed to load question list") {
            remoteDataSource.fetchQuestionList(category, nextPageUrl)
        }

    override suspend fun searchQuestions(
        keyword: String,
        page: Int,
    ): Result<List<QuestionSummary>> =
        resultOf(TimberLogTags.NETWORK, "Search failed") {
            remoteDataSource.fetchSearchResults(keyword, page)
        }

    override suspend fun getQuestionDetail(id: Long): Result<QuestionDetail> =
        resultOf(TimberLogTags.NETWORK, "Failed to load question $id") {
            val accountKey = progressRepository.current.accountKey
            val detail = remoteDataSource.fetchQuestionDetail(id)
            val progress = progressRepository.current
            detail.copy(
                isAnswered = accountKey == progress.accountKey && id in progress.answeredIds,
                hasViewedAnswer = accountKey == progress.accountKey && id in progress.viewedAnswerIds,
                isAnswerRevealPending = accountKey == progress.accountKey && id in progress.pendingAnswerRevealIds,
            )
        }
}
