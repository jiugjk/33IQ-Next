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
        /**
         * Correct option remembered from this account's own history, independent of the reveal
         * state: marking the right choice green must not depend on holding the analysis text.
         */
        val knownCorrectOption: String? = null,
        /**
         * Word-bank answer restored from history that could not be mapped back onto the current
         * tiles (the server may return a different candidate array). Shown read-only instead of
         * leaving the field blank under a "submitted" state.
         */
        val answerEcho: String = "",
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

        /** What the word-bank field shows: the live selection, or the read-only history echo. */
        val wordBankDisplayAnswer: String
            get() = wordBankAnswer.ifEmpty { answerEcho }

        /** Correct option to highlight: freshly revealed content first, then this account's history. */
        val revealedCorrectOption: String?
            get() =
                (answerReveal as? RevealState.Revealed)
                    ?.reveal
                    ?.answerText
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: knownCorrectOption?.trim()?.takeIf { it.isNotEmpty() }

        val canStartAnswerReveal: Boolean
            get() =
                !isSubmitting && !hintReveal.isBusy && !answerReveal.isBusy &&
                    answerReveal !is RevealState.Revealed && answerReveal !is RevealState.Entitled &&
                    !answerReveal.isRetryBlocked && !detail.isAnswerRevealPending

        val canConfirmAnswerReveal: Boolean
            get() = !isSubmitting && !hintReveal.isBusy && answerReveal is RevealState.QuoteReady && !detail.isAnswerRevealPending

        /**
         * A fetch-only re-read is available: either a request that may already have been paid for,
         * or content this account revealed earlier whose text this device does not hold. Neither
         * path goes through the quote/pay endpoints.
         */
        val canRecoverAnswerReveal: Boolean
            get() =
                !isSubmitting && !hintReveal.isBusy && !answerReveal.isBusy && answerReveal !is RevealState.Revealed &&
                    (detail.isAnswerRevealPending || answerReveal.isRetryBlocked || answerReveal is RevealState.Entitled)

        /** True when the analysis was revealed before but its text is not cached on this device. */
        val isAnswerRevealEntitled: Boolean get() = answerReveal is RevealState.Entitled

        /**
         * Same for the hint - but `showtips` charges on every call, so there is no free re-read: the
         * entry point stays open and the UI says plainly that this costs 学识 again.
         */
        val isHintRevealEntitled: Boolean get() = hintReveal is RevealState.Entitled

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
