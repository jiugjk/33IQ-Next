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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary
import com.jiugjk.iq33.feature.feed.presentation.composable.QuestionCard
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
            FeedListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            FeedListUiState.Error -> ErrorAnim()
            is FeedListUiState.Content ->
                FeedListContent(
                    uiState = currentUiState,
                    onCategorySelect = viewModel::selectCategory,
                    onRefresh = viewModel::onRefresh,
                    onLoadMore = viewModel::loadMore,
                    onQuestionClick = onNavigateToQuestionDetail,
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedListContent(
    uiState: FeedListUiState.Content,
    onCategorySelect: (Category) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onQuestionClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CategoryChipRow(
            categories = uiState.categories,
            selectedCategory = uiState.selectedCategory,
            onCategorySelect = onCategorySelect,
        )

        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            QuestionList(
                questions = uiState.questions,
                isLoadingMore = uiState.isLoadingMore,
                onQuestionClick = onQuestionClick,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedCategory: Category,
    onCategorySelect: (Category) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimen.spaceM, vertical = Dimen.spaceS),
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS),
    ) {
        items(items = categories, key = { it.tagName }) { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategorySelect(category) },
                label = { Text(category.displayName) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun QuestionList(
    questions: List<QuestionSummary>,
    isLoadingMore: Boolean,
    onQuestionClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by
        remember {
            derivedStateOf {
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: 0
                lastVisibleIndex >= questions.size - LOAD_MORE_THRESHOLD
            }
        }
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && questions.isNotEmpty()) {
            currentOnLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
    }
}

private const val LOAD_MORE_THRESHOLD = 4

@Preview
@Composable
private fun CategoryChipRowPreview() {
    CategoryChipRow(
        categories = Category.DEFAULT_CATEGORIES,
        selectedCategory = Category.ALL,
        onCategorySelect = { },
    )
}
