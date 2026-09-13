package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import com.jiugjk.iq33.feature.feed.domain.model.Category

/**
 * Everything the question-list screen can ask its view model to do.
 *
 * One callback instead of four same-shaped lambdas threaded through the content composables, which
 * also keeps those composables inside the project's parameter-count budget.
 */
internal sealed interface FeedListEvent {
    data class CategorySelected(
        val category: Category,
    ) : FeedListEvent

    data class HideAnsweredChanged(
        val hide: Boolean,
    ) : FeedListEvent

    /** Requests a fresh batch, or retries a failed request without advancing its cursor. */
    data object Refreshed : FeedListEvent

    /** The list was scrolled close enough to its end to want the next page. */
    data object EndReached : FeedListEvent

    /** Explicit retry of a page whose request failed. */
    data object LoadMoreRetried : FeedListEvent
}
