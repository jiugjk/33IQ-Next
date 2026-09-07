package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

internal sealed interface FeedListAction : BaseAction<FeedListUiState> {
    class LoadStart(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState) = FeedListUiState.Loading
    }

    class LoadSuccess(
        private val category: Category,
        private val questions: List<QuestionSummary>,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            FeedListUiState.Content(
                selectedCategory = category,
                questions = questions,
                page = 1,
                canLoadMore = questions.isNotEmpty(),
            )
    }

    object LoadFailure : FeedListAction {
        override fun reduce(state: FeedListUiState) = FeedListUiState.Error
    }

    object LoadMoreStart : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            if (state is FeedListUiState.Content) state.copy(isLoadingMore = true) else state
    }

    class LoadMoreSuccess(
        private val page: Int,
        private val newQuestions: List<QuestionSummary>,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState {
            if (state !is FeedListUiState.Content) return state

            val existingIds = state.questions.map { it.id }.toSet()
            val actuallyNewQuestions = newQuestions.filterNot { it.id in existingIds }

            return state.copy(
                questions = state.questions + actuallyNewQuestions,
                page = page,
                isLoadingMore = false,
                // If the "next page" came back empty, or turned out to be the same content the site
                // returned for page 1 (see the pagination caveat in QuestionRemoteDataSource), stop.
                canLoadMore = actuallyNewQuestions.isNotEmpty(),
            )
        }
    }

    object LoadMoreFailure : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            if (state is FeedListUiState.Content) state.copy(isLoadingMore = false, canLoadMore = false) else state
    }
}
