package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionRepository
import timber.log.Timber

internal class QuestionRepositoryImpl(
    private val remoteDataSource: QuestionRemoteDataSource,
) : QuestionRepository {
    override suspend fun getQuestionList(
        category: Category,
        page: Int,
    ): Result<List<QuestionSummary>> =
        runCatching { remoteDataSource.fetchQuestionList(category, page) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag("Network").w(throwable, "Failed to load question list")
                    Result.Failure(throwable)
                },
            )

    override suspend fun searchQuestions(
        keyword: String,
        page: Int,
    ): Result<List<QuestionSummary>> =
        runCatching { remoteDataSource.fetchSearchResults(keyword, page) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag("Network").w(throwable, "Search failed")
                    Result.Failure(throwable)
                },
            )

    override suspend fun getQuestionDetail(id: Long): Result<QuestionDetail> =
        runCatching { remoteDataSource.fetchQuestionDetail(id) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag("Network").w(throwable, "Failed to load question $id")
                    Result.Failure(throwable)
                },
            )
}
