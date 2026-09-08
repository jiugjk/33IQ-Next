package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.ErrorAnim
import com.jiugjk.iq33.feature.base.presentation.compose.composable.LoadingIndicator
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.Comment
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailScreen(
    questionId: Long,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    val viewModel: QuestionDetailViewModel = koinViewModel()
    val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(questionId) {
        viewModel.load(questionId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feed_navigate_back),
                    )
                }
            },
            actions = {
                val currentUiState = uiState
                if (currentUiState is QuestionDetailUiState.Content) {
                    IconButton(onClick = { viewModel.onBookmarkClick(currentUiState.detail) }) {
                        Icon(
                            imageVector = if (currentUiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.feed_bookmark_content_description),
                        )
                    }
                }
            },
        )

        when (val currentUiState = uiState) {
            QuestionDetailUiState.Loading -> LoadingIndicator()
            QuestionDetailUiState.Error -> ErrorAnim()
            is QuestionDetailUiState.Content ->
                QuestionDetailContent(
                    uiState = currentUiState,
                    onChoiceSelect = viewModel::onChoiceSelected,
                    onSubmitAnswerClick = { viewModel.onSubmitAnswerClick(questionId, it) },
                    onRevealAnswerClick = { viewModel.onRevealAnswerClick(questionId) },
                    onConfirmRevealAnswer = { viewModel.onConfirmRevealAnswer(questionId) },
                    onDismissAnswerFlow = viewModel::onDismissAnswerFlow,
                    onRevealHintClick = { viewModel.onRevealHintClick(questionId) },
                    onConfirmRevealHint = { viewModel.onConfirmRevealHint(questionId) },
                    onDismissHintFlow = viewModel::onDismissHintFlow,
                )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun QuestionDetailContent(
    uiState: QuestionDetailUiState.Content,
    onChoiceSelect: (String) -> Unit,
    onSubmitAnswerClick: (String) -> Unit,
    onRevealAnswerClick: () -> Unit,
    onConfirmRevealAnswer: () -> Unit,
    onDismissAnswerFlow: () -> Unit,
    onRevealHintClick: () -> Unit,
    onConfirmRevealHint: () -> Unit,
    onDismissHintFlow: () -> Unit,
) {
    val detail = uiState.detail

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimen.spaceL),
    ) {
        if (detail.breadcrumb.isNotEmpty()) {
            Text(
                text = detail.breadcrumb.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = detail.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Dimen.spaceM),
        )

        AuthorRow(detail)

        if (detail.tags.isNotEmpty()) {
            TagRow(tags = detail.tags)
        }

        StatsRow(detail)

        if (detail.questionType == QuestionType.CHOICE && detail.choices.isNotEmpty()) {
            ChoiceAndSubmitSection(
                detail = detail,
                uiState = uiState,
                onChoiceSelect = onChoiceSelect,
                onSubmitAnswerClick = onSubmitAnswerClick,
            )
        }

        HintSection(
            hintReveal = uiState.hintReveal,
            onRevealHintClick = onRevealHintClick,
            onConfirmRevealHint = onConfirmRevealHint,
            onDismissHintFlow = onDismissHintFlow,
        )

        AnswerSection(
            fallbackAnalysis = detail.analysis,
            answerReveal = uiState.answerReveal,
            onRevealAnswerClick = onRevealAnswerClick,
            onConfirmRevealAnswer = onConfirmRevealAnswer,
            onDismissAnswerFlow = onDismissAnswerFlow,
        )

        // 33IQ hides a question's comments (they routinely spoil the answer) until the real answer
        // has been revealed - matches the official app's own behaviour, not a client limitation.
        if (uiState.answerReveal is RevealState.Revealed) {
            CommentSection(comments = detail.comments, commentCount = detail.commentCount)
        } else {
            CommentsLockedNotice()
        }
    }
}

@Composable
private fun ChoiceAndSubmitSection(
    detail: QuestionDetail,
    uiState: QuestionDetailUiState.Content,
    onChoiceSelect: (String) -> Unit,
    onSubmitAnswerClick: (String) -> Unit,
) {
    // Once the answer has been revealed or already submitted, 33IQ no longer accepts a scored
    // resubmission - the choice buttons and submit action are disabled to match.
    val canInteract = uiState.canSubmit && uiState.submission !is SubmissionState.Done

    ChoiceSection(
        choices = detail.choices,
        selectedChoiceId = uiState.selectedChoiceId,
        enabled = canInteract,
        onChoiceSelect = onChoiceSelect,
    )

    SubmitAnswerSection(
        selectedChoiceId = uiState.selectedChoiceId,
        submission = uiState.submission,
        enabled = canInteract,
        onSubmitAnswerClick = onSubmitAnswerClick,
    )
}

@Composable
private fun AuthorRow(detail: QuestionDetail) {
    if (detail.author != null || detail.publishedDate != null) {
        Text(
            text =
                listOfNotNull(detail.author, detail.publishedDate)
                    .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimen.spaceS),
        )
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    Row(
        modifier = Modifier.padding(top = Dimen.spaceM),
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS),
    ) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = { },
                label = { Text(tag) },
                colors = SuggestionChipDefaults.suggestionChipColors(),
            )
        }
    }
}

@Composable
private fun StatsRow(detail: QuestionDetail) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimen.spaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.ThumbUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail.upvoteCount.toString(), style = MaterialTheme.typography.bodyMedium)

            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(detail.commentCount.toString(), style = MaterialTheme.typography.bodyMedium)

            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail.collectCount.toString(), style = MaterialTheme.typography.bodyMedium)
        }

        if (detail.rightRatio != null) {
            Text(
                text = stringResource(R.string.feed_right_ratio, detail.rightRatio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }
    }
}

@Composable
private fun ChoiceSection(
    choices: List<Choice>,
    selectedChoiceId: String?,
    enabled: Boolean,
    onChoiceSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        choices.forEach { choice ->
            val isSelected = choice.id == selectedChoiceId

            OutlinedButton(
                onClick = { onChoiceSelect(choice.id) },
                enabled = enabled,
                colors =
                    if (isSelected) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                border =
                    if (isSelected) {
                        BorderStroke(SelectedChoiceBorderWidth, MaterialTheme.colorScheme.primary)
                    } else {
                        ButtonDefaults.outlinedButtonBorder(enabled)
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimen.spaceS),
            ) {
                Text(choice.text, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val SelectedChoiceBorderWidth = 2.dp

@Composable
internal fun AnalysisSection(analysis: String) {
    Card(
        modifier = Modifier.padding(top = Dimen.spaceL).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Text(
                text = stringResource(R.string.feed_analysis_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = analysis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }
    }
}

@Composable
private fun CommentSection(
    comments: List<Comment>,
    commentCount: Int,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        Text(
            text = stringResource(R.string.feed_comments_title, commentCount),
            style = MaterialTheme.typography.titleSmall,
        )

        if (comments.isEmpty()) {
            Text(
                text = stringResource(R.string.feed_comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        } else {
            comments.forEach { comment ->
                Column(modifier = Modifier.padding(top = Dimen.spaceM)) {
                    Text(
                        text = "${comment.author}  ${comment.time}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = comment.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Preview
@Composable
private fun AnalysisSectionPreview() {
    AnalysisSection(analysis = "示例解析文本")
}
