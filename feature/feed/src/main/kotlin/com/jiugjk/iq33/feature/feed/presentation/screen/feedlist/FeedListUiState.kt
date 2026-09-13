package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.domain.model.QuestionProgress

@Immutable
internal sealed interface FeedListUiState : BaseState {
    /** The category being loaded, so a retry and the chip row survive a failed first page. */
    val selectedCategory: Category
    val progress: QuestionProgress

    @Immutable
    data class Loading(
        override val selectedCategory: Category = Category.ALL,
        override val progress: QuestionProgress = QuestionProgress(),
    ) : FeedListUiState

    @Immutable
    data class Error(
        override val selectedCategory: Category = Category.ALL,
        override val progress: QuestionProgress = QuestionProgress(),
    ) : FeedListUiState

    @Immutable
    data class Content(
        val categories: List<Category> = Category.DEFAULT_CATEGORIES,
        override val selectedCategory: Category = Category.ALL,
        val questions: List<QuestionSummary> = emptyList(),
        val page: Int = 1,
        override val progress: QuestionProgress = QuestionProgress(),
        val nextPageUrl: String? = null,
        val refreshFailed: Boolean = false,
        val batchRevision: Int = 0,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val canLoadMore: Boolean = true,
        /**
         * The last page request failed. Distinct from `canLoadMore = false`, which means 33IQ said
         * there is nothing more - a failure here is retryable, the end of the list is not.
         */
        val loadMoreFailed: Boolean = false,
        /**
         * Automatic paging stopped because one continuous walk spent its request budget without
         * finding anything visible. Not an error and not the end of the feed - the user decides
         * whether to keep going.
         */
        val autoPagingPaused: Boolean = false,
    ) : FeedListUiState {
        /**
         * "隐藏已答" hides answered questions and nothing else. Viewing the analysis or leaving a
         * reveal pending also blocks a submission, but the user never answered those - hiding them
         * would quietly take questions out of the feed that were only ever looked at.
         */
        val visibleQuestions: List<QuestionSummary>
            get() = if (progress.hideAnswered) questions.filterNot { it.id in progress.answeredIds } else questions

        /** No page request is in flight, and none is sitting unretried. */
        private val isPagingIdle: Boolean
            get() = !isLoadingMore && !isRefreshing && !loadMoreFailed

        /**
         * A further page may be started right now.
         *
         * A failed page waits for [canRetryLoadMore] instead: the scroll trigger sits at the bottom
         * of the list, so auto-retrying would hammer a failing endpoint for as long as the user
         * stays there.
         */
        val canStartLoadMore: Boolean
            get() = isPagingIdle && canLoadMore && !autoPagingPaused

        /** The failed page can be asked for again, without discarding what is already listed. */
        val canRetryLoadMore: Boolean
            get() = loadMoreFailed && !isLoadingMore && !isRefreshing

        /** The user may extend a paused automatic walk; this bypasses [canLoadMore] on purpose. */
        val canContinuePaging: Boolean
            get() = autoPagingPaused && nextPageUrl != null && !isLoadingMore && !isRefreshing
    }
}
