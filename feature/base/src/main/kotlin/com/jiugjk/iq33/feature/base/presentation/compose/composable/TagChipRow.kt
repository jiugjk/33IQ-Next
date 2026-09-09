package com.jiugjk.iq33.feature.base.presentation.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen

/**
 * A question's tags as Material chips.
 *
 * FlowRow rather than a scrolling Row: on a card the tags have to be visible without interaction,
 * and a horizontal scroller inside a vertically scrolling list fights the gesture. Wrapping is also
 * what keeps a long tag readable instead of squeezing it to one character per line.
 *
 * The chips are display-only. They carry no onClick because tapping one would have to navigate away
 * from the question the card is about, which is not what a tap on a card should do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimen.spaceM),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
    ) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = { },
                enabled = false,
                label = { Text(tag, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                shape = MaterialTheme.shapes.small,
                border = null,
                colors =
                    SuggestionChipDefaults.suggestionChipColors(
                        // A disabled chip would otherwise read as "unavailable" rather than as a
                        // label, so the disabled colours are set back to the enabled container's.
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                modifier = Modifier.height(ChipHeight),
            )
        }
    }
}

private val ChipHeight = 26.dp

@Preview
@Composable
private fun TagChipRowPreview() {
    TagChipRow(tags = listOf("侦探推理", "恐怖推理", "逻辑思维"))
}
