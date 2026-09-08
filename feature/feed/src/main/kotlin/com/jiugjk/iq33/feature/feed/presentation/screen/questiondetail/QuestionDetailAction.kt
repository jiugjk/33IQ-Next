package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

/*
 * Reducers re-check the interaction rules that the UI also enforces through `enabled` flags: two
 * taps in the same frame, or a result arriving for a request the state has since moved past, must
 * not be able to install a state the rules forbid.
 */
internal sealed interface QuestionDetailAction : BaseAction<QuestionDetailUiState> {
    object LoadStart : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState) = QuestionDetailUiState.Loading
    }

    class LoadSuccess(
        private val detail: QuestionDetail,
        private val isBookmarked: Boolean,
        private val bookmarkFailed: Boolean = false,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState) =
            QuestionDetailUiState.Content(detail = detail, isBookmarked = isBookmarked, bookmarkFailed = bookmarkFailed)
    }

    object LoadFailure : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState) = QuestionDetailUiState.Error
    }

    class ChoiceSelected(
        private val choiceId: String,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.canSelectChoice) {
                state.copy(selectedChoiceId = choiceId)
            } else {
                state
            }
    }

    object BookmarkStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && !state.isBookmarkChanging) {
                state.copy(isBookmarkChanging = true, bookmarkFailed = false)
            } else {
                state
            }
    }

    class BookmarkChanged(
        private val isBookmarked: Boolean,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) {
                state.copy(isBookmarked = isBookmarked, isBookmarkChanging = false, bookmarkFailed = false)
            } else {
                state
            }
    }

    object BookmarkFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(isBookmarkChanging = false, bookmarkFailed = true) else state
    }

    class SubmissionStarted(
        private val choiceId: String,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.canSelectChoice) {
                // Freeze the selection at what is actually being sent.
                state.copy(selectedChoiceId = choiceId, submission = SubmissionState.Submitting(choiceId))
            } else {
                state
            }
    }

    class SubmissionFinished(
        private val result: SubmitAnswerResult,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState {
            if (state !is QuestionDetailUiState.Content) return state

            val submitting = state.submission as? SubmissionState.Submitting ?: return state

            return state.copy(submission = SubmissionState.Done(submitting.choiceId, result))
        }
    }

    object SubmissionFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.isSubmitting) state.copy(submission = SubmissionState.Failed) else state
    }

    // Revealing the real answer has no live quote to fetch first (see AnswerRemoteDataSource.revealAnswer's
    // doc) - tapping "查看正确答案" goes straight to a confirmation step with no network call.
    object AnswerConfirmRequested : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.canReveal && !state.isAnswerRevealed) {
                state.copy(answerReveal = RevealState.QuoteReady(Unit))
            } else {
                state
            }
    }

    object AnswerRevealStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.canReveal) state.copy(answerReveal = RevealState.Revealing) else state
    }

    class AnswerRevealFinished(
        private val reveal: AnswerReveal,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.Revealed(reveal)) else state
    }

    object AnswerFlowFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && !state.isAnswerRevealed) state.copy(answerReveal = RevealState.Failed) else state
    }

    object AnswerFlowDismissed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.answerReveal is RevealState.QuoteReady) {
                state.copy(answerReveal = RevealState.Idle)
            } else {
                state
            }
    }

    object HintQuoteStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.canReveal) state.copy(hintReveal = RevealState.QuoteLoading) else state
    }

    class HintQuoteReady(
        private val quote: HintQuote,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.hintReveal is RevealState.QuoteLoading) {
                state.copy(hintReveal = RevealState.QuoteReady(quote))
            } else {
                state
            }
    }

    object HintRevealStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.canReveal) state.copy(hintReveal = RevealState.Revealing) else state
    }

    class HintRevealFinished(
        private val reveal: HintReveal,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.Revealed(reveal)) else state
    }

    object HintFlowFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.hintReveal !is RevealState.Revealed) {
                state.copy(hintReveal = RevealState.Failed)
            } else {
                state
            }
    }

    object HintFlowDismissed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.hintReveal is RevealState.QuoteReady) {
                state.copy(hintReveal = RevealState.Idle)
            } else {
                state
            }
    }

    object PraiseStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && !state.isPraising) state.copy(isPraising = true) else state
    }

    /**
     * `/index/praise` toggles - a second tap un-praises - and only reports the *total* count, which
     * every other user also moves. So this flips the state that was in effect when the request was
     * sent (a praise is only ever sent while `isPraising` is false, so that state is still the one
     * being toggled) and uses the reported number purely as the new displayed total. Inferring the
     * direction from whether the total went up or down mis-reads any concurrent vote by someone else.
     */
    class Praised(
        private val newCount: Int,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.isPraising) {
                state.copy(
                    detail = state.detail.copy(upvoteCount = newCount, isUpvoted = !state.detail.isUpvoted),
                    isPraising = false,
                )
            } else {
                state
            }
    }

    object PraiseFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(isPraising = false) else state
    }
}
