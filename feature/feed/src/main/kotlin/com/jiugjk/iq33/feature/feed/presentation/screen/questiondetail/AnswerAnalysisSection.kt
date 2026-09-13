package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal

/**
 * The 查看解析 flow: quote, explicit confirmation, then content.
 *
 * Nothing here starts a charged request on its own - opening this section only ever asks for a
 * price, and the reveal happens after the dialog is confirmed.
 */
@Composable
internal fun AnswerAnalysisSection(
    uiState: QuestionDetailUiState.Content,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val state = uiState.answerReveal

    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimen.spaceL)) {
        if (state is RevealState.Revealed) {
            AnswerAnalysisContent(state.reveal)
        } else {
            AnswerAnalysisPrompt(uiState = uiState, onEvent = onEvent)
        }
    }

    if (state is RevealState.QuoteReady) {
        AnswerQuoteDialog(
            quote = state.quote,
            confirmEnabled = uiState.canConfirmAnswerReveal,
            onConfirm = { onEvent(QuestionDetailEvent.AnswerRevealConfirmed) },
            onDismiss = { onEvent(QuestionDetailEvent.AnswerFlowDismissed) },
        )
    }
}

@Composable
private fun AnswerAnalysisPrompt(
    uiState: QuestionDetailUiState.Content,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val state = uiState.answerReveal
    // A request that may already have been charged is only ever followed by a fetch-only recovery.
    val recovery = uiState.detail.isAnswerRevealPending || state.isRetryBlocked
    val busy = state is RevealState.QuoteLoading || state is RevealState.Revealing
    val notice =
        when {
            recovery -> R.string.feed_answer_pending_notice
            state is RevealState.Failed -> R.string.feed_answer_flow_failed
            else -> null
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnswerRevealButton(uiState = uiState, recovery = recovery, onEvent = onEvent)

        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
            Text(stringResource(R.string.feed_answer_loading), style = MaterialTheme.typography.bodySmall)
        }

        notice?.let { message ->
            Text(
                text = stringResource(message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnswerRevealButton(
    uiState: QuestionDetailUiState.Content,
    recovery: Boolean,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val event = if (recovery) QuestionDetailEvent.AnswerRevealRecovered else QuestionDetailEvent.AnswerQuoteRequested
    val enabled = if (recovery) uiState.canRecoverAnswerReveal else uiState.canStartAnswerReveal
    val label = if (recovery) R.string.feed_answer_recover else R.string.feed_reveal_answer_button

    OutlinedButton(onClick = { onEvent(event) }, enabled = enabled) {
        Text(stringResource(label))
    }
}

@Composable
private fun AnswerQuoteDialog(
    quote: AnswerQuote,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_reveal_answer_button)) },
        text = {
            Column {
                // The server's own figure, shown as-is: a 0 here is an entitlement, not a discount.
                Text(stringResource(R.string.feed_answer_quote_cost, quote.cost))
                Text(
                    text = stringResource(R.string.feed_answer_reveal_warning),
                    modifier = Modifier.padding(top = Dimen.spaceM),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(stringResource(R.string.feed_answer_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_dialog_cancel)) }
        },
    )
}

@Composable
private fun AnswerAnalysisContent(reveal: AnswerReveal) {
    val hasExplanation = reveal.explanationBlocks.isNotEmpty() || reveal.explanationText.isNotBlank()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Text(stringResource(R.string.feed_answer_content_title), style = MaterialTheme.typography.titleMedium)
            QuestionBody(reveal.answerText, imageUrls = emptyList(), bodyBlocks = reveal.answerBlocks)

            Text(
                text = stringResource(R.string.feed_analysis_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Dimen.spaceL),
            )

            if (hasExplanation) {
                QuestionBody(reveal.explanationText, imageUrls = emptyList(), bodyBlocks = reveal.explanationBlocks)
            } else {
                Text(stringResource(R.string.feed_analysis_missing))
            }
        }
    }
}

private val ProgressSize = 24.dp
