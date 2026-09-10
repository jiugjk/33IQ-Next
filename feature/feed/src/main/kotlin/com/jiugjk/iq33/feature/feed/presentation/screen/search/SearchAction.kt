package com.jiugjk.iq33.feature.feed.presentation.screen.search

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.presentation.paging.mergeUniqueById

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
            state.copy(
                query = query,
                results = if (query.isBlank()) SearchResults.Idle else state.results,
                page = if (query.isBlank()) 1 else state.page,
                isLoadingMore = false,
                canLoadMore = false,
                loadMoreFailed = false,
            )
    }

    class SearchStart(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.forQuery(query) {
                copy(
                    results = SearchResults.Loading,
                    page = 1,
                    isLoadingMore = false,
                    canLoadMore = false,
                    loadMoreFailed = false,
                )
            }
    }

    class SearchSuccess(
        private val query: String,
        private val questions: List<QuestionSummary>,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.forQuery(query) {
                copy(
                    results = if (questions.isEmpty()) SearchResults.Empty else SearchResults.Content(questions),
                    page = 1,
                    isLoadingMore = false,
                    canLoadMore = questions.isNotEmpty(),
                    loadMoreFailed = false,
                )
            }
    }

    class SearchFailure(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.forQuery(query) {
                copy(results = SearchResults.Error, isLoadingMore = false, canLoadMore = false, loadMoreFailed = false)
            }
    }

    class LoadMoreStart(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.forQuery(query) {
                if (results is SearchResults.Content) copy(isLoadingMore = true, loadMoreFailed = false) else this
            }
    }

    class LoadMoreSuccess(
        private val query: String,
        private val page: Int,
        private val newQuestions: List<QuestionSummary>,
    ) : SearchAction {
        override fun reduce(state: SearchUiState): SearchUiState {
            // Anything but the page that was actually asked for - a stale query, a state that is no
            // longer showing results, an out-of-order page - is dropped rather than appended.
            val content = state.results as? SearchResults.Content
            if (state.query != query || content == null || state.page != page - 1) return state

            val (merged, hasNew) = mergeUniqueById(content.questions, newQuestions) { it.id }

            return state.copy(
                results = SearchResults.Content(merged),
                page = page,
                isLoadingMore = false,
                loadMoreFailed = false,
                canLoadMore = hasNew,
            )
        }
    }

    class LoadMoreFailure(
        private val query: String,
    ) : SearchAction {
        override fun reduce(state: SearchUiState) =
            state.forQuery(query) {
                copy(isLoadingMore = false, loadMoreFailed = true)
            }
    }
}

/** Applies [update] only while [query] is still the query on screen. */
private inline fun SearchUiState.forQuery(
    query: String,
    update: SearchUiState.() -> SearchUiState,
): SearchUiState = if (this.query == query) update() else this
