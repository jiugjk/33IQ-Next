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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
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
            FavouriteUiState.Loading -> Unit
            FavouriteUiState.Empty -> EmptyFavourites()
            FavouriteUiState.Error -> FavouritesUnavailable()
            is FavouriteUiState.Content ->
                FavouriteList(
                    savedQuestions = currentUiState.savedQuestions,
                    onQuestionClick = onQuestionClick,
                    onRemoveClick = viewModel::onRemoveClick,
                )
        }
    }
}

@Composable
private fun EmptyFavourites(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.favourite_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FavouritesUnavailable(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.favourite_storage_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FavouriteList(
    savedQuestions: List<SavedQuestion>,
    onQuestionClick: (Long) -> Unit,
    onRemoveClick: (SavedQuestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimen.spaceM),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
    ) {
        items(items = savedQuestions, key = { it.id }) { savedQuestion ->
            FavouriteItem(
                savedQuestion = savedQuestion,
                onClick = { onQuestionClick(savedQuestion.id) },
                onRemoveClick = { onRemoveClick(savedQuestion) },
            )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Text(
                    text = savedQuestion.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimen.spaceS),
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
        onQuestionClick = { },
        onRemoveClick = { },
    )
}
