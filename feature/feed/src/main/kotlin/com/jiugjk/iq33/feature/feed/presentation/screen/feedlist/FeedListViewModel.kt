package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.lifecycle.viewModelScope
import com.jiugjk.iq33.feature.base.domain.result.Result
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseViewModel
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.usecase.GetQuestionListUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class FeedListViewModel(
    private val getQuestionListUseCase: GetQuestionListUseCase,
) : BaseViewModel<FeedListUiState, FeedListAction>(FeedListUiState.Loading) {
    private var loadJob: Job? = null

    fun onInit() {
        if (uiStateFlow.value == FeedListUiState.Loading) {
            selectCategory(Category.ALL)
        }
    }

    fun selectCategory(category: Category) {
        loadJob?.cancel()

        sendAction(FeedListAction.LoadStart(category))

        loadJob =
            viewModelScope.launch {
                when (val result = getQuestionListUseCase(category, PAGE_FIRST)) {
                    is Result.Success -> sendAction(FeedListAction.LoadSuccess(category, result.value))
                    is Result.Failure -> sendAction(FeedListAction.LoadFailure)
                }
            }
    }

    fun onRefresh() {
        val currentState = uiStateFlow.value

        if (currentState is FeedListUiState.Content) {
            selectCategory(currentState.selectedCategory)
        }
    }

    fun loadMore() {
        val currentState = uiStateFlow.value

        if (currentState !is FeedListUiState.Content || currentState.isLoadingMore || !currentState.canLoadMore) {
            return
        }

        sendAction(FeedListAction.LoadMoreStart)

        viewModelScope.launch {
            val nextPage = currentState.page + 1

            when (val result = getQuestionListUseCase(currentState.selectedCategory, nextPage)) {
                is Result.Success -> sendAction(FeedListAction.LoadMoreSuccess(nextPage, result.value))
                is Result.Failure -> sendAction(FeedListAction.LoadMoreFailure)
            }
        }
    }

    private companion object {
        const val PAGE_FIRST = 1
    }
}
