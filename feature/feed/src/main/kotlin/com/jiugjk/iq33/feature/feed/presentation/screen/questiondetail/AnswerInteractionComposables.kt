package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.AnswerQuote
import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.HintQuote
import com.jiugjk.iq33.feature.feed.domain.model.HintReveal
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

@Composable
internal fun SubmitAnswerSection(
    selectedChoiceId: String?,
    submission: SubmissionState,
    enabled: Boolean,
    onSubmitAnswerClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { selectedChoiceId?.let(onSubmitAnswerClick) },
                enabled = enabled && selectedChoiceId != null && submission !is SubmissionState.Submitting,
            ) {
                Text(stringResource(R.string.feed_submit_answer))
            }

            if (submission is SubmissionState.Submitting) {
                CircularProgressIndicator(modifier = Modifier.padding(start = Dimen.spaceM).size(20.dp))
            }
        }

        SubmissionResultText(submission)
    }
}

@Composable
private fun SubmissionResultText(submission: SubmissionState) {
    when (submission) {
        is SubmissionState.Done -> SubmissionDoneText(submission.result)
        SubmissionState.Failed ->
            Text(
                text = stringResource(R.string.feed_submission_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        SubmissionState.Idle, SubmissionState.Submitting -> Unit
    }
}

@Composable
private fun SubmissionDoneText(result: SubmitAnswerResult) {
    when (result) {
        is SubmitAnswerResult.Correct ->
            Text(
                text = stringResource(R.string.feed_submission_correct, result.scoreDelta, result.myScore),
                color = CorrectColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        is SubmitAnswerResult.Wrong ->
            Text(
                text = stringResource(R.string.feed_submission_wrong, result.scoreDelta, result.myScore),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        SubmitAnswerResult.AlreadyAnswered ->
            Text(
                text = stringResource(R.string.feed_submission_already_answered),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        SubmitAnswerResult.LimitReached ->
            Text(
                text = stringResource(R.string.feed_submission_limit_reached),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
    }
}

@Composable
internal fun HintSection(
    hintReveal: RevealState<HintQuote, HintReveal>,
    onRevealHintClick: () -> Unit,
    onConfirmRevealHint: () -> Unit,
    onDismissHintFlow: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        if (hintReveal is RevealState.Revealed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = hintReveal.reveal.tips,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Dimen.spaceL),
                )
            }
        } else {
            OutlinedButton(
                onClick = onRevealHintClick,
                enabled = hintReveal !is RevealState.QuoteLoading && hintReveal !is RevealState.Revealing,
            ) {
                Text(stringResource(R.string.feed_reveal_hint_button))
            }
        }

        if (hintReveal is RevealState.Failed) {
            Text(
                text = stringResource(R.string.feed_flow_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }
    }

    if (hintReveal is RevealState.QuoteReady) {
        HintQuoteDialog(
            quote = hintReveal.quote,
            onConfirm = onConfirmRevealHint,
            onDismiss = onDismissHintFlow,
        )
    }
}

@Composable
private fun HintQuoteDialog(
    quote: HintQuote,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_reveal_hint_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.feed_reveal_hint_confirm_message,
                    quote.normalCost,
                    quote.memberCost,
                    quote.lifeMemberCost,
                    quote.effectiveCost,
                ),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.feed_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_dialog_cancel)) } },
    )
}

@Composable
internal fun AnswerSection(
    fallbackAnalysis: String?,
    answerReveal: RevealState<AnswerQuote, AnswerReveal>,
    onRevealAnswerClick: () -> Unit,
    onConfirmRevealAnswer: () -> Unit,
    onDismissAnswerFlow: () -> Unit,
) {
    if (answerReveal is RevealState.Revealed) {
        Card(
            modifier = Modifier.padding(top = Dimen.spaceL).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(Dimen.spaceL)) {
                Text(
                    text = stringResource(R.string.feed_reveal_answer_result_title, answerReveal.reveal.answer),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = answerReveal.reveal.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Dimen.spaceS),
                )
            }
        }
    } else {
        Column {
            AnalysisSection(analysis = fallbackAnalysis)

            OutlinedButton(
                onClick = onRevealAnswerClick,
                enabled = answerReveal !is RevealState.QuoteLoading && answerReveal !is RevealState.Revealing,
                modifier = Modifier.padding(top = Dimen.spaceS),
            ) {
                Text(stringResource(R.string.feed_reveal_answer_button))
            }

            if (answerReveal is RevealState.Failed) {
                Text(
                    text = stringResource(R.string.feed_flow_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimen.spaceS),
                )
            }
        }
    }

    if (answerReveal is RevealState.QuoteReady) {
        AnswerQuoteDialog(
            quote = answerReveal.quote,
            onConfirm = onConfirmRevealAnswer,
            onDismiss = onDismissAnswerFlow,
        )
    }
}

@Composable
private fun AnswerQuoteDialog(
    quote: AnswerQuote,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_reveal_answer_confirm_title)) },
        text = {
            val message =
                if (quote.alreadyPaid) {
                    stringResource(R.string.feed_reveal_answer_confirm_message_free)
                } else {
                    stringResource(R.string.feed_reveal_answer_confirm_message_cost, quote.cost)
                }
            Text(message)
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.feed_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_dialog_cancel)) } },
    )
}

@Suppress("MagicNumber")
private val CorrectColor = Color(0xFF2E7D32)
