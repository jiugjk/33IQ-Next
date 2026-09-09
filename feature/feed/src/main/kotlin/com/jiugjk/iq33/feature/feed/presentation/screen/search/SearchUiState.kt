package com.jiugjk.iq33.feature.feed.presentation.screen.search

import androidx.compose.runtime.Immutable
import com.jiugjk.iq33.feature.base.presentation.viewmodel.BaseState
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

/**
 * The search screen's whole state, query included.
 *
 * The query lives here rather than in the search field's own `remember`: the field is recreated
 * whenever the screen leaves composition - opening a result and coming back, for instance - and a
 * field that owns the query would come back empty and then submit that empty query, wiping the
 * results the user navigated away from.
 */
@Immutable
internal data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults.Idle,
    val page: Int = 1,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
) : BaseState {
    /** The results currently listed, if any - paging only ever appends to those. */
    private val listedQuestions: List<QuestionSummary>
        get() = (results as? SearchResults.Content)?.questions.orEmpty()

    /** No page request is in flight, and none is sitting unretried. */
    private val isPagingIdle: Boolean
        get() = !isLoadingMore && !loadMoreFailed

    /**
     * A further page may be started right now.
     *
     * A failed page waits for [canRetryLoadMore] instead: the scroll trigger sits at the bottom of
     * the list, so auto-retrying would hammer a failing endpoint for as long as the user stays there.
     */
    val canStartLoadMore: Boolean
        get() = isPagingIdle && canLoadMore && listedQuestions.isNotEmpty()

    /** The failed page can be asked for again, without discarding what is already listed. */
    val canRetryLoadMore: Boolean
        get() = loadMoreFailed && !isLoadingMore && results is SearchResults.Content
}

@Immutable
internal sealed interface SearchResults {
    @Immutable
    data object Idle : SearchResults

    @Immutable
    data object Loading : SearchResults

    @Immutable
    data object Error : SearchResults

    @Immutable
    data object Empty : SearchResults

    @Immutable
    data class Content(
        val questions: List<QuestionSummary>,
    ) : SearchResults
}
