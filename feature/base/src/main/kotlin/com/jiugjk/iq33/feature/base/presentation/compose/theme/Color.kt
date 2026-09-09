package com.jiugjk.iq33.feature.base.presentation.compose.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The app's own colour scheme, used wherever Material You dynamic colour is unavailable - every
 * device below Android 12, and any build where the user's wallpaper palette cannot be read.
 *
 * These are full Material 3 schemes rather than a handful of brand colours: the UI is built on the
 * semantic roles (surfaceContainer* for elevation, primaryContainer for selection, outlineVariant
 * for dividers), so a scheme that left them to Material's defaults would look flat next to the
 * dynamic one. Tones follow M3's own tonal-palette positions - 40/90/10 for light, 80/30/90 for
 * dark - around an indigo-violet seed.
 */

private val BrandPrimaryLight = Color(0xFF4F46B8)
private val BrandPrimaryDark = Color(0xFFC5C0FF)

internal val BrandLightColorScheme =
    lightColorScheme(
        primary = BrandPrimaryLight,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE4DFFF),
        onPrimaryContainer = Color(0xFF100764),
        inversePrimary = BrandPrimaryDark,
        secondary = Color(0xFF5C5B71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE2DFF9),
        onSecondaryContainer = Color(0xFF1A192C),
        tertiary = Color(0xFF7B5265),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD8E7),
        onTertiaryContainer = Color(0xFF2F1121),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFDFBFF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFCF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE4E1EC),
        onSurfaceVariant = Color(0xFF47464F),
        surfaceTint = BrandPrimaryLight,
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF3EFF7),
        outline = Color(0xFF787680),
        outlineVariant = Color(0xFFC8C5D0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFCF8FF),
        surfaceDim = Color(0xFFDCD9E0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F2FA),
        surfaceContainer = Color(0xFFF0ECF4),
        surfaceContainerHigh = Color(0xFFEAE7EF),
        surfaceContainerHighest = Color(0xFFE5E1E9),
    )

internal val BrandDarkColorScheme =
    darkColorScheme(
        primary = BrandPrimaryDark,
        onPrimary = Color(0xFF1F1287),
        primaryContainer = Color(0xFF372E9F),
        onPrimaryContainer = Color(0xFFE4DFFF),
        inversePrimary = BrandPrimaryLight,
        secondary = Color(0xFFC6C3DC),
        onSecondary = Color(0xFF2F2E42),
        secondaryContainer = Color(0xFF454459),
        onSecondaryContainer = Color(0xFFE2DFF9),
        tertiary = Color(0xFFECB8CE),
        onTertiary = Color(0xFF482536),
        tertiaryContainer = Color(0xFF613B4D),
        onTertiaryContainer = Color(0xFFFFD8E7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE5E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE5E1E9),
        surfaceVariant = Color(0xFF47464F),
        onSurfaceVariant = Color(0xFFC8C5D0),
        surfaceTint = BrandPrimaryDark,
        inverseSurface = Color(0xFFE5E1E9),
        inverseOnSurface = Color(0xFF303036),
        outline = Color(0xFF928F9A),
        outlineVariant = Color(0xFF47464F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF39383F),
        surfaceDim = Color(0xFF131318),
        surfaceContainerLowest = Color(0xFF0E0E13),
        surfaceContainerLow = Color(0xFF1B1B21),
        surfaceContainer = Color(0xFF1F1F25),
        surfaceContainerHigh = Color(0xFF2A2930),
        surfaceContainerHighest = Color(0xFF35343B),
    )
