package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

internal sealed interface QuestionDetailAction : BaseAction<QuestionDetailUiState> {
    object LoadStart : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState) = QuestionDetailUiState.Loading
    }

    class LoadSuccess(
        private val detail: QuestionDetail,
        private val isBookmarked: Boolean,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState) = QuestionDetailUiState.Content(detail, isBookmarked)
    }

    object LoadFailure : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState) = QuestionDetailUiState.Error
    }

    class ChoiceSelected(
        private val choiceId: String,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(selectedChoiceId = choiceId) else state
    }

    class BookmarkChanged(
        private val isBookmarked: Boolean,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(isBookmarked = isBookmarked) else state
    }

    object SubmissionStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(submission = SubmissionState.Submitting) else state
    }

    class SubmissionFinished(
        private val result: SubmitAnswerResult,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(submission = SubmissionState.Done(result)) else state
    }

    object SubmissionFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(submission = SubmissionState.Failed) else state
    }

    // Revealing the real answer has no live quote to fetch first (see AnswerRemoteDataSource.revealAnswer's
    // doc) - tapping "查看正确答案" goes straight to a confirmation step with no network call.
    object AnswerConfirmRequested : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.QuoteReady(Unit)) else state
    }

    object AnswerRevealStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.Revealing) else state
    }

    class AnswerRevealFinished(
        private val reveal: AnswerReveal,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.Revealed(reveal)) else state
    }

    object AnswerFlowFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.Failed) else state
    }

    object AnswerFlowDismissed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.answerReveal !is RevealState.Revealed) {
                state.copy(answerReveal = RevealState.Idle)
            } else {
                state
            }
    }

    object HintQuoteStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.QuoteLoading) else state
    }

    class HintQuoteReady(
        private val quote: HintQuote,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.QuoteReady(quote)) else state
    }

    object HintRevealStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.Revealing) else state
    }

    class HintRevealFinished(
        private val reveal: HintReveal,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.Revealed(reveal)) else state
    }

    object HintFlowFailed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.Failed) else state
    }

    object HintFlowDismissed : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(hintReveal = RevealState.Idle) else state
    }

    object PraiseStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(isPraising = true) else state
    }

    // /index/praise toggles - a second tap un-praises. It only reports the new total count, not the
    // direction, so this compares against the previous count to infer whether we just liked or
    // unliked (rather than assuming "praised" unconditionally, which left the icon stuck lit after
    // an un-praise).
    class Praised(
        private val newCount: Int,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) {
                val isUpvoted =
                    when {
                        newCount > state.detail.upvoteCount -> true
                        newCount < state.detail.upvoteCount -> false
                        else -> state.detail.isUpvoted
                    }
                state.copy(
                    detail = state.detail.copy(upvoteCount = newCount, isUpvoted = isUpvoted),
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
