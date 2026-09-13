package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseAction
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress
import com.jiugjk.iq33.feature.feed.presentation.paging.mergeUniqueById

/*
 * Paging results carry the category and page they were requested for. A response that arrives after
 * the user switched category - or after a refresh restarted paging - no longer matches the state and
 * is dropped, instead of appending another category's questions to the visible list.
 */
internal sealed interface FeedListAction : BaseAction<FeedListUiState> {
    class ProgressChanged(
        private val progress: QuestionProgress,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            when (state) {
                is FeedListUiState.Loading -> state.copy(progress = progress)
                is FeedListUiState.Error -> state.copy(progress = progress)
                is FeedListUiState.Content -> state.copy(progress = progress)
            }
    }

    class LoadStart(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState) = FeedListUiState.Loading(category, state.progress)
    }

    class LoadSuccess(
        private val category: Category,
        private val questions: List<QuestionSummary>,
        private val nextPageUrl: String? = null,
        private val hasMore: Boolean = questions.isNotEmpty(),
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            FeedListUiState.Content(
                selectedCategory = category,
                questions = questions,
                page = FIRST_PAGE,
                canLoadMore = hasMore,
                nextPageUrl = nextPageUrl,
                progress = state.progress,
                batchRevision = ((state as? FeedListUiState.Content)?.batchRevision ?: 0) + 1,
            )
    }

    class RefreshStart(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            if (state is FeedListUiState.Content && state.selectedCategory == category) {
                state.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false, refreshFailed = false, noNewContent = false)
            } else {
                state
            }
    }

    class RefreshFailure(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState =
            if (state is FeedListUiState.Content && state.selectedCategory == category) {
                state.copy(isRefreshing = false, refreshFailed = true)
            } else {
                state
            }
    }

    class LoadFailure(
        private val category: Category,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState) = FeedListUiState.Error(category, state.progress)
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
        private val nextPageUrl: String? = null,
        private val hasMore: Boolean = newQuestions.isNotEmpty(),
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState {
            if (state !is FeedListUiState.Content) return state
            // Stale: the category changed, or a refresh reset paging back past this page.
            if (state.selectedCategory != category || state.page != page - 1) return state

            val (merged, hasNew) = mergeUniqueById(state.questions, newQuestions) { it.id }

            return state.copy(
                questions = merged,
                page = page,
                isLoadingMore = false,
                loadMoreFailed = false,
                // A duplicate-only reply must not keep an automatic paging loop alive.
                canLoadMore = hasNew && hasMore,
                nextPageUrl = nextPageUrl,
                noNewContent = !hasNew,
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

    class NoNewContent(
        private val category: Category,
        private val nextPageUrl: String?,
    ) : FeedListAction {
        override fun reduce(state: FeedListUiState): FeedListUiState {
            if (state.selectedCategory != category) return state
            val content =
                state as? FeedListUiState.Content ?: FeedListUiState.Content(selectedCategory = category, progress = state.progress)
            return content.copy(isRefreshing = false, noNewContent = true, nextPageUrl = nextPageUrl, canLoadMore = false)
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
