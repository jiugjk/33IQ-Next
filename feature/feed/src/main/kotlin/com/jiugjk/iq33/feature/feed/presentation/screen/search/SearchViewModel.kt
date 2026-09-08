package com.jiugjk.iq33.feature.feed.presentation.screen.search

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.feed.domain.usecase.SearchQuestionsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class SearchViewModel(
    private val searchQuestionsUseCase: SearchQuestionsUseCase,
) : BaseViewModel<SearchUiState, SearchAction>(SearchUiState()) {
    private var searchJob: Job? = null

    /**
     * Records what the user typed and, after a short pause, searches for it.
     *
     * Debouncing lives here rather than in the text field so that only a real change to the query
     * starts a search: re-entering composition with the same query no longer re-submits it.
     */
    fun onQueryChange(query: String) {
        if (query == uiStateFlow.value.query) return

        searchJob?.cancel()
        sendAction(SearchAction.QueryChanged(query))

        if (query.isBlank()) return

        searchJob =
            viewModelScope.launch {
                delay(QUERY_DEBOUNCE_MILLIS)

                sendAction(SearchAction.SearchStart(query))

                when (val result = searchQuestionsUseCase(query, PAGE_FIRST)) {
                    is Result.Success -> sendAction(SearchAction.SearchSuccess(query, result.value))
                    is Result.Failure -> sendAction(SearchAction.SearchFailure(query))
                }
            }
    }

    private companion object {
        const val PAGE_FIRST = 1
        const val QUERY_DEBOUNCE_MILLIS = 300L
    }
}
