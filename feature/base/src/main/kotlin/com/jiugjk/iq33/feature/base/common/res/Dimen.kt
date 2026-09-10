package com.jiugjk.iq33.feature.base.common.res

import androidx.compose.ui.unit.dp

/**
 * Spacing steps, on Material's 8dp grid with a 4dp half-step.
 *
 * Every gap and inset in the app comes from this list, so vertical rhythm stays consistent across
 * screens that were written months apart. The names are sizes rather than roles on purpose - a
 * "spaceCardInset" would be right until the one place it isn't.
 */
object Dimen {
    /** 4dp - the half-step, for gaps inside a single component (icon to its label). */
    val spaceS = 4.dp

    /** 8dp - between closely related elements. */
    val spaceM = 8.dp

    /** 12dp - between list items, and inside dense cards. */
    val spaceML = 12.dp

    /** 16dp - the default screen inset and the gap between sections. */
    val spaceL = 16.dp

    /** 24dp - between groups that should read as separate. */
    val spaceXL = 24.dp

    /** 32dp - around a screen-level empty or error state. */
    val spaceXXL = 32.dp

    /** Minimum touch target, per the Material accessibility guidance. */
    val touchTarget = 48.dp

    val imageSize = 96.dp
}
