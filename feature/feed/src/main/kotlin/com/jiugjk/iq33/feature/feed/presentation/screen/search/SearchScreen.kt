package com.jiugjk.iq33.feature.feed.presentation.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                        isLoadingMore = uiState.isLoadingMore,
                        loadMoreFailed = uiState.loadMoreFailed,
                        onQuestionClick = onNavigateToQuestionDetail,
                        onEndReached = viewModel::onEndReached,
                        onLoadMoreRetry = viewModel::onLoadMoreRetry,
                    )
            }
        }
    }
}

@Composable
private fun SearchHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.feed_search_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchEmpty(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.feed_search_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ErrorAnim()
        Button(onClick = onRetry, modifier = Modifier.padding(top = Dimen.spaceL)) {
            Text(stringResource(R.string.feed_retry))
        }
    }
}

@Composable
private fun SearchResultList(
    questions: List<QuestionSummary>,
    isLoadingMore: Boolean,
    loadMoreFailed: Boolean,
    onQuestionClick: (Long) -> Unit,
    onEndReached: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    SearchLoadMoreTrigger(listState = listState, questionCount = questions.size, onLoadMore = onEndReached)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Dimen.spaceM),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
    ) {
        items(items = questions, key = { it.id }) { question ->
            QuestionCard(question = question, onClick = { onQuestionClick(question.id) })
        }

        if (isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimen.spaceXL).padding(Dimen.spaceM))
                }
            }
        }

        if (loadMoreFailed) {
            item {
                TextButton(onClick = onLoadMoreRetry, modifier = Modifier.fillMaxWidth()) {
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

@Preview
@Composable
private fun SearchHintPreview() {
    SearchHint()
}
