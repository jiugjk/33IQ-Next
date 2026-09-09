package com.jiugjk.iq33.feature.base.presentation.compose.composable

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiugjk.iq33.feature.base.common.res.Dimen

/*
 * Skeleton placeholders for content that is loading.
 *
 * A skeleton beats a spinner here because the shape of what is coming is known - a list of question
 * cards - so the layout does not jump when the real thing arrives. Everything in here is decorative:
 * it is cleared from the accessibility tree, so a screen reader hears the screen's own loading state
 * rather than a series of empty boxes.
 */

/** One pulsing block, the primitive the skeleton layouts are built from. */
@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SKELETON_PULSE_MILLIS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeletonAlpha",
    )

    Box(
        modifier =
            modifier
                .width(width)
                .height(height)
                .clip(MaterialTheme.shapes.small)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** A stand-in for one question card, matching its real padding and text rhythm. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(Dimen.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimen.spaceM),
        ) {
            SkeletonBlock(width = TitleLineWideWidth, height = TextLineHeight)
            SkeletonBlock(width = TitleLineNarrowWidth, height = TextLineHeight)

            Row(horizontalArrangement = Arrangement.spacedBy(Dimen.spaceM)) {
                SkeletonBlock(width = ChipWidth, height = ChipHeight)
                SkeletonBlock(width = ChipWidth, height = ChipHeight)
            }
        }
    }
}

/** A full screen of [SkeletonCard]s, for a list that has nothing to show yet. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    itemCount: Int = DEFAULT_SKELETON_ITEMS,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Dimen.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimen.spaceML),
        // Placeholders must never scroll: there is nothing underneath them to scroll to.
        userScrollEnabled = false,
    ) {
        items(itemCount) { SkeletonCard() }
    }
}

private const val SKELETON_ALPHA_MIN = 0.35f
private const val SKELETON_ALPHA_MAX = 0.75f
private const val SKELETON_PULSE_MILLIS = 750
private const val DEFAULT_SKELETON_ITEMS = 6

private val TextLineHeight = 18.dp
private val TitleLineWideWidth = 260.dp
private val TitleLineNarrowWidth = 180.dp
private val ChipWidth = 64.dp
private val ChipHeight = 22.dp

@Preview
@Composable
private fun SkeletonCardPreview() {
    SkeletonCard()
}
