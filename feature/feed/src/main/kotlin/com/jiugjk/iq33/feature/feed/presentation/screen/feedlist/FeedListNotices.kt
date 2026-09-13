package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.EmptyState
import com.jiugjk.iq33.feature.feed.R

/*
 * The feed's explanatory chrome: the filter switch and the messages that say why the list looks the
 * way it does. Kept beside FeedListScreen so that file stays about layout and list rendering.
 */

/** A failed refresh keeps the current batch on screen, so the reason for it has to be stated. */
@Composable
internal fun BatchNotice(uiState: FeedListUiState) {
    val content = uiState as? FeedListUiState.Content ?: return
    if (!content.refreshFailed && !content.noNewContent) return

    Text(
        text = stringResource(if (content.refreshFailed) R.string.feed_refresh_failed else R.string.feed_no_new_content),
        style = MaterialTheme.typography.bodySmall,
        color = if (content.refreshFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimen.spaceL, vertical = Dimen.spaceS),
    )
}

/** Empty because everything loaded is filtered out is a different situation from an empty category. */
@Composable
internal fun FeedEmptyState(
    uiState: FeedListUiState.Content,
    onEvent: (FeedListEvent) -> Unit,
) {
    val copy = emptyStateCopy(uiState)
    val next = nextStep(uiState)
    val filterOn = uiState.progress.hideAnswered
    val primaryAction =
        if (filterOn) {
            { onEvent(FeedListEvent.ShowAllQuestions) }
        } else {
            { onEvent(next) }
        }

    Column {
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(copy.title),
            description = stringResource(copy.description),
            actionLabel = stringResource(if (filterOn) R.string.feed_show_all_questions else copy.action),
            action = primaryAction,
        )
        // With the filter on, "show everything" takes the primary slot - so the way forward through
        // the feed gets its own button instead of disappearing.
        if (filterOn && (uiState.canAdvanceFeed || uiState.canContinuePaging)) {
            TextButton(
                onClick = { onEvent(next) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(copy.action))
            }
        }
    }
}

private val FeedListUiState.Content.canAdvanceFeed: Boolean
    get() = canLoadMore && !loadMoreFailed

/** What this empty screen is about: a paused walk, a filtered-out batch, or an empty category. */
private fun emptyStateCopy(uiState: FeedListUiState.Content): EmptyStateCopy {
    val hidden = uiState.questions.isNotEmpty()

    return when {
        uiState.canContinuePaging ->
            EmptyStateCopy(
                R.string.feed_auto_paging_paused_title,
                R.string.feed_auto_paging_paused_description,
                R.string.feed_auto_paging_continue,
            )
        hidden && uiState.canAdvanceFeed ->
            EmptyStateCopy(
                R.string.feed_all_answered_hidden,
                R.string.feed_filtered_loading_more,
                R.string.feed_next_batch,
            )
        hidden ->
            EmptyStateCopy(
                R.string.feed_filtered_exhausted_title,
                R.string.feed_hidden_description,
                R.string.feed_retry,
            )
        uiState.noNewContent ->
            EmptyStateCopy(
                R.string.feed_no_new_title,
                R.string.feed_empty_description,
                R.string.feed_retry,
            )
        else ->
            EmptyStateCopy(
                R.string.feed_empty_title,
                R.string.feed_empty_description,
                R.string.feed_retry,
            )
    }
}

private fun nextStep(uiState: FeedListUiState.Content): FeedListEvent =
    when {
        uiState.canContinuePaging -> FeedListEvent.ContinuePagingRequested
        uiState.loadMoreFailed -> FeedListEvent.LoadMoreRetried
        uiState.canLoadMore -> FeedListEvent.EndReached
        else -> FeedListEvent.Refreshed
    }

private data class EmptyStateCopy(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val action: Int,
)
