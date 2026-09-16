package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import kotlinx.coroutines.flow.Flow

internal interface QuestionProgressRepository {
    val progress: Flow<QuestionProgress>
    val current: QuestionProgress

    /** Durable before a potentially charged request. Call off the main thread; false means do not send. */
    fun setAnswerRevealPending(
        questionId: Long,
        pending: Boolean,
        accountKey: String?,
    ): Boolean

    /** Durable before a potentially charged hint request. Call off the main thread; false means do not send. */
    fun setHintRevealPending(
        questionId: Long,
        pending: Boolean,
        accountKey: String?,
    ): Boolean

    fun setHideAnswered(hide: Boolean)
}

/** True while a potentially charged reveal for [questionId] has no confirmed outcome yet. */
internal fun QuestionProgressRepository.hasPendingReveal(questionId: Long): Boolean =
    questionId in current.pendingAnswerRevealIds

/** True while a potentially charged hint reveal for [questionId] has no confirmed outcome yet. */
internal fun QuestionProgressRepository.hasPendingHintReveal(questionId: Long): Boolean =
    questionId in current.pendingHintRevealIds
