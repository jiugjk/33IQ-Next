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
        title: String,
    ): Result<SubmitAnswerResult> =
        resultOf(TimberLogTags.NETWORK, "Failed to submit answer for $questionId") {
            // Owner *and* session generation are captured before the request. Every side effect below
            // re-checks them, so a reply that lands after the user switched accounts cannot move the
            // new account's 学识, streak or change log - the record write already refused it.
            val accountKey = progressRepository.current.accountKey
            val epoch = sessionManager.sessionEpoch
            remoteDataSource.submitAnswer(questionId, answer).also { result ->
                when (result) {
                    is SubmitAnswerResult.Correct -> {
                        answerRecords.recordAnswer(
                            accountKey = accountKey,
                            questionId = questionId,
                            title = title,
                            selectedOption = answer,
                            isCorrect = true,
                            knowledgeDelta = result.scoreDelta,
                        )
                        if (isStillCurrent(accountKey, epoch)) feedbackPreferences.recordCorrect(accountKey)
                        applyKnowledgeScore(questionId, result.myScore, result.scoreDelta, accountKey, epoch, title)
                    }
                    is SubmitAnswerResult.Wrong -> {
                        answerRecords.recordAnswer(
                            accountKey = accountKey,
                            questionId = questionId,
                            title = title,
                            selectedOption = answer,
                            isCorrect = false,
                            knowledgeDelta = result.scoreDelta,
                        )
                        if (isStillCurrent(accountKey, epoch)) feedbackPreferences.recordWrong(accountKey)
                        applyKnowledgeScore(questionId, result.myScore, result.scoreDelta, accountKey, epoch, title)
                    }
                    SubmitAnswerResult.AlreadyAnswered -> {
                        answerRecords.recordAnswer(
                            accountKey = accountKey,
                            questionId = questionId,
                            title = title,
                            selectedOption = answer,
                            isCorrect = null,
                        )
                    }
                    SubmitAnswerResult.AnswerAlreadyViewed -> {
                        answerRecords.recordExplanationViewed(accountKey, questionId, title = title)
                    }
                    SubmitAnswerResult.LimitReached -> { }
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
            remoteDataSource.revealHint(questionId).also { reveal ->
                // `showtips` is the call that spends 学识, so the text is cached locally: re-opening
                // the question must never need a second charged request to show it again.
                answerRecords.recordHintViewed(accountKey, questionId, hintText = reveal.tips)
            }
        }.withSideEffectFlag()

    override suspend fun praiseQuestion(questionId: Long): Result<Int> =
        resultOf(TimberLogTags.NETWORK, "Failed to praise question $questionId") {
            remoteDataSource.praiseQuestion(questionId)
        }.withSideEffectFlag()

    @Suppress("LongParameterList")
    private fun applyKnowledgeScore(
        questionId: Long,
        myScore: Int?,
        scoreDelta: Int?,
        accountKey: String?,
        epoch: Int,
        title: String,
    ) {
        if (scoreDelta != null && isStillCurrent(accountKey, epoch)) {
            knowledgeChangeLog.append(accountKey = accountKey, questionId = questionId, delta = scoreDelta, title = title)
        }
        when {
            // The session manager re-validates owner + generation inside the lock that commits the
            // score, so the check is not a check-then-act race.
            myScore != null -> sessionManager.applyServerScore(myScore, accountKey, epoch)
            scoreDelta != null -> sessionManager.applyOptimisticDelta(scoreDelta, accountKey, epoch)
        }
    }

    /** Cheap pre-check for the side effects that are not themselves guarded by the session lock. */
    private fun isStillCurrent(
        accountKey: String?,
        epoch: Int,
    ): Boolean =
        accountKey != null &&
            epoch == sessionManager.sessionEpoch &&
            accountKey == sessionManager.sessionFlow.value.accountKey

    private fun <T> Result<T>.withSideEffectFlag(): Result<T> {
        if (this !is Result.Failure) return this

        val afterSideEffect = (throwable as? IqResponseException)?.afterSideEffect == true

        return if (afterSideEffect == this.afterSideEffect) this else Result.Failure(throwable, afterSideEffect)
    }
}
