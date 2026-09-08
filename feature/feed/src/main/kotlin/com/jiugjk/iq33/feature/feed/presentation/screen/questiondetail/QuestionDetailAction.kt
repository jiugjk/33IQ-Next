package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail

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
}
