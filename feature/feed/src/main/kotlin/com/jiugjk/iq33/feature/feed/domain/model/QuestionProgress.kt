package com.jiugjk.iq33.feature.feed.domain.model

/** Absence from answeredIds means unknown, not a server-confirmed unanswered question. */
internal data class QuestionProgress(
    val accountKey: String? = null,
    val answeredIds: Set<Long> = emptySet(),
    val hideAnswered: Boolean = false,
    val viewedAnswerIds: Set<Long> = emptySet(),
    val pendingAnswerRevealIds: Set<Long> = emptySet(),
) {
    fun isSubmissionBlocked(id: Long): Boolean = restrictionsFor(id).hasAny

    fun restrictionsFor(id: Long): QuestionRestrictions =
        QuestionRestrictions(
            isAnswered = id in answeredIds,
            hasViewedAnswer = id in viewedAnswerIds,
            isAnswerRevealPending = id in pendingAnswerRevealIds,
        )
}

internal data class FeedPosition(
    val nextPageUrl: String? = null,
    val lastQuestionIds: Set<Long> = emptySet(),
)
