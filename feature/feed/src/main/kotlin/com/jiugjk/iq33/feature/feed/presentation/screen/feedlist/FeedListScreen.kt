package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.ErrorAnim
import com.jiugjk.iq33.feature.base.presentation.compose.composable.LoadingIndicator
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.presentation.composable.QuestionCard
import com.jiugjk.iq33.feature.feed.presentation.composable.label
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(
    modifier: Modifier = Modifier,
    onNavigateToQuestionDetail: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
) {
    val viewModel: FeedListViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onInit()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.feed_title)) },
            actions = {
                IconButton(onClick = onNavigateToSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.feed_search_content_description),
                    )
                }
            },
        )

        when (val currentUiState = uiState) {
            is FeedListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            // The category is kept on failure, so the chips stay usable and a retry reloads the
            // category the user was actually browsing rather than starting over from "全部".
            is FeedListUiState.Error ->
                FeedListError(selectedCategory = currentUiState.selectedCategory, onEvent = viewModel::onEvent)
            is FeedListUiState.Content ->
                FeedListContent(
                    uiState = currentUiState,
                    onEvent = viewModel::onEvent,
                    onQuestionClick = onNavigateToQuestionDetail,
                )
        }
    }
}

@Composable
private fun FeedListError(
    selectedCategory: Category,
    onEvent: (FeedListEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CategoryChipRow(
            categories = Category.DEFAULT_CATEGORIES,
            selectedCategory = selectedCategory,
            onEvent = onEvent,
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ErrorAnim()

            Button(onClick = { onEvent(FeedListEvent.Refreshed) }, modifier = Modifier.padding(top = Dimen.spaceL)) {
                Text(stringResource(R.string.feed_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedListContent(
    uiState: FeedListUiState.Content,
    onEvent: (FeedListEvent) -> Unit,
    onQuestionClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CategoryChipRow(
            categories = uiState.categories,
            selectedCategory = uiState.selectedCategory,
            onEvent = onEvent,
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onEvent(FeedListEvent.Refreshed) },
            modifier = Modifier.fillMaxSize(),
        ) {
            QuestionList(uiState = uiState, onEvent = onEvent, onQuestionClick = onQuestionClick)
        }
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedCategory: Category,
    onEvent: (FeedListEvent) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimen.spaceM, vertical = Dimen.spaceS),
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS),
    ) {
        items(items = categories, key = { it.tagName }) { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onEvent(FeedListEvent.CategorySelected(category)) },
                label = { Text(category.label()) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun QuestionList(
    uiState: FeedListUiState.Content,
    onEvent: (FeedListEvent) -> Unit,
    onQuestionClick: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    LoadMoreTrigger(listState = listState, uiState = uiState, onLoadMore = { onEvent(FeedListEvent.EndReached) })

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimen.spaceM),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
    ) {
        items(items = uiState.questions, key = { it.id }) { question ->
            QuestionCard(question = question, onClick = { onQuestionClick(question.id) })
        }

        if (uiState.isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimen.spaceXL).padding(Dimen.spaceM))
                }
            }
        }

        if (uiState.loadMoreFailed) {
            item {
                TextButton(onClick = { onEvent(FeedListEvent.LoadMoreRetried) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.feed_load_more_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Requests the next page whenever the list is scrolled near its end.
 *
 * The scroll position and the current list length are read inside a [snapshotFlow], so the threshold
 * is recomputed against the list as it grows. A `derivedStateOf` remembered without keys captures
 * the list it was created with, which pins the threshold to the first page's length: the derived
 * boolean then stays `true` forever and, because it never changes, never triggers another load -
 * paging stops after page two.
 */
@Composable
private fun LoadMoreTrigger(
    listState: LazyListState,
    uiState: FeedListUiState.Content,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentUiState by rememberUpdatedState(uiState)

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            val questionCount = currentUiState.questions.size
            val reachedEnd = questionCount > 0 && lastVisibleIndex >= questionCount - LOAD_MORE_THRESHOLD

            // Emitting the count (not just a boolean) means an appended page re-arms the trigger
            // even when the longer list still ends on screen, so paging continues past page two.
            questionCount.takeIf { reachedEnd }
        }.distinctUntilChanged()
            .filterNotNull()
            .collect { currentOnLoadMore() }
    }
}

private const val LOAD_MORE_THRESHOLD = 4

@Preview
@Composable
private fun CategoryChipRowPreview() {
    CategoryChipRow(
        categories = Category.DEFAULT_CATEGORIES,
        selectedCategory = Category.ALL,
        onEvent = { },
    )
}
