package com.jiugjk.iq33.feature.feed.domain.model

/** Result of submitting an answer via 33IQ's real submission endpoint (see `SUBMIT_ANSWER_URL`). */
sealed interface SubmitAnswerResult {
    /** Correct. [scoreDelta] is the 学识 gained, [myScore] the account's new total (both server-reported). */
    data class Correct(
        val scoreDelta: Int,
        val myScore: Int,
    ) : SubmitAnswerResult

    /** Wrong. [scoreDelta] is the 学识 lost (server-reported, zero or negative), [myScore] the new total. */
    data class Wrong(
        val scoreDelta: Int,
        val myScore: Int,
    ) : SubmitAnswerResult

    /** 33IQ rejected the submission because this account already answered this question before. */
    data object AlreadyAnswered : SubmitAnswerResult

    /** 33IQ's own `isLimit` flag came back set - exact trigger unconfirmed, surfaced as a soft failure. */
    data object LimitReached : SubmitAnswerResult
}

data class AnswerReveal(
    val answer: String,
    val explanation: String,
    /** 学识 cost reported by `payforshowanswer` for this reveal - 0 if [alreadyPaid]. */
    val cost: Int,
    val alreadyPaid: Boolean,
)

/**
 * Price quote for a paid hint, from 33IQ's own `showtipsbuy` endpoint - it reports all three of the
 * account's possible prices, letting the UI show the 会员/终身会员 discount even before purchase.
 */
data class HintQuote(
    val normalCost: Int,
    val memberCost: Int,
    val lifeMemberCost: Int,
    /** Which of the three costs actually applies to the current account. */
    val effectiveCost: Int,
)

data class HintReveal(
    val tips: String,
)
