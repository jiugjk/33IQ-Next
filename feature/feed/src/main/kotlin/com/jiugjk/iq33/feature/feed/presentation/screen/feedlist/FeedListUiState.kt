package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

@Immutable
internal sealed interface FeedListUiState : BaseState {
    /** The category being loaded, so a retry and the chip row survive a failed first page. */
    val selectedCategory: Category

    @Immutable
    data class Loading(
        override val selectedCategory: Category = Category.ALL,
    ) : FeedListUiState

    @Immutable
    data class Error(
        override val selectedCategory: Category = Category.ALL,
    ) : FeedListUiState

    @Immutable
    data class Content(
        val categories: List<Category> = Category.DEFAULT_CATEGORIES,
        override val selectedCategory: Category = Category.ALL,
        val questions: List<QuestionSummary> = emptyList(),
        val page: Int = 1,
        val isLoadingMore: Boolean = false,
        val canLoadMore: Boolean = true,
        /**
         * The last page request failed. Distinct from `canLoadMore = false`, which means 33IQ said
         * there is nothing more - a failure here is retryable, the end of the list is not.
         */
        val loadMoreFailed: Boolean = false,
    ) : FeedListUiState
}
