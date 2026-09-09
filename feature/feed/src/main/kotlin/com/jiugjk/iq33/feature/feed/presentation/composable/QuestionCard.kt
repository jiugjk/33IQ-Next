package com.jiugjk.iq33.feature.feed.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.base.presentation.compose.composable.TagChipRow
import com.jiugjk.iq33.feature.feed.R
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

/**
 * One question in a list.
 *
 * @param question the list item to render.
 * @param onClick opens the question.
 * @param modifier layout for the card.
 * @param hiddenTag a tag to leave off this card. The feed passes the category being browsed: inside
 *   「对联大全」every question carries the 对联大全 tag, so printing it on all of them is noise. Null
 *   (the default) shows every tag, which is what the bookmark and search lists want - there the tag
 *   is the only clue to where a question came from.
 */
@Composable
fun QuestionCard(
    question: QuestionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hiddenTag: String? = null,
) {
    val tags = question.tags.filterNot { tag -> tag == hiddenTag }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        // surfaceContainerLow, not surface: it separates the card from the background by tone rather
        // than by a shadow, which is what M3 asks for and what stops a list of large white slabs.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (tags.isNotEmpty()) {
                TagChipRow(
                    tags = tags,
                    modifier = Modifier.padding(top = Dimen.spaceML),
                )
            }

            Row(
                modifier = Modifier.padding(top = Dimen.spaceML),
                horizontalArrangement = Arrangement.spacedBy(Dimen.spaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatItem(
                    icon = Icons.Default.ThumbUp,
                    count = question.upvoteCount,
                    contentDescription = stringResource(R.string.feed_upvote_count_content_description),
                )
                StatItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    count = question.commentCount,
                    contentDescription = stringResource(R.string.feed_comment_count_content_description),
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    count: Int,
    contentDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimen.spaceS)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(StatIconSize),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val StatIconSize = 16.dp

@Preview
@Composable
private fun QuestionCardPreview() {
    QuestionCard(
        question =
            QuestionSummary(
                id = 1,
                title = "男子被发现死在自家浴室，现场没有任何外人进入的痕迹。",
                tags = listOf("侦探推理", "恐怖推理"),
                upvoteCount = 128,
                commentCount = 43,
            ),
        onClick = { },
    )
}
