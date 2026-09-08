package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.domain.result.resultOf
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository

internal class AnswerRepositoryImpl(
    private val remoteDataSource: AnswerRemoteDataSource,
) : AnswerRepository {
    override suspend fun submitAnswer(
        questionId: Long,
        answer: String,
    ): Result<SubmitAnswerResult> =
        resultOf(NETWORK_LOG_TAG, "Failed to submit answer for $questionId") {
            remoteDataSource.submitAnswer(questionId, answer)
        }

    override suspend fun revealAnswer(questionId: Long): Result<AnswerReveal> =
        resultOf(NETWORK_LOG_TAG, "Failed to reveal answer for $questionId") {
            remoteDataSource.revealAnswer(questionId)
        }

    override suspend fun quoteHint(questionId: Long): Result<HintQuote> =
        resultOf(NETWORK_LOG_TAG, "Failed to quote hint price for $questionId") {
            remoteDataSource.quoteHint(questionId)
        }

    override suspend fun revealHint(questionId: Long): Result<HintReveal> =
        resultOf(NETWORK_LOG_TAG, "Failed to reveal hint for $questionId") {
            remoteDataSource.revealHint(questionId)
        }

    override suspend fun praiseQuestion(questionId: Long): Result<Int> =
        resultOf(NETWORK_LOG_TAG, "Failed to praise question $questionId") {
            remoteDataSource.praiseQuestion(questionId)
        }

    private companion object {
        const val NETWORK_LOG_TAG = "Network"
    }
}
