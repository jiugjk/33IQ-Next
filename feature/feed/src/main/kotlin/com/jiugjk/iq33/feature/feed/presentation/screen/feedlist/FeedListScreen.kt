package com.jiugjk.iq33.feature.feed.presentation.screen.feedlist

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.EmptyState
import com.jiugjk.iq33.feature.base.presentation.compose.composable.ErrorState
import com.jiugjk.iq33.feature.base.presentation.compose.composable.SkeletonList
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Category
import com.jiugjk.iq33.feature.feed.domain.model.redundantCardTag
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_title)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.feed_search_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        FeedListBody(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onQuestionClick = onNavigateToQuestionDetail,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

/**
 * Category chips live here, outside the loading/error/content `when`, so switching category only
 * replaces the list below. Putting a [CategoryChipRow] in each branch used to dispose the row (and
 * its [LazyRow] scroll) every time [FeedListAction.LoadStart] moved the state to Loading.
 */
@Composable
private fun FeedListBody(
    uiState: FeedListUiState,
    onEvent: (FeedListEvent) -> Unit,
    onQuestionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CategoryChipRow(
            categories = chipCategories(uiState),
            selectedCategory = uiState.selectedCategory,
            onEvent = onEvent,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val currentUiState = uiState) {
                is FeedListUiState.Loading -> SkeletonList()
                // The category is kept on failure, so a retry reloads the category the user was
                // actually browsing rather than starting over from "全部".
                is FeedListUiState.Error ->
                    ErrorState(
                        title = stringResource(R.string.feed_load_failed_title),
                        description = stringResource(R.string.feed_load_failed_description),
                        retryLabel = stringResource(R.string.feed_retry),
                        onRetry = { onEvent(FeedListEvent.Refreshed) },
                    )
                is FeedListUiState.Content ->
                    FeedListContent(
                        uiState = currentUiState,
                        onEvent = onEvent,
                        onQuestionClick = onQuestionClick,
                    )
            }
        }
    }
}

private fun chipCategories(uiState: FeedListUiState): List<Category> =
    (uiState as? FeedListUiState.Content)?.categories ?: Category.DEFAULT_CATEGORIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedListContent(
    uiState: FeedListUiState.Content,
    onEvent: (FeedListEvent) -> Unit,
    onQuestionClick: (Long) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { onEvent(FeedListEvent.Refreshed) },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (uiState.questions.isEmpty() && !uiState.isRefreshing) {
            // 33IQ answered with nothing rather than failing - a retry is still offered, since
            // an empty tag page is usually transient.
            EmptyState(
                icon = Icons.Outlined.Inbox,
                title = stringResource(R.string.feed_empty_title),
                description = stringResource(R.string.feed_empty_description),
                actionLabel = stringResource(R.string.feed_retry),
                action = { onEvent(FeedListEvent.Refreshed) },
            )
        } else {
            QuestionList(uiState = uiState, onEvent = onEvent, onQuestionClick = onQuestionClick)
        }
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedCategory: Category,
    onEvent: (FeedListEvent) -> Unit,
    enabled: Boolean = true,
) {
    // Not keyed on the selected category: a click must not recreate this state or scrollToItem(0).
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Dimen.spaceL, vertical = Dimen.spaceM),
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceM),
    ) {
        items(items = categories, key = { it.id }) { category ->
            val selected = category == selectedCategory

            // Selected chips carry a little elevation on top of their container colour, so the
            // current category still reads as chosen where dynamic colour makes the selected and
            // unselected containers close in tone.
            val elevation by animateDpAsState(
                targetValue = if (selected) SelectedChipElevation else 0.dp,
                label = "categoryChipElevation",
            )

            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = { onEvent(FeedListEvent.CategorySelected(category)) },
                label = { Text(category.label(), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
                elevation = FilterChipDefaults.filterChipElevation(elevation = elevation),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = enabled,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
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
        contentPadding = PaddingValues(Dimen.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceML),
    ) {
        items(items = uiState.questions, key = { it.id }) { question ->
            QuestionCard(
                question = question,
                onClick = { onQuestionClick(question.id) },
                hiddenTag = uiState.selectedCategory.redundantCardTag(),
                modifier = Modifier.animateItem(),
            )
        }

        if (uiState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimen.spaceL),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(LoadMoreIndicatorSize))
                }
            }
        }

        if (uiState.loadMoreFailed) {
            item {
                TextButton(
                    onClick = { onEvent(FeedListEvent.LoadMoreRetried) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimen.spaceM),
                ) {
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
private val SelectedChipElevation = 2.dp
private val LoadMoreIndicatorSize = 28.dp

@Preview
@Composable
private fun CategoryChipRowPreview() {
    CategoryChipRow(
        categories = Category.DEFAULT_CATEGORIES,
        selectedCategory = Category.ALL,
        onEvent = { },
    )
}
