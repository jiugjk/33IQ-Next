package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
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

    object AnswerQuoteStarted : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.QuoteLoading) else state
    }

    class AnswerQuoteReady(
        private val quote: AnswerQuote,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) state.copy(answerReveal = RevealState.QuoteReady(quote)) else state
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

    class Praised(
        private val newCount: Int,
    ) : QuestionDetailAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content) {
                state.copy(detail = state.detail.copy(upvoteCount = newCount, isUpvoted = true))
            } else {
                state
            }
    }
}
