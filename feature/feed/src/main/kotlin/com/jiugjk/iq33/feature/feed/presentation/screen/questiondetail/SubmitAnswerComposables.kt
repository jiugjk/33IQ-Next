package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult

@Composable
internal fun SubmitAnswerSection(
    selectedChoiceId: String?,
    submission: SubmissionState,
    enabled: Boolean,
    onSubmitAnswerClick: (String) -> Unit,
    onRedoClick: (() -> Unit)? = null,
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

            if (submission is SubmissionState.Done && onRedoClick != null) {
                TextButton(onClick = onRedoClick, modifier = Modifier.padding(start = Dimen.spaceM)) {
                    Text(stringResource(R.string.feed_redo_question))
                }
            }
        }

        SubmissionResultText(submission)
    }
}

@Suppress("LongParameterList")
@Composable
internal fun OpenAnswerSection(
    draftAnswer: String,
    submission: SubmissionState,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmitAnswerClick: (String) -> Unit,
    onRedoClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        OutlinedTextField(
            value = draftAnswer,
            onValueChange = onDraftChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text(stringResource(R.string.feed_open_answer_label)) },
        )

        SubmitAnswerSection(
            selectedChoiceId = draftAnswer.takeIf { it.isNotBlank() },
            submission = submission,
            enabled = enabled,
            onSubmitAnswerClick = onSubmitAnswerClick,
            onRedoClick = onRedoClick,
        )
    }
}

@Composable
private fun SubmissionResultText(submission: SubmissionState) {
    when (submission) {
        is SubmissionState.Done -> SubmissionDoneText(submission.submittedAnswer, submission.result)
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
private fun SubmissionDoneText(
    submittedAnswer: String,
    result: SubmitAnswerResult,
) {
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
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        is SubmitAnswerResult.Wrong ->
            Text(
                text =
                    if (result.scoreDelta != null && result.myScore != null) {
                        scoreText(
                            R.string.feed_submission_wrong,
                            R.string.feed_submission_wrong_no_score,
                            result.scoreDelta,
                            result.myScore,
                        )
                    } else if (submittedAnswer.isNotBlank()) {
                        stringResource(R.string.feed_submission_wrong_choice, submittedAnswer)
                    } else {
                        stringResource(R.string.feed_submission_wrong_no_score)
                    },
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
        SubmitAnswerResult.AnswerAlreadyViewed ->
            Text(
                text = stringResource(R.string.feed_submission_answer_viewed),
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
