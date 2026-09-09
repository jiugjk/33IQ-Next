package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail

@Immutable
internal sealed interface QuestionDetailUiState : BaseState {
    @Immutable
    data object Loading : QuestionDetailUiState

    @Immutable
    data object Error : QuestionDetailUiState

    @Immutable
    data class Content(
        val detail: QuestionDetail,
        val isBookmarked: Boolean,
        val selectedChoiceId: String? = null,
        val draftAnswer: String = "",
        val submission: SubmissionState = SubmissionState.Idle,
        // No live quote exists for revealing the real answer (see AnswerRemoteDataSource.revealAnswer) -
        // the confirm step has no data of its own, hence Unit.
        val answerReveal: RevealState<Unit, AnswerReveal> = RevealState.Idle,
        val hintReveal: RevealState<HintQuote, HintReveal> = RevealState.Idle,
        /** Guards against a double-tap firing two overlapping praise requests. */
        val isPraising: Boolean = false,
        /** Guards against overlapping bookmark writes for the same question. */
        val isBookmarkChanging: Boolean = false,
        /** A local bookmark read/write failed; the question itself is still usable. */
        val bookmarkFailed: Boolean = false,
    ) : QuestionDetailUiState {
        val isAnswerRevealed: Boolean get() = answerReveal is RevealState.Revealed

        val isSubmitting: Boolean get() = submission is SubmissionState.Submitting

        /**
         * 33IQ no longer accepts a scored submission once the real answer has been revealed, and a
         * reveal that is already under way (confirm dialog open, request in flight) is treated the
         * same - otherwise a submission and a reveal could both land on the same question.
         */
        val canSubmit: Boolean
            get() =
                answerReveal is RevealState.Idle ||
                    (answerReveal is RevealState.Failed && !answerReveal.afterSideEffect)

        /**
         * Choices / the open-answer field are frozen while a submission is in flight or finished: a
         * result that comes back for choice A must never be displayed next to a freshly selected
         * choice B.
         */
        val canSelectChoice: Boolean
            get() = canSubmit && submission !is SubmissionState.Submitting && submission !is SubmissionState.Done

        val canStartAnswerReveal: Boolean
            get() =
                !isSubmitting &&
                    !isAnswerRevealed &&
                    !answerReveal.isBusy &&
                    !hintReveal.isBusy &&
                    !answerReveal.isRetryBlocked

        val canStartHintReveal: Boolean
            get() =
                !isSubmitting &&
                    hintReveal !is RevealState.Revealed &&
                    !answerReveal.isBusy &&
                    !hintReveal.isBusy &&
                    !hintReveal.isRetryBlocked

        val canConfirmAnswerReveal: Boolean
            get() = !isSubmitting && answerReveal is RevealState.QuoteReady && !hintReveal.isBusy

        val canConfirmHintReveal: Boolean
            get() = !isSubmitting && hintReveal is RevealState.QuoteReady && !answerReveal.isBusy
    }
}
