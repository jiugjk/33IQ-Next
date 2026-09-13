package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal

internal sealed interface AnswerRevealAction : QuestionDetailAction {
    class QuoteStarted(
        private val id: Long,
    ) : AnswerRevealAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.detail.id == id && state.canStartAnswerReveal) {
                state.copy(answerReveal = RevealState.QuoteLoading)
            } else {
                state
            }
    }

    class QuoteReady(
        private val quote: AnswerQuote,
    ) : AnswerRevealAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.detail.id == quote.questionId &&
                state.answerReveal is RevealState.QuoteLoading
            ) {
                state.copy(answerReveal = RevealState.QuoteReady(quote))
            } else {
                state
            }
    }

    class Started(
        private val id: Long,
        private val recovery: Boolean,
    ) : AnswerRevealAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.detail.id == id &&
                (if (recovery) state.canRecoverAnswerReveal else state.canConfirmAnswerReveal)
            ) {
                state.copy(answerReveal = RevealState.Revealing)
            } else {
                state
            }
    }

    class Finished(
        private val id: Long,
        private val reveal: AnswerReveal,
    ) : AnswerRevealAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.detail.id == id && state.answerReveal is RevealState.Revealing) {
                state.copy(
                    answerReveal = RevealState.Revealed(reveal),
                    detail = state.detail.copy(hasViewedAnswer = true, isAnswerRevealPending = false),
                )
            } else {
                state
            }
    }

    class Failed(
        private val id: Long,
        private val afterSideEffect: Boolean,
    ) : AnswerRevealAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState {
            if (state !is QuestionDetailUiState.Content || state.detail.id != id) return state
            val inFlight = state.answerReveal is RevealState.QuoteLoading || state.answerReveal is RevealState.Revealing
            return if (inFlight) state.copy(answerReveal = RevealState.Failed(afterSideEffect)) else state
        }
    }

    data object Dismissed : AnswerRevealAction {
        override fun reduce(state: QuestionDetailUiState): QuestionDetailUiState =
            if (state is QuestionDetailUiState.Content && state.answerReveal is RevealState.QuoteReady) {
                state.copy(answerReveal = RevealState.Idle)
            } else {
                state
            }
    }
}
