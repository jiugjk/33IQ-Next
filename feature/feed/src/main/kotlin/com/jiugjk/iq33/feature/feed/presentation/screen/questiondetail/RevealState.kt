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

    /**
     * This account already obtained (and possibly paid for) the content, but its text is not on this
     * device - a record written before the local cache existed, or one whose text was never stored.
     *
     * Deliberately distinct from [Revealed]: "entitled" is not "loaded". Showing an empty [Revealed]
     * is what used to leave the screen with no content *and* no way to get it back.
     */
    data object Entitled : RevealState<Nothing, Nothing>

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
        /**
         * Identifies *this* submission's completion, once.
         *
         * Non-null only for an answer submitted in this session; a result restored from history
         * carries null. Feedback (haptics, animation, the 学识 float) is keyed on this token, so
         * re-entering an answered question - or recomposing with a surviving view model - cannot
         * replay a reward the user already received.
         */
        val completionToken: Long? = null,
    ) : SubmissionState

    data object Failed : SubmissionState
}
