package com.jiugjk.iq33.feature.feed.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.domain.model.QuestionSummary

@Composable
fun QuestionCard(
    question: QuestionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimen.spaceL)) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (question.tags.isNotEmpty()) {
                Text(
                    text = question.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Dimen.spaceS),
                )
            }

            Row(
                modifier = Modifier.padding(top = Dimen.spaceM),
                horizontalArrangement = Arrangement.spacedBy(Dimen.spaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatItem(icon = Icons.Default.ThumbUp, count = question.upvoteCount)
                StatItem(icon = Icons.Default.ChatBubbleOutline, count = question.commentCount)
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
