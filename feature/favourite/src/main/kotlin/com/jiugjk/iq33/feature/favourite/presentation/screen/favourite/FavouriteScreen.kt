package com.jiugjk.iq33.feature.favourite.presentation.screen.favourite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.EmptyState
import com.jiugjk.iq33.feature.base.presentation.compose.composable.LoadingIndicator
import com.jiugjk.iq33.feature.base.presentation.compose.composable.TagChipRow
import com.jiugjk.iq33.feature.favourite.R
import com.jiugjk.iq33.feature.favourite.domain.model.SavedQuestion
import org.koin.androidx.compose.koinViewModel

@Composable
fun FavouriteScreen(
    modifier: Modifier = Modifier,
    onQuestionClick: (Long) -> Unit = {},
) {
    val viewModel: FavouriteViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val currentUiState = uiState) {
            FavouriteUiState.Loading -> LoadingIndicator()
            FavouriteUiState.Empty -> EmptyFavourites()
            FavouriteUiState.Error -> FavouritesUnavailable(onRetry = viewModel::onRetry)
            is FavouriteUiState.Content ->
                FavouriteList(
                    savedQuestions = currentUiState.savedQuestions,
                    actionFailed = currentUiState.actionFailed,
                    onQuestionClick = onQuestionClick,
                    onRemoveClick = viewModel::onRemoveClick,
                )
        }
    }
}

@Composable
private fun EmptyFavourites(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.BookmarkBorder,
        title = stringResource(R.string.favourite_empty),
        description = stringResource(R.string.favourite_empty_description),
        modifier = modifier,
    )
}

/**
 * The local database could not be read. Retry re-subscribes to the observation flow rather than
 * assuming the process has to be restarted.
 */
@Composable
private fun FavouritesUnavailable(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.favourite_storage_error),
        actionLabel = stringResource(R.string.favourite_retry),
        action = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun FavouriteList(
    savedQuestions: List<SavedQuestion>,
    actionFailed: Boolean,
    onQuestionClick: (Long) -> Unit,
    onRemoveClick: (SavedQuestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (actionFailed) {
            Text(
                text = stringResource(R.string.favourite_action_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Dimen.spaceM, vertical = Dimen.spaceS),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimen.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimen.spaceML),
        ) {
            items(items = savedQuestions, key = { it.id }) { savedQuestion ->
                FavouriteItem(
                    savedQuestion = savedQuestion,
                    onClick = { onQuestionClick(savedQuestion.id) },
                    onRemoveClick = { onRemoveClick(savedQuestion) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FavouriteItem(
    savedQuestion: SavedQuestion,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimen.spaceL),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = savedQuestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.favourite_remove_content_description),
                    )
                }
            }

            if (savedQuestion.tags.isNotEmpty()) {
                // Every tag is shown here: unlike the feed there is no category context, so the tag
                // is the only clue to where a bookmarked question came from.
                TagChipRow(
                    tags = savedQuestion.tags,
                    modifier = Modifier.padding(top = Dimen.spaceM),
                )
            }
        }
    }
}

@Preview
@Composable
private fun FavouriteListPreview() {
    FavouriteList(
        savedQuestions =
            listOf(
                SavedQuestion(id = 1, title = "示例题目标题", tags = listOf("逻辑思维"), savedAt = 0L),
            ),
        actionFailed = false,
        onQuestionClick = { },
        onRemoveClick = { },
    )
}
