package com.jiugjk.iq33.feature.feed.presentation.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.jiugjk.iq33.feature.base.presentation.compose.composable.LoadingIndicator
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.presentation.composable.QuestionCard
import com.jiugjk.iq33.feature.feed.presentation.composable.SearchBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onNavigateToQuestionDetail: (Long) -> Unit = {},
) {
    val viewModel: SearchViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feed_navigate_back),
                )
            }

            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                autoFocus = true,
                modifier = Modifier.weight(1f),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val results = uiState.results) {
                SearchResults.Idle -> SearchHint()
                SearchResults.Loading -> LoadingIndicator()
                SearchResults.Error -> SearchError(onRetry = viewModel::onRetry)
                SearchResults.Empty -> SearchEmpty()
                is SearchResults.Content ->
                    SearchResultList(
                        questions = results.questions,
                        paging =
                            SearchPaging(
                                isLoadingMore = uiState.isLoadingMore,
                                failed = uiState.loadMoreFailed,
                                onEndReached = viewModel::onEndReached,
                                onRetry = viewModel::onLoadMoreRetry,
                            ),
                        onQuestionClick = onNavigateToQuestionDetail,
                    )
            }
        }
    }
}

@Composable
private fun SearchHint(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Search,
        title = stringResource(R.string.feed_search_hint),
        modifier = modifier,
    )
}

@Composable
private fun SearchEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.SearchOff,
        title = stringResource(R.string.feed_search_empty),
        description = stringResource(R.string.feed_search_empty_description),
        modifier = modifier,
    )
}

@Composable
private fun SearchError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorState(
        title = stringResource(R.string.feed_load_failed_title),
        description = stringResource(R.string.feed_load_failed_description),
        retryLabel = stringResource(R.string.feed_retry),
        onRetry = onRetry,
        modifier = modifier,
    )
}

/**
 * The paging half of the result list: what the next page is doing, and how to ask for it.
 *
 * Bundled rather than passed as four separate parameters, so the list keeps one parameter per
 * concern - the questions, their paging, and what a tap on one means.
 */
@Immutable
private data class SearchPaging(
    val isLoadingMore: Boolean,
    val failed: Boolean,
    val onEndReached: () -> Unit,
    val onRetry: () -> Unit,
)

@Composable
private fun SearchResultList(
    questions: List<QuestionSummary>,
    paging: SearchPaging,
    onQuestionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    SearchLoadMoreTrigger(listState = listState, questionCount = questions.size, onLoadMore = paging.onEndReached)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Dimen.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceML),
    ) {
        items(items = questions, key = { it.id }) { question ->
            QuestionCard(
                question = question,
                onClick = { onQuestionClick(question.id) },
                modifier = Modifier.animateItem(),
            )
        }

        if (paging.isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(LoadMoreIndicatorSize))
                }
            }
        }

        if (paging.failed) {
            item {
                TextButton(onClick = paging.onRetry, modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun SearchLoadMoreTrigger(
    listState: LazyListState,
    questionCount: Int,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentCount by rememberUpdatedState(questionCount)

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            val count = currentCount
            val reachedEnd = count > 0 && lastVisibleIndex >= count - LOAD_MORE_THRESHOLD

            count.takeIf { reachedEnd }
        }.distinctUntilChanged()
            .filterNotNull()
            .collect { currentOnLoadMore() }
    }
}

private const val LOAD_MORE_THRESHOLD = 4
private val LoadMoreIndicatorSize = 28.dp

@Preview
@Composable
private fun SearchHintPreview() {
    SearchHint()
}
