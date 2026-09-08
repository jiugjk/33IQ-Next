package com.jiugjk.iq33.feature.feed.presentation.screen.search

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

internal sealed interface SearchAction : BaseAction<SearchUiState> {
    object SearchCleared : SearchAction {
        override fun reduce(state: SearchUiState) = SearchUiState.Idle
    }

    object SearchStart : SearchAction {
        override fun reduce(state: SearchUiState) = SearchUiState.Loading
    }

    class SearchSuccess(
        private val questions: List<QuestionSummary>,
    ) : SearchAction {
        override fun reduce(state: SearchUiState): SearchUiState =
            if (questions.isEmpty()) SearchUiState.Empty else SearchUiState.Content(questions)
    }

    object SearchFailure : SearchAction {
        override fun reduce(state: SearchUiState) = SearchUiState.Error
    }
}
