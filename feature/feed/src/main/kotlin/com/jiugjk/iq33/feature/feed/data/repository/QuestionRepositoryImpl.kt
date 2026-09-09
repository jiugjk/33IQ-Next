package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.domain.result.resultOf
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository

internal class QuestionRepositoryImpl(
    private val remoteDataSource: QuestionRemoteDataSource,
) : QuestionRepository {
    override suspend fun getQuestionList(
        category: Category,
        page: Int,
    ): Result<List<QuestionSummary>> =
        resultOf(TimberLogTags.NETWORK, "Failed to load question list") {
            remoteDataSource.fetchQuestionList(category, page)
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
            remoteDataSource.fetchQuestionDetail(id)
        }
}
