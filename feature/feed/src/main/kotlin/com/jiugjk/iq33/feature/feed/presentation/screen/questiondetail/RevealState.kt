package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

/**
 * Quote-then-reveal flow state shared by the independent hint and answer-analysis features.
 */
internal sealed interface RevealState<out Quote, out Reveal> {
    data object Idle : RevealState<Nothing, Nothing>

    data object QuoteLoading : RevealState<Nothing, Nothing>

    data class QuoteReady<Quote>(
        val quote: Quote,
    ) : RevealState<Quote, Nothing>

    data object Revealing : RevealState<Nothing, Nothing>

    data class Revealed<Reveal>(
        val reveal: Reveal,
    ) : RevealState<Nothing, Reveal>

    data class Failed(
        val afterSideEffect: Boolean = false,
    ) : RevealState<Nothing, Nothing>
}

internal val RevealState<*, *>.isBusy: Boolean
    get() = this is RevealState.QuoteLoading || this is RevealState.QuoteReady || this is RevealState.Revealing

internal val RevealState<*, *>.isRetryBlocked: Boolean
    get() = this is RevealState.Failed && afterSideEffect

/**
 * State of this question's answer submission.
 *
 * [Submitting] and [Done] carry the choice that was actually sent, so the result is always shown
 * against the option it belongs to even if the user taps another one in the meantime.
 */
internal sealed interface SubmissionState {
    data object Idle : SubmissionState

    data class Submitting(
        val submittedAnswer: String,
    ) : SubmissionState

    data class Done(
        val submittedAnswer: String,
        val result: SubmitAnswerResult,
    ) : SubmissionState

    data object Failed : SubmissionState
}
