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
) : BaseState

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
