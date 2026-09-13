package com.jiugjk.iq33.feature.feed.data.repository

import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.domain.result.resultOf
import com.jiugjk.iq33.feature.base.util.TimberLogTags
import com.jiugjk.iq33.feature.feed.data.datasource.remote.AnswerRemoteDataSource
import com.jiugjk.iq33.feature.feed.data.datasource.remote.IqResponseException
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerFeedbackPreferences
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRepository
import com.jiugjk.iq33.feature.feed.domain.repository.QuestionProgressRepository
import com.jiugjk.iq33.library.network.KnowledgeChangeLog
import com.jiugjk.iq33.library.network.SessionManager

internal class AnswerRepositoryImpl(
    private val remoteDataSource: AnswerRemoteDataSource,
    private val progressRepository: QuestionProgressRepository,
    private val answerRecords: AnswerRecordRepository,
    private val feedbackPreferences: AnswerFeedbackPreferences,
    private val sessionManager: SessionManager,
    private val knowledgeChangeLog: KnowledgeChangeLog,
) : AnswerRepository {
    override suspend fun submitAnswer(
        questionId: Long,
        answer: String,
    ): Result<SubmitAnswerResult> =
        resultOf(TimberLogTags.NETWORK, "Failed to submit answer for $questionId") {
            val accountKey = progressRepository.current.accountKey
            remoteDataSource.submitAnswer(questionId, answer).also { result ->
                when (result) {
                    is SubmitAnswerResult.Correct -> {
                        answerRecords.recordAnswer(
                            accountKey = accountKey,
                            questionId = questionId,
                            selectedOption = answer,
                            isCorrect = true,
                            knowledgeDelta = result.scoreDelta,
                        )
                        feedbackPreferences.recordCorrect()
                        applyKnowledgeScore(questionId, result.myScore, result.scoreDelta)
                    }
                    is SubmitAnswerResult.Wrong -> {
                        answerRecords.recordAnswer(
                            accountKey = accountKey,
                            questionId = questionId,
                            selectedOption = answer,
                            isCorrect = false,
                            knowledgeDelta = result.scoreDelta,
                        )
                        feedbackPreferences.recordWrong()
                        applyKnowledgeScore(questionId, result.myScore, result.scoreDelta)
                    }
                    SubmitAnswerResult.AlreadyAnswered ->
                        answerRecords.recordAnswer(
                            accountKey = accountKey,
                            questionId = questionId,
                            selectedOption = answer,
                            isCorrect = null,
                        )
                    SubmitAnswerResult.AnswerAlreadyViewed ->
                        answerRecords.recordExplanationViewed(accountKey, questionId)
                    SubmitAnswerResult.LimitReached -> Unit
                }
            }
        }.withSideEffectFlag()

    override suspend fun quoteHint(questionId: Long): Result<HintQuote> =
        resultOf(TimberLogTags.NETWORK, "Failed to quote hint price for $questionId") {
            remoteDataSource.quoteHint(questionId)
        }.withSideEffectFlag()

    override suspend fun revealHint(questionId: Long): Result<HintReveal> =
        resultOf(TimberLogTags.NETWORK, "Failed to reveal hint for $questionId") {
            val accountKey = progressRepository.current.accountKey
            remoteDataSource.revealHint(questionId).also {
                answerRecords.recordHintViewed(accountKey, questionId)
            }
        }.withSideEffectFlag()

    override suspend fun praiseQuestion(questionId: Long): Result<Int> =
        resultOf(TimberLogTags.NETWORK, "Failed to praise question $questionId") {
            remoteDataSource.praiseQuestion(questionId)
        }.withSideEffectFlag()


    private fun applyKnowledgeScore(
        questionId: Long,
        myScore: Int?,
        scoreDelta: Int?,
    ) {
        if (scoreDelta != null) {
            knowledgeChangeLog.append(questionId = questionId, delta = scoreDelta)
        }
        when {
            myScore != null -> sessionManager.applyServerScore(myScore)
            scoreDelta != null -> sessionManager.applyOptimisticDelta(scoreDelta)
        }
    }

    private fun <T> Result<T>.withSideEffectFlag(): Result<T> {
        if (this !is Result.Failure) return this

        val afterSideEffect = (throwable as? IqResponseException)?.afterSideEffect == true

        return if (afterSideEffect == this.afterSideEffect) this else Result.Failure(throwable, afterSideEffect)
    }
}
