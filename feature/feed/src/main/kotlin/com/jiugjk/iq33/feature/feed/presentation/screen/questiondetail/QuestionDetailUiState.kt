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
        val submission: SubmissionState = SubmissionState.Idle,
        // No live quote exists for revealing the real answer (see AnswerRemoteDataSource.revealAnswer) -
        // the confirm step has no data of its own, hence Unit.
        val answerReveal: RevealState<Unit, AnswerReveal> = RevealState.Idle,
        val hintReveal: RevealState<HintQuote, HintReveal> = RevealState.Idle,
        /** Guards against a double-tap firing two overlapping praise requests. */
        val isPraising: Boolean = false,
    ) : QuestionDetailUiState {
        /** Once the real answer has been revealed, 33IQ no longer accepts a scored submission for it. */
        val canSubmit: Boolean get() = answerReveal !is RevealState.Revealed
    }
}
