package com.jiugjk.iq33.feature.feed.domain.repository

import com.jiugjk.iq33.feature.feed.domain.model.FeedPosition
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import kotlinx.coroutines.flow.Flow

internal interface QuestionProgressRepository {
    val progress: Flow<QuestionProgress>
    val current: QuestionProgress

    fun recordAnswered(
        questionId: Long,
        accountKey: String?,
    )

    fun recordAnswerViewed(
        questionId: Long,
        accountKey: String?,
    )

    /** Durable before a potentially charged request. Call off the main thread; false means do not send. */
    fun setAnswerRevealPending(
        questionId: Long,
        pending: Boolean,
        accountKey: String?,
    ): Boolean

    fun setHideAnswered(hide: Boolean)

    /** True while a potentially charged reveal for [questionId] has no confirmed outcome yet. */
    fun hasPendingReveal(questionId: Long): Boolean = questionId in current.pendingAnswerRevealIds

    fun feedPosition(categoryId: String): FeedPosition

    fun saveFeedPosition(
        categoryId: String,
        position: FeedPosition,
        accountKey: String?,
    )
}
