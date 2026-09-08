package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

/*
 * Paging results carry the category and page they were requested for. A response that arrives after
 * the user switched category - or after a refresh restarted paging - no longer matches the state and
 * is dropped, instead of appending another category's questions to the visible list.
 */
internal sealed interface FeedListAction : BaseAction<FeedListUiState> {
    class LoadStart(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState) = FeedListUiState.Loading(category)
    }

    class LoadSuccess(
        private val category: Category,
        private val questions: List<QuestionSummary>,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            FeedListUiState.Content(
                selectedCategory = category,
                questions = questions,
                page = FIRST_PAGE,
                canLoadMore = questions.isNotEmpty(),
            )
    }

    class LoadFailure(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState) = FeedListUiState.Error(category)
    }

    class LoadMoreStart(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            if (state is FeedListUiState.Content && state.selectedCategory == category) {
                state.copy(isLoadingMore = true, loadMoreFailed = false)
            } else {
                state
            }
    }

    class LoadMoreSuccess(
        private val category: Category,
        private val page: Int,
        private val newQuestions: List<QuestionSummary>,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState {
            if (state !is FeedListUiState.Content) return state
            // Stale: the category changed, or a refresh reset paging back past this page.
            if (state.selectedCategory != category || state.page != page - 1) return state

            val existingIds = state.questions.map { it.id }.toSet()
            val actuallyNewQuestions = newQuestions.filterNot { it.id in existingIds }

            return state.copy(
                questions = state.questions + actuallyNewQuestions,
                page = page,
                isLoadingMore = false,
                loadMoreFailed = false,
                // If the "next page" came back empty, or turned out to be the same content the site
                // returned for page 1 (see the pagination caveat in QuestionRemoteDataSource), stop.
                canLoadMore = actuallyNewQuestions.isNotEmpty(),
            )
        }
    }

    class LoadMoreFailure(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            if (state is FeedListUiState.Content && state.selectedCategory == category) {
                // Keep canLoadMore: a network blip is not proof that 33IQ has no more questions.
                state.copy(isLoadingMore = false, loadMoreFailed = true)
            } else {
                state
            }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
