package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/**
 * The hide switch, plus the standing caveat under it.
 *
 * The caveat is not decoration: the switch hides questions using records this device made, so it
 * cannot hide answers given on the website, another device or an older install.
 */
@Composable
internal fun HideAnsweredRow(
    uiState: FeedListUiState,
    onEvent: (FeedListEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.spaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feed_hide_answered),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = uiState.progress.hideAnswered,
                onCheckedChange = { onEvent(FeedListEvent.HideAnsweredChanged(it)) },
            )
        }
        Text(
            text = stringResource(R.string.feed_answered_local_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimen.spaceL),
        )
    }
}

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

    val title =
        when {
            hidden -> R.string.feed_all_answered_hidden
            uiState.noNewContent -> R.string.feed_no_new_title
            else -> R.string.feed_empty_title
        }
    val next =
        when {
            uiState.loadMoreFailed -> FeedListEvent.LoadMoreRetried
            uiState.canLoadMore -> FeedListEvent.EndReached
            else -> FeedListEvent.Refreshed
        }

    EmptyState(
        icon = Icons.Outlined.Inbox,
        title = stringResource(title),
        description = stringResource(if (hidden) R.string.feed_hidden_description else R.string.feed_empty_description),
        actionLabel = stringResource(if (canAdvance) R.string.feed_next_batch else R.string.feed_retry),
        action = { onEvent(next) },
    )
}
