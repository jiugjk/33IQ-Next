package com.jiugjk.iq33.feature.base.presentation.compose.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's single theme entry point: colour scheme, type scale and shape scale in one place.
 *
 * Material You dynamic colour is used wherever the platform offers it (Android 12+) and the brand
 * schemes in [BrandLightColorScheme] / [BrandDarkColorScheme] stand in everywhere else, so the app
 * looks deliberate on a device that has no wallpaper palette to borrow rather than falling back to
 * Material's stock purple.
 *
 * NOTE: this is a plain MaterialTheme, not MaterialExpressiveTheme. material3 1.4.0 ships the
 * Expressive classes (MaterialExpressiveTheme, MotionScheme.expressive) but compiles them
 * `internal`, so they cannot be called from here - verified by compiling against the pinned
 * artifact, not assumed. Moving to Expressive needs a material3 upgrade; until then transitions are
 * built from explicit specs at the call site.
 *
 * @param darkTheme whether to render dark. Defaults to the system setting; the settings screen's
 *   light/dark/system preference is resolved by the caller and passed in.
 * @param dynamicColor set false to force the brand scheme even where dynamic colour is available -
 *   used by previews, which have no wallpaper to derive one from.
 */
@Composable
fun Iq33Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = iq33ColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor),
        typography = Iq33Typography,
        shapes = Iq33Shapes,
        content = content,
    )
}

@Composable
private fun iq33ColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
): ColorScheme {
    val context = LocalContext.current
    val dynamicAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return when {
        dynamicAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable -> dynamicLightColorScheme(context)
        darkTheme -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }
}
