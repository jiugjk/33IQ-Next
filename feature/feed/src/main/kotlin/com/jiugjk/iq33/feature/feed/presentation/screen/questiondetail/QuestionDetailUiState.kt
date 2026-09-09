package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
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
        val hintReveal: RevealState<HintQuote, HintReveal> = RevealState.Idle,
        /** Guards against a double-tap firing two overlapping praise requests. */
        val isPraising: Boolean = false,
        /** Guards against overlapping bookmark writes for the same question. */
        val isBookmarkChanging: Boolean = false,
        /** A local bookmark read/write failed; the question itself is still usable. */
        val bookmarkFailed: Boolean = false,
    ) : QuestionDetailUiState {
        val isSubmitting: Boolean get() = submission is SubmissionState.Submitting

        /**
         * Choices / the open-answer field are frozen while a submission is in flight or finished: a
         * result that comes back for choice A must never be displayed next to a freshly selected
         * choice B.
         */
        val canSelectChoice: Boolean
            get() = submission !is SubmissionState.Submitting && submission !is SubmissionState.Done

        val canStartHintReveal: Boolean
            get() =
                !isSubmitting &&
                    hintReveal !is RevealState.Revealed &&
                    !hintReveal.isBusy &&
                    !hintReveal.isRetryBlocked

        val canConfirmHintReveal: Boolean
            get() = !isSubmitting && hintReveal is RevealState.QuoteReady
    }
}
