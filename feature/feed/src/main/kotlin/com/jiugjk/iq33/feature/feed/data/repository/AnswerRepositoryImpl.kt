package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.domain.result.resultOf
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.datasource.remote.IqResponseException
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
        resultOf(TimberLogTags.NETWORK, "Failed to submit answer for $questionId") {
            remoteDataSource.submitAnswer(questionId, answer)
        }.withSideEffectFlag()

    override suspend fun revealAnswer(questionId: Long): Result<AnswerReveal> =
        resultOf(TimberLogTags.NETWORK, "Failed to reveal answer for $questionId") {
            remoteDataSource.revealAnswer(questionId)
        }.withSideEffectFlag()

    override suspend fun quoteHint(questionId: Long): Result<HintQuote> =
        resultOf(TimberLogTags.NETWORK, "Failed to quote hint price for $questionId") {
            remoteDataSource.quoteHint(questionId)
        }.withSideEffectFlag()

    override suspend fun revealHint(questionId: Long): Result<HintReveal> =
        resultOf(TimberLogTags.NETWORK, "Failed to reveal hint for $questionId") {
            remoteDataSource.revealHint(questionId)
        }.withSideEffectFlag()

    override suspend fun praiseQuestion(questionId: Long): Result<Int> =
        resultOf(TimberLogTags.NETWORK, "Failed to praise question $questionId") {
            remoteDataSource.praiseQuestion(questionId)
        }.withSideEffectFlag()

    private fun <T> Result<T>.withSideEffectFlag(): Result<T> {
        if (this !is Result.Failure) return this

        val afterSideEffect = (throwable as? IqResponseException)?.afterSideEffect == true

        return if (afterSideEffect == this.afterSideEffect) this else Result.Failure(throwable, afterSideEffect)
    }
}
