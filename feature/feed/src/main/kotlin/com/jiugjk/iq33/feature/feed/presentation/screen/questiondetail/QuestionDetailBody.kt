package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail

/*
 * The read-only parts of the question detail screen: the question itself and the metadata around it.
 * Kept apart from QuestionDetailScreen so that file stays about state handling and wiring.
 */

/**
 * The question's own text and images - for almost every 33IQ question this *is* the question, since
 * the site gives ordinary questions no title at all (see [QuestionDetail.title]).
 */
@Composable
internal fun QuestionBody(
    bodyText: String,
    imageUrls: List<String>,
) {
    if (bodyText.isNotBlank()) {
        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Dimen.spaceL),
        )
    }

    imageUrls.forEach { imageUrl ->
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(R.string.feed_question_image_content_description),
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimen.spaceM),
        )
    }
}

@Composable
internal fun AuthorRow(detail: QuestionDetail) {
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
internal fun TagRow(tags: List<String>) {
    // A plain fillMaxWidth Row squeezes the last chip into whatever space is left once the others
    // don't fit, wrapping its text one character per line - scrolling instead keeps every chip intact.
    Row(
        modifier =
            Modifier
                .padding(top = Dimen.spaceM)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS),
    ) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = { },
                label = { Text(tag, maxLines = 1) },
                colors = SuggestionChipDefaults.suggestionChipColors(),
            )
        }
    }
}

@Composable
internal fun StatsRow(
    detail: QuestionDetail,
    isPraising: Boolean,
    onPraiseClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = Dimen.spaceL)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimen.spaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // /index/praise toggles (点赞/取消点赞) - only guard against a double-tap firing two
            // overlapping requests, don't disable once liked.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isPraising, onClick = onPraiseClick)
                        .padding(horizontal = Dimen.spaceS, vertical = Dimen.spaceS),
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = stringResource(R.string.feed_praise_content_description),
                    tint = if (detail.isUpvoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = detail.upvoteCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (detail.isUpvoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Dimen.spaceS),
                )
            }

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
internal fun ChoiceSection(
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
