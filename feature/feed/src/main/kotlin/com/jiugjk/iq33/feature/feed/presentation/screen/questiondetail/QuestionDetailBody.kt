package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.TagChipRow
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionContentBlock
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer.ImageViewerDialog

/*
 * The read-only parts of the question detail screen: the question itself and the metadata around it.
 * Kept apart from QuestionDetailScreen so that file stays about state handling and wiring.
 */

/**
 * The question's own text and images - for almost every 33IQ question this *is* the question, since
 * the site gives ordinary questions no title at all (see [QuestionDetail.title]).
 *
 * Tapping an image opens the full-screen viewer at that image, with the whole list handed over so
 * the viewer can page between them. For a question whose picture *is* the puzzle, being able to
 * zoom into it is the difference between solvable and not.
 */
@Composable
internal fun QuestionBody(
    bodyText: String,
    imageUrls: List<String>,
    bodyBlocks: List<QuestionContentBlock> = emptyList(),
) {
    var viewerIndex by remember(imageUrls) { mutableStateOf<Int?>(null) }
    val gallery = imageUrls.ifEmpty { bodyBlocks.filterIsInstance<QuestionContentBlock.Image>().map { it.url }.distinct() }

    val blocks =
        bodyBlocks.ifEmpty {
            buildList {
                if (bodyText.isNotBlank()) add(QuestionContentBlock.Text(bodyText))
                imageUrls.forEach { add(QuestionContentBlock.Image(it)) }
            }
        }

    blocks.forEach { block ->
        when (block) {
            is QuestionContentBlock.Text -> {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Dimen.spaceL),
                )
            }
            is QuestionContentBlock.Image -> {
                val index = gallery.indexOf(block.url).takeIf { it >= 0 } ?: 0

                AsyncImage(
                    model = block.url,
                    contentDescription = stringResource(R.string.feed_question_image_content_description),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimen.spaceM)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { viewerIndex = index },
                )
            }
        }
    }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            imageUrls = gallery,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
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
    // The same chip row the list cards use, so a tag looks identical wherever it appears. It wraps
    // rather than scrolls: on the detail screen there is room, and a horizontal scroller nested in
    // the vertically scrolling page fights the gesture.
    TagChipRow(
        tags = tags,
        modifier = Modifier.padding(top = Dimen.spaceML),
    )
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
            // /index/praise toggles, so this stays tappable once liked; the whole row is the
            // target and it is held to the 48dp minimum rather than just the icon's own size.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isPraising, onClick = onPraiseClick)
                        .defaultMinSize(minHeight = Dimen.touchTarget)
                        .padding(horizontal = Dimen.spaceM),
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = stringResource(R.string.feed_praise_content_description),
                    tint = if (detail.isUpvoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(StatIconSize),
                )
                Text(
                    text = detail.upvoteCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (detail.isUpvoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Dimen.spaceM),
                )
            }

            StatText(
                icon = Icons.Default.ChatBubbleOutline,
                value = detail.commentCount,
                contentDescription = stringResource(R.string.feed_comment_count_content_description),
            )
            StatText(
                icon = Icons.Default.Star,
                value = detail.collectCount,
                contentDescription = stringResource(R.string.feed_collect_count_content_description),
            )
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
private fun StatText(
    icon: ImageVector,
    value: Int,
    contentDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(StatIconSize),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimen.spaceM),
        )
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
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = Dimen.spaceL, vertical = Dimen.spaceML),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Dimen.touchTarget)
                        .padding(vertical = Dimen.spaceS),
            ) {
                Text(
                    text = choice.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val SelectedChoiceBorderWidth = 2.dp
private val StatIconSize = 18.dp
