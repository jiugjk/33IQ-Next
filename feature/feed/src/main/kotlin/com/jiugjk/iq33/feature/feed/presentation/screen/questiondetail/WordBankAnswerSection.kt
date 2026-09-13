package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R

/** Non-lazy wrapping tiles: the detail screen already owns vertical scrolling. */
@Composable
internal fun WordBankAnswerSection(
    uiState: QuestionDetailUiState.Content,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val length = uiState.detail.answerLength

    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimen.spaceL)) {
        if (!uiState.isWordBankReady || length == null) {
            WordBankUnavailable(onEvent = onEvent)
        } else {
            WordBankPicker(uiState = uiState, length = length, onEvent = onEvent)
        }
    }
}

/**
 * Shown when the server did not return a usable tile set or answer length.
 *
 * Deliberately offers a retry instead of falling back to a free-text field: the site grades this
 * question against its own tiles, so an unrestricted guess would just spend an attempt.
 */
@Composable
private fun WordBankUnavailable(onEvent: (QuestionDetailEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.feed_word_bank_unavailable),
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = { onEvent(QuestionDetailEvent.RetryRequested) }) {
            Text(stringResource(R.string.feed_retry))
        }
    }
}

@Composable
private fun WordBankPicker(
    uiState: QuestionDetailUiState.Content,
    length: Int,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val answer = uiState.wordBankAnswer
    val hasSelection = uiState.selectedCandidateIndices.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = answer,
            onValueChange = {},
            readOnly = true,
            enabled = uiState.canSelectChoice,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feed_word_bank_length, length)) },
            placeholder = { Text(stringResource(R.string.feed_word_bank_placeholder)) },
        )

        Row {
            TextButton(
                onClick = { uiState.selectedCandidateIndices.lastOrNull()?.let { onEvent(QuestionDetailEvent.CandidateToggled(it)) } },
                enabled = uiState.canSelectChoice && hasSelection,
            ) {
                Text(stringResource(R.string.feed_word_bank_undo))
            }
            TextButton(
                onClick = { onEvent(QuestionDetailEvent.CandidatesCleared) },
                enabled = uiState.canSelectChoice && hasSelection,
            ) {
                Text(stringResource(R.string.feed_word_bank_clear))
            }
        }

        CandidateTiles(uiState = uiState, length = length, answer = answer, onEvent = onEvent)

        SubmitAnswerSection(
            selectedChoiceId = answer.takeIf { it.isNotBlank() },
            submission = uiState.submission,
            enabled = uiState.canSubmitAnswer(answer),
            onSubmitAnswerClick = { onEvent(QuestionDetailEvent.AnswerSubmitted(it)) },
        )
    }
}

@Composable
private fun CandidateTiles(
    uiState: QuestionDetailUiState.Content,
    length: Int,
    answer: String,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val used = answer.codePointCount(0, answer.length)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceS),
        maxItemsInEachRow = TILES_PER_ROW,
    ) {
        // Keyed by index, not by text: a word bank legitimately repeats the same character.
        uiState.detail.answerCandidates.forEachIndexed { index, candidate ->
            key(index) {
                val selected = index in uiState.selectedCandidateIndices
                val candidateLength = candidate.codePointCount(0, candidate.length)
                val fits = used + candidateLength <= length
                val singleSlotSwap = length == 1 && candidateLength == 1

                FilterChip(
                    selected = selected,
                    onClick = { onEvent(QuestionDetailEvent.CandidateToggled(index)) },
                    enabled = uiState.canSelectChoice && (selected || fits || singleSlotSwap),
                    label = { Text(candidate, style = MaterialTheme.typography.titleLarge) },
                    modifier = Modifier.sizeIn(minWidth = TileMinWidth, minHeight = TileMinHeight),
                )
            }
        }
    }
}

private const val TILES_PER_ROW = 8
private val TileMinWidth = 56.dp
private val TileMinHeight = 48.dp
