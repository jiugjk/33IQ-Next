package com.jiugjk.iq33.feature.base.presentation.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The Material 3 type scale, with line heights widened for the Chinese text this app is made of.
 *
 * Material's default line heights are tuned for Latin script; Han characters fill their em box far
 * more completely, so a paragraph of question text set at the stock 1.33 ratio reads as a solid
 * block. Body and title styles therefore sit nearer 1.55-1.6, which is what makes a long question
 * scannable. Sizes themselves are M3's own - the hierarchy is the point, not a bespoke scale.
 *
 * FontFamily.Default resolves to the device's own CJK-capable system font; naming a specific family
 * here would fall back to a Latin face and lose that.
 */

private val DefaultFontFamily = FontFamily.Default

internal val Iq33Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 44.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 38.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 34.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 32.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.1.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
        // The question body itself. 17sp/28 is the one style worth tuning away from M3's 16sp/24:
        // a 33IQ question is often several paragraphs of Han text and this is what makes it read.
        bodyLarge =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.3.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = DefaultFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
