package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    val hidden = uiState.questions.isNotEmpty()
    val canAdvance = uiState.canLoadMore && !uiState.loadMoreFailed
    val filterOn = uiState.progress.hideAnswered

    val title =
        when {
            hidden && canAdvance -> R.string.feed_all_answered_hidden
            hidden && !canAdvance -> R.string.feed_filtered_exhausted_title
            uiState.noNewContent -> R.string.feed_no_new_title
            else -> R.string.feed_empty_title
        }
    val description =
        when {
            hidden && canAdvance -> R.string.feed_filtered_loading_more
            hidden -> R.string.feed_hidden_description
            else -> R.string.feed_empty_description
        }
    val next =
        when {
            uiState.loadMoreFailed -> FeedListEvent.LoadMoreRetried
            uiState.canLoadMore -> FeedListEvent.EndReached
            else -> FeedListEvent.Refreshed
        }
    val primaryLabel =
        when {
            filterOn -> R.string.feed_show_all_questions
            canAdvance -> R.string.feed_next_batch
            else -> R.string.feed_retry
        }
    val primaryAction =
        if (filterOn) {
            { onEvent(FeedListEvent.ShowAllQuestions) }
        } else {
            { onEvent(next) }
        }

    Column {
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(title),
            description = stringResource(description),
            actionLabel = stringResource(primaryLabel),
            action = primaryAction,
        )
        if (filterOn && canAdvance) {
            TextButton(
                onClick = { onEvent(next) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.feed_next_batch))
            }
        }
    }
}
