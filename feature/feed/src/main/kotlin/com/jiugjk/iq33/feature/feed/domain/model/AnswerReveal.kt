package com.jiugjk.iq33.feature.feed.domain.model

/** A quote for one question. `show` permits fetching without another payment request. */
internal data class AnswerQuote(
    val questionId: Long,
    val cost: Int,
    val requiresPayment: Boolean,
)

internal data class AnswerReveal(
    val answerText: String,
    val explanationText: String,
    val answerBlocks: List<QuestionContentBlock> = emptyList(),
    val explanationBlocks: List<QuestionContentBlock> = emptyList(),
)
