package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.ErrorAnim
import com.jiugjk.iq33.feature.base.presentation.compose.composable.LoadingIndicator
import com.jiugjk.iq33.feature.feed.R
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

    // load() is idempotent for a question that is already loaded, so re-running this effect after a
    // configuration change keeps the answer/hint state instead of resetting it - see the view model.
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
                    val context = LocalContext.current

                    IconButton(onClick = { copyQuestion(context, currentUiState.detail) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.feed_copy_content_description),
                        )
                    }

                    IconButton(onClick = { shareQuestion(context, currentUiState.detail) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.feed_share_content_description),
                        )
                    }

                    IconButton(
                        onClick = { viewModel.onEvent(QuestionDetailEvent.BookmarkToggled) },
                        enabled = !currentUiState.isBookmarkChanging,
                    ) {
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
            QuestionDetailUiState.Error -> LoadErrorContent(onRetryClick = { viewModel.onEvent(QuestionDetailEvent.RetryRequested) })
            is QuestionDetailUiState.Content -> QuestionDetailContent(uiState = currentUiState, onEvent = viewModel::onEvent)
        }
    }
}

@Composable
private fun LoadErrorContent(onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ErrorAnim()

        Button(onClick = onRetryClick, modifier = Modifier.padding(top = Dimen.spaceL)) {
            Text(stringResource(R.string.feed_retry))
        }
    }
}

@Composable
private fun QuestionDetailContent(
    uiState: QuestionDetailUiState.Content,
    onEvent: (QuestionDetailEvent) -> Unit,
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

        // Only the rare question that really has a 33IQ title gets a heading. Ordinary ones have
        // none, and the truncated body that used to stand in for it just repeated the question -
        // cut off mid-sentence - directly above the full text. See QuestionDetail.title.
        detail.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Dimen.spaceM),
            )
        }

        AuthorRow(detail)

        if (detail.tags.isNotEmpty()) {
            TagRow(tags = detail.tags)
        }

        StatsRow(detail = detail, isPraising = uiState.isPraising, onPraiseClick = { onEvent(QuestionDetailEvent.PraiseClicked) })

        QuestionBody(bodyText = detail.bodyText, imageUrls = detail.imageUrls)

        if (uiState.bookmarkFailed) {
            Text(
                text = stringResource(R.string.feed_bookmark_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimen.spaceS),
            )
        }

        AnswerSections(uiState = uiState, onEvent = onEvent)
    }
}

/**
 * Everything below the question itself: where the answer is given, the paid hint and answer, and
 * the comments that go with them.
 */
@Composable
private fun AnswerSections(
    uiState: QuestionDetailUiState.Content,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    val detail = uiState.detail

    if (detail.questionType == QuestionType.CHOICE && detail.choices.isNotEmpty()) {
        ChoiceAndSubmitSection(detail = detail, uiState = uiState, onEvent = onEvent)
    } else if (detail.questionType == QuestionType.OPEN) {
        OpenAnswerSection(
            draftAnswer = uiState.draftAnswer,
            submission = uiState.submission,
            enabled = uiState.canSelectChoice,
            onDraftChange = { text -> onEvent(QuestionDetailEvent.DraftAnswerChanged(text)) },
            onSubmitAnswerClick = { answer -> onEvent(QuestionDetailEvent.AnswerSubmitted(answer)) },
        )
    }

    HintSection(hintReveal = uiState.hintReveal, canStartHintReveal = uiState.canStartHintReveal, onEvent = onEvent)

    AnswerSection(
        fallbackAnalysis = detail.analysis,
        answerReveal = uiState.answerReveal,
        canStartAnswerReveal = uiState.canStartAnswerReveal,
        onEvent = onEvent,
    )

    // 33IQ hides a question's comments (they routinely spoil the answer) until the real answer
    // has been revealed - matches the official app's own behaviour, not a client limitation.
    if (uiState.isAnswerRevealed) {
        CommentSection(comments = detail.comments, commentCount = detail.commentCount)
    } else {
        CommentsLockedNotice()
    }
}

@Composable
private fun ChoiceAndSubmitSection(
    detail: QuestionDetail,
    uiState: QuestionDetailUiState.Content,
    onEvent: (QuestionDetailEvent) -> Unit,
) {
    // Choices stay tappable only while a submission could still be sent: once one is in flight the
    // selection is frozen, so the result can never be shown next to a different option.
    ChoiceSection(
        choices = detail.choices,
        selectedChoiceId = uiState.selectedChoiceId,
        enabled = uiState.canSelectChoice,
        onChoiceSelect = { choiceId -> onEvent(QuestionDetailEvent.ChoiceSelected(choiceId)) },
    )

    SubmitAnswerSection(
        selectedChoiceId = uiState.selectedChoiceId,
        submission = uiState.submission,
        enabled = uiState.canSelectChoice,
        onSubmitAnswerClick = { choiceId -> onEvent(QuestionDetailEvent.AnswerSubmitted(choiceId)) },
    )
}

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
