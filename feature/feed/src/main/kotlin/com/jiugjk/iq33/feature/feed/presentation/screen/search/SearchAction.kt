package com.jiugjk.iq33.feature.feed.presentation.screen.search

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

/*
 * Every result-bearing action carries the query it was requested for, so a response that arrives
 * after the user typed something else (or cleared the field) is discarded instead of replacing the
 * current results.
 */
internal sealed interface SearchAction : BaseAction<SearchUiState> {
    class QueryChanged(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.copy(query = query, results = if (query.isBlank()) SearchResults.Idle else state.results)
    }

    class SearchStart(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) = state.forQuery(query) { copy(results = SearchResults.Loading) }
    }

    class SearchSuccess(
        private val query: String,
        private val questions: List<QuestionSummary>,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.forQuery(query) {
                copy(results = if (questions.isEmpty()) SearchResults.Empty else SearchResults.Content(questions))
            }
    }

    class SearchFailure(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) = state.forQuery(query) { copy(results = SearchResults.Error) }
    }
}

/** Applies [update] only while [query] is still the query on screen. */
private inline fun SearchUiState.forQuery(
    query: String,
    update: SearchUiState.() -> SearchUiState,
): SearchUiState = if (this.query == query) update() else this
