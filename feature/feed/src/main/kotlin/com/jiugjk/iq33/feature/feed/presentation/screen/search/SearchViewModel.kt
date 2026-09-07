package com.jiugjk.iq33.feature.feed.presentation.screen.search

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.feed.domain.usecase.SearchQuestionsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SearchViewModel(
    private val searchQuestionsUseCase: SearchQuestionsUseCase,
) : BaseViewModel<SearchUiState, SearchAction>(SearchUiState.Idle) {
    private var job: Job? = null

    fun onQueryChange(query: String) {
        job?.cancel()

        if (query.isBlank()) {
            sendAction(SearchAction.SearchCleared)
            return
        }

        sendAction(SearchAction.SearchStart)

        job =
            viewModelScope.launch {
                when (val result = searchQuestionsUseCase(query, PAGE_FIRST)) {
                    is Result.Success -> sendAction(SearchAction.SearchSuccess(result.value))
                    is Result.Failure -> sendAction(SearchAction.SearchFailure)
                }
            }
    }

    private companion object {
        const val PAGE_FIRST = 1
    }
}
