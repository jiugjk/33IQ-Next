package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

/** Shared quote-then-reveal flow state for both the paid-answer and paid-hint features. */
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

    data object Failed : RevealState<Nothing, Nothing>
}

internal sealed interface SubmissionState {
    data object Idle : SubmissionState

    data object Submitting : SubmissionState

    data class Done(
        val result: SubmitAnswerResult,
    ) : SubmissionState

    data object Failed : SubmissionState
}
