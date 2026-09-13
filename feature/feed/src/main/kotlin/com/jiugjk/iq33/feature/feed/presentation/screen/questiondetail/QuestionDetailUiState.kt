package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType

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
        /** Tile identity is the server-array index, not its text: duplicate characters are distinct tiles. */
        val selectedCandidateIndices: List<Int> = emptyList(),
        val submission: SubmissionState = SubmissionState.Idle,
        val hintReveal: RevealState<HintQuote, HintReveal> = RevealState.Idle,
        val answerReveal: RevealState<AnswerQuote, AnswerReveal> = RevealState.Idle,
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
            get() =
                !detail.isSubmissionBlocked && !answerReveal.isBusy && !answerReveal.isRetryBlocked &&
                    !hintReveal.isBusy && submission !is SubmissionState.Submitting && submission !is SubmissionState.Done

        val isWordBankReady: Boolean
            get() =
                detail.questionType == QuestionType.WORD_BANK &&
                    (detail.answerLength ?: 0) > 0 &&
                    detail.answerCandidates.isNotEmpty() &&
                    detail.answerCandidates.all { it.isNotBlank() }

        val wordBankAnswer: String
            get() = selectedCandidateIndices.mapNotNull { detail.answerCandidates.getOrNull(it) }.joinToString("")

        val canStartAnswerReveal: Boolean
            get() =
                !isSubmitting && !hintReveal.isBusy && !answerReveal.isBusy &&
                    answerReveal !is RevealState.Revealed && !answerReveal.isRetryBlocked && !detail.isAnswerRevealPending

        val canConfirmAnswerReveal: Boolean
            get() = !isSubmitting && !hintReveal.isBusy && answerReveal is RevealState.QuoteReady && !detail.isAnswerRevealPending

        val canRecoverAnswerReveal: Boolean
            get() =
                !isSubmitting && !hintReveal.isBusy && !answerReveal.isBusy && answerReveal !is RevealState.Revealed &&
                    (detail.isAnswerRevealPending || answerReveal.isRetryBlocked)

        val canStartHintReveal: Boolean
            get() =
                !isSubmitting && !answerReveal.isBusy && !answerReveal.isRetryBlocked && !detail.isAnswerRevealPending &&
                    hintReveal !is RevealState.Revealed &&
                    !hintReveal.isBusy &&
                    !hintReveal.isRetryBlocked

        val canConfirmHintReveal: Boolean
            get() = !isSubmitting && hintReveal is RevealState.QuoteReady

        fun canSubmitAnswer(answer: String): Boolean {
            if (!canSelectChoice || answer.isBlank()) return false
            if (detail.questionType != QuestionType.WORD_BANK) return true
            return isWordBankReady && answer == wordBankAnswer &&
                selectedCandidateIndices.distinct().size == selectedCandidateIndices.size &&
                selectedCandidateIndices.all { it in detail.answerCandidates.indices } &&
                answer.codePointCount(0, answer.length) == detail.answerLength
        }
    }
}
