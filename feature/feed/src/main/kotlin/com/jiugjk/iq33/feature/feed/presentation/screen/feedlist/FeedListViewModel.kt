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
) : BaseViewModel<FeedListUiState, FeedListAction>(FeedListUiState.Loading()) {
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    /** Single entry point for the screen's interactions - see [FeedListEvent]. */
    fun onEvent(event: FeedListEvent) {
        when (event) {
            is FeedListEvent.CategorySelected -> selectCategory(event.category)
            FeedListEvent.Refreshed -> refresh()
            FeedListEvent.EndReached -> loadMore()
            FeedListEvent.LoadMoreRetried -> retryLoadMore()
        }
    }

    fun onInit() {
        val state = uiStateFlow.value

        if (state is FeedListUiState.Loading && loadJob?.isActive != true) {
            selectCategory(state.selectedCategory)
        }
    }

    private fun selectCategory(category: Category) {
        // Both jobs belong to the category being replaced: a first page and an in-flight next page
        // must be abandoned together, or the old category's page 2 lands in the new category's list.
        loadJob?.cancel()
        loadMoreJob?.cancel()

        sendAction(FeedListAction.LoadStart(category))

        loadJob =
            viewModelScope.launch {
                when (val result = getQuestionListUseCase(category, PAGE_FIRST)) {
                    is Result.Success -> sendAction(FeedListAction.LoadSuccess(category, result.value))
                    is Result.Failure -> sendAction(FeedListAction.LoadFailure(category))
                }
            }
    }

    /** Reloads the first page without blanking a list that is already on screen. */
    private fun refresh() {
        val current = uiStateFlow.value

        if (current !is FeedListUiState.Content) {
            selectCategory(current.selectedCategory)
            return
        }

        loadJob?.cancel()
        loadMoreJob?.cancel()

        val category = current.selectedCategory

        sendAction(FeedListAction.RefreshStart(category))

        loadJob =
            viewModelScope.launch {
                when (val result = getQuestionListUseCase(category, PAGE_FIRST)) {
                    is Result.Success -> sendAction(FeedListAction.LoadSuccess(category, result.value))
                    is Result.Failure -> sendAction(FeedListAction.RefreshFailure(category))
                }
            }
    }

    private fun loadMore() {
        val currentState = uiStateFlow.value as? FeedListUiState.Content ?: return

        if (!currentState.canStartLoadMore) return

        startLoadMore(currentState)
    }

    /** Retries the page whose request failed, without discarding what is already listed. */
    private fun retryLoadMore() {
        val currentState = uiStateFlow.value as? FeedListUiState.Content ?: return

        if (!currentState.canRetryLoadMore) return

        startLoadMore(currentState)
    }

    private fun startLoadMore(currentState: FeedListUiState.Content) {
        if (loadMoreJob?.isActive == true) return

        val category = currentState.selectedCategory
        val nextPage = currentState.page + 1

        sendAction(FeedListAction.LoadMoreStart(category))

        loadMoreJob =
            viewModelScope.launch {
                when (val result = getQuestionListUseCase(category, nextPage)) {
                    is Result.Success -> sendAction(FeedListAction.LoadMoreSuccess(category, nextPage, result.value))
                    is Result.Failure -> sendAction(FeedListAction.LoadMoreFailure(category))
                }
            }
    }

    private companion object {
        const val PAGE_FIRST = 1
    }
}
