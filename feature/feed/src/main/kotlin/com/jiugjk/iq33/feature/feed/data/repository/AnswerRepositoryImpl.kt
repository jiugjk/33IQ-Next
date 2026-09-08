package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository
import timber.log.Timber

internal class AnswerRepositoryImpl(
    private val remoteDataSource: AnswerRemoteDataSource,
) : AnswerRepository {
    override suspend fun submitAnswer(
        questionId: Long,
        context: String,
    ): Result<SubmitAnswerResult> =
        runCatching { remoteDataSource.submitAnswer(questionId, context) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag(NETWORK_LOG_TAG).w(throwable, "Failed to submit answer for $questionId")
                    Result.Failure(throwable)
                },
            )

    override suspend fun revealAnswer(questionId: Long): Result<AnswerReveal> =
        runCatching { remoteDataSource.revealAnswer(questionId) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag(NETWORK_LOG_TAG).w(throwable, "Failed to reveal answer for $questionId")
                    Result.Failure(throwable)
                },
            )

    override suspend fun quoteHint(questionId: Long): Result<HintQuote> =
        runCatching { remoteDataSource.quoteHint(questionId) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag(NETWORK_LOG_TAG).w(throwable, "Failed to quote hint price for $questionId")
                    Result.Failure(throwable)
                },
            )

    override suspend fun revealHint(questionId: Long): Result<HintReveal> =
        runCatching { remoteDataSource.revealHint(questionId) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag(NETWORK_LOG_TAG).w(throwable, "Failed to reveal hint for $questionId")
                    Result.Failure(throwable)
                },
            )

    override suspend fun praiseQuestion(questionId: Long): Result<Int> =
        runCatching { remoteDataSource.praiseQuestion(questionId) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { throwable ->
                    Timber.tag(NETWORK_LOG_TAG).w(throwable, "Failed to praise question $questionId")
                    Result.Failure(throwable)
                },
            )

    private companion object {
        const val NETWORK_LOG_TAG = "Network"
    }
}
