package com.jiugjk.iq33.feature.base.presentation.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3's corner-radius scale, spelled out so every surface picks a step from one list rather
 * than inventing a radius at the call site.
 *
 * The steps map to what they are for: extraSmall for dense chrome, small for chips, medium for list
 * cards, large for sheets and prominent cards, extraLarge for the fully rounded shapes M3 uses on
 * dialogs and large containers.
 */
internal val Iq33Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
