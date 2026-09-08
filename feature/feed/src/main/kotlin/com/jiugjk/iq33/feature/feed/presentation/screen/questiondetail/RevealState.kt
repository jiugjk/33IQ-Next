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

internal enum class RevealKind {
    ANSWER,
    HINT,
    ;

    /** The action that closes this flow's confirmation step. */
    fun dismissAction(): QuestionDetailAction =
        when (this) {
            ANSWER -> QuestionDetailAction.AnswerFlowDismissed
            HINT -> QuestionDetailAction.HintFlowDismissed
        }
}

/**
 * State of this question's answer submission.
 *
 * [Submitting] and [Done] carry the choice that was actually sent, so the result is always shown
 * against the option it belongs to even if the user taps another one in the meantime.
 */
internal sealed interface SubmissionState {
    data object Idle : SubmissionState

    data class Submitting(
        val choiceId: String,
    ) : SubmissionState

    data class Done(
        val choiceId: String,
        val result: SubmitAnswerResult,
    ) : SubmissionState

    data object Failed : SubmissionState
}
