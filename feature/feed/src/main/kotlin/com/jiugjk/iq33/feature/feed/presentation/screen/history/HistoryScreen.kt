package com.jiugjk.iq33.feature.feed.presentation.screen.history

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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.EmptyState
import com.jiugjk.iq33.feature.base.presentation.compose.composable.LoadingIndicator
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.AnswerRecord
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onQuestionClick: (Long) -> Unit = {},
) {
    val viewModel: HistoryViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            HistoryUiState.Loading -> LoadingIndicator()
            HistoryUiState.Empty ->
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.feed_history_empty),
                    description = stringResource(R.string.feed_history_empty_description),
                )
            is HistoryUiState.Content ->
                HistoryContent(
                    state = state,
                    onQuestionClick = onQuestionClick,
                    onDelete = viewModel::onDelete,
                    onFilter = viewModel::onFilter,
                    onQuery = viewModel::onQuery,
                    onClearRequested = viewModel::onClearRequested,
                    onClearDismissed = viewModel::onClearDismissed,
                    onClearConfirmed = viewModel::onClearConfirmed,
                )
        }
    }
}

@Composable
private fun HistoryContent(
    state: HistoryUiState.Content,
    onQuestionClick: (Long) -> Unit,
    onDelete: (AnswerRecord) -> Unit,
    onFilter: (HistoryFilter) -> Unit,
    onQuery: (String) -> Unit,
    onClearRequested: () -> Unit,
    onClearDismissed: () -> Unit,
    onClearConfirmed: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimen.spaceL, vertical = Dimen.spaceS),
            singleLine = true,
            label = { Text(stringResource(R.string.feed_history_search)) },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimen.spaceL),
            horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(filterLabel(filter)) },
                )
            }
            TextButton(onClick = onClearRequested) {
                Text(stringResource(R.string.feed_history_clear))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimen.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimen.spaceML),
        ) {
            items(items = state.records, key = { "${it.accountKey}:${it.questionId}" }) { record ->
                HistoryItem(
                    record = record,
                    onClick = { onQuestionClick(record.questionId) },
                    onDelete = { onDelete(record) },
                )
            }
        }
    }

    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = onClearDismissed,
            title = { Text(stringResource(R.string.feed_history_clear_title)) },
            text = { Text(stringResource(R.string.feed_history_clear_message)) },
            confirmButton = {
                TextButton(onClick = onClearConfirmed) {
                    Text(stringResource(R.string.feed_history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onClearDismissed) {
                    Text(stringResource(R.string.feed_history_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryItem(
    record: AnswerRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title.ifBlank { "#${record.questionId}" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = resultLabel(record),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimen.spaceS),
                    )
                    if (record.categoryId.isNotBlank()) {
                        Text(
                            text = record.categoryId,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Dimen.spaceS),
                        )
                    }
                    Text(
                        text = formatTime(record.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimen.spaceS),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.feed_history_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun filterLabel(filter: HistoryFilter): String =
    stringResource(
        when (filter) {
            HistoryFilter.ALL -> R.string.feed_history_filter_all
            HistoryFilter.CORRECT -> R.string.feed_history_filter_correct
            HistoryFilter.WRONG -> R.string.feed_history_filter_wrong
            HistoryFilter.VIEWED_ONLY -> R.string.feed_history_filter_viewed
        },
    )

@Composable
private fun resultLabel(record: AnswerRecord): String =
    when {
        record.isCorrect == true -> stringResource(R.string.feed_history_result_correct)
        record.isCorrect == false -> stringResource(R.string.feed_history_result_wrong)
        record.viewedExplanation && record.answeredAt == null ->
            stringResource(R.string.feed_history_result_viewed)
        record.answeredAt != null -> stringResource(R.string.feed_history_result_answered)
        else -> stringResource(R.string.feed_history_result_viewed)
    }

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}
