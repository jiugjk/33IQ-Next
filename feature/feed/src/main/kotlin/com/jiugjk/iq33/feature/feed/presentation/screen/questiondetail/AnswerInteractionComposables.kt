package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.annotation.StringRes
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
        SubmissionState.Idle, is SubmissionState.Submitting -> Unit
    }
}

@Composable
private fun SubmissionDoneText(result: SubmitAnswerResult) {
    when (result) {
        is SubmitAnswerResult.Correct ->
            Text(
                text =
                    scoreText(
                        R.string.feed_submission_correct,
                        R.string.feed_submission_correct_no_score,
                        result.scoreDelta,
                        result.myScore,
                    ),
                color = CorrectColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        is SubmitAnswerResult.Wrong ->
            Text(
                text =
                    scoreText(
                        R.string.feed_submission_wrong,
                        R.string.feed_submission_wrong_no_score,
                        result.scoreDelta,
                        result.myScore,
                    ),
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
    canReveal: Boolean,
    onEvent: (QuestionDetailEvent) -> Unit,
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
                onClick = { onEvent(QuestionDetailEvent.HintQuoteRequested) },
                enabled = canReveal && hintReveal !is RevealState.QuoteLoading && hintReveal !is RevealState.Revealing,
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
            onConfirm = { onEvent(QuestionDetailEvent.HintRevealConfirmed) },
            onDismiss = { onEvent(QuestionDetailEvent.RevealFlowDismissed(RevealKind.HINT)) },
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
            // Only the effective cost is guaranteed; 33IQ may omit a membership tier, and inventing
            // a 0 for a missing tier would advertise a free hint that does not exist.
            val tiers = listOfNotNull(quote.normalCost, quote.memberCost, quote.lifeMemberCost)
            val message =
                if (tiers.size == ALL_MEMBERSHIP_TIERS) {
                    stringResource(
                        R.string.feed_reveal_hint_confirm_message,
                        quote.normalCost ?: 0,
                        quote.memberCost ?: 0,
                        quote.lifeMemberCost ?: 0,
                        quote.effectiveCost,
                    )
                } else {
                    stringResource(R.string.feed_reveal_hint_confirm_message_simple, quote.effectiveCost)
                }

            Text(message)
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.feed_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_dialog_cancel)) } },
    )
}

@Composable
internal fun AnswerSection(
    fallbackAnalysis: String?,
    answerReveal: RevealState<Unit, AnswerReveal>,
    canReveal: Boolean,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    if (answerReveal is RevealState.Revealed) {
        AnswerRevealedCard(answerReveal.reveal)
    } else {
        Column {
            if (fallbackAnalysis != null) {
                AnalysisSection(analysis = fallbackAnalysis)
            }

            OutlinedButton(
                onClick = { onEvent(QuestionDetailEvent.AnswerRevealRequested) },
                enabled = canReveal && answerReveal !is RevealState.QuoteLoading && answerReveal !is RevealState.Revealing,
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
        AnswerConfirmDialog(
            onConfirm = { onEvent(QuestionDetailEvent.AnswerRevealConfirmed) },
            onDismiss = { onEvent(QuestionDetailEvent.RevealFlowDismissed(RevealKind.ANSWER)) },
        )
    }
}

@Composable
private fun AnswerRevealedCard(reveal: AnswerReveal) {
    Card(
        modifier = Modifier.padding(top = Dimen.spaceL).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Text(
                text = stringResource(R.string.feed_reveal_answer_result_title, reveal.answer),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = reveal.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
            val cost = reveal.cost
            val costText =
                when {
                    reveal.alreadyPaid -> stringResource(R.string.feed_reveal_answer_cost_already_paid)
                    // 33IQ did not report a parseable amount - saying "0 学识" would be a guess.
                    cost == null -> stringResource(R.string.feed_reveal_answer_cost_unknown)
                    else -> stringResource(R.string.feed_reveal_answer_cost_spent, cost)
                }
            Text(
                text = costText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }
    }
}

@Composable
private fun AnswerConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_reveal_answer_confirm_title)) },
        text = { Text(stringResource(R.string.feed_reveal_answer_confirm_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.feed_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_dialog_cancel)) } },
    )
}

/**
 * Uses the numbers-included wording only when 33IQ actually reported both figures - a missing
 * 学识 field means "not reported", not a change of zero.
 */
@Composable
private fun scoreText(
    @StringRes withScoreRes: Int,
    @StringRes withoutScoreRes: Int,
    scoreDelta: Int?,
    myScore: Int?,
): String =
    if (scoreDelta != null && myScore != null) {
        stringResource(withScoreRes, scoreDelta, myScore)
    } else {
        stringResource(withoutScoreRes)
    }

/** 普通 / 会员 / 终身会员 - the three tiers `showtipsbuy` quotes. */
private const val ALL_MEMBERSHIP_TIERS = 3

@Suppress("MagicNumber")
private val CorrectColor = Color(0xFF2E7D32)

@Composable
internal fun CommentsLockedNotice() {
    Text(
        text = stringResource(R.string.feed_comments_locked),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimen.spaceL),
    )
}
