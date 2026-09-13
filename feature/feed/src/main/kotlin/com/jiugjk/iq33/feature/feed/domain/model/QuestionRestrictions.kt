package com.jiugjk.iq33.feature.feed.domain.model

/**
 * Locally confirmed answer restrictions for one question.
 *
 * Every flag defaults to false, which means **unknown** rather than "confirmed still answerable":
 * this client only records what it saw happen on this device for the active account.
 */
data class QuestionRestrictions(
    val isAnswered: Boolean = false,
    val hasViewedAnswer: Boolean = false,
    val isAnswerRevealPending: Boolean = false,
) {
    val hasAny: Boolean get() = isAnswered || hasViewedAnswer || isAnswerRevealPending
}
