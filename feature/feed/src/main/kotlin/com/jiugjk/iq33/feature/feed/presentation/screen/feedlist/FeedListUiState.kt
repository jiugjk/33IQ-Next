package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

@Immutable
internal sealed interface FeedListUiState : BaseState {
    @Immutable
    data object Loading : FeedListUiState

    @Immutable
    data object Error : FeedListUiState

    @Immutable
    data class Content(
        val categories: List<Category> = Category.DEFAULT_CATEGORIES,
        val selectedCategory: Category = Category.ALL,
        val questions: List<QuestionSummary> = emptyList(),
        val page: Int = 1,
        val isLoadingMore: Boolean = false,
        val canLoadMore: Boolean = true,
    ) : FeedListUiState
}
