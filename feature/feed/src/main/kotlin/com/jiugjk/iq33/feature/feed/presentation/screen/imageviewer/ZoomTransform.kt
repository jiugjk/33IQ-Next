package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max

/**
 * Scale and pan of one image in the full-screen viewer.
 *
 * `graphicsLayer` scales around the composable's centre, then applies [offsetX]/[offsetY] in
 * screen pixels. Pinch handling therefore has to shift the offset so the point under the fingers
 * stays there - otherwise the image zooms around the middle of the screen and feels disconnected
 * from the gesture.
 */
internal data class ZoomTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val isZoomed: Boolean get() = scale > 1f + SCALE_EPSILON

    /**
     * Apply one pinch/pan sample. [ZoomGesture.centroid] and [ZoomGesture.pan] are in the same
     * viewport space as the offset, which is what makes the focal-point correction a subtraction
     * of `(centroid - centre) * (oldScale - newScale)`.
     */
    fun pinch(
        gesture: ZoomGesture,
        viewport: Offset,
        intrinsicSize: Size,
    ): ZoomTransform {
        if (gesture.centroid == Offset.Unspecified) return this

        val newScale = (scale * gesture.zoomFactor).coerceIn(MIN_SCALE, MAX_SCALE)
        val centreX = viewport.x / 2f
        val centreY = viewport.y / 2f

        return ZoomTransform(
            scale = newScale,
            offsetX = offsetX + gesture.pan.x + (gesture.centroid.x - centreX) * (scale - newScale),
            offsetY = offsetY + gesture.pan.y + (gesture.centroid.y - centreY) * (scale - newScale),
        ).clamped(viewport = viewport, intrinsicSize = intrinsicSize)
    }

    /**
     * Toggle between fit and [DOUBLE_TAP_SCALE]. Zooming in is centred on the tap, using the same
     * focal-point correction as a pinch; zooming out always returns to the fitted image.
     */
    fun doubleTap(
        tapOffset: Offset,
        viewport: Offset,
        intrinsicSize: Size,
    ): ZoomTransform {
        if (isZoomed) {
            return ZoomTransform()
        }

        return ZoomTransform(
            scale = DOUBLE_TAP_SCALE,
            offsetX = (viewport.x / 2f - tapOffset.x) * (DOUBLE_TAP_SCALE - 1f),
            offsetY = (viewport.y / 2f - tapOffset.y) * (DOUBLE_TAP_SCALE - 1f),
        ).clamped(viewport = viewport, intrinsicSize = intrinsicSize)
    }

    private fun clamped(
        viewport: Offset,
        intrinsicSize: Size,
    ): ZoomTransform {
        val fitted = fittedSize(intrinsic = intrinsicSize, viewport = viewport)
        val maxX = max(0f, (fitted.x * scale - viewport.x) / 2f)
        val maxY = max(0f, (fitted.y * scale - viewport.y) / 2f)

        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }
}

/** One pointer-input sample from a pinch or a one-finger pan while zoomed in. */
internal data class ZoomGesture(
    val centroid: Offset,
    val pan: Offset,
    val zoomFactor: Float,
)

/**
 * Size of the image after `ContentScale.Fit` inside [viewport]. Pan limits are this rectangle
 * scaled, not the whole screen: a wide strip must not be draggable into the letterbox.
 */
private fun fittedSize(
    intrinsic: Size,
    viewport: Offset,
): Offset {
    if (intrinsic.width <= 0f || intrinsic.height <= 0f) {
        return viewport
    }
    if (viewport.x <= 0f || viewport.y <= 0f) {
        return viewport
    }

    val fit = minOf(viewport.x / intrinsic.width, viewport.y / intrinsic.height)

    return Offset(intrinsic.width * fit, intrinsic.height * fit)
}

internal const val MAX_SCALE = 5f
internal const val DOUBLE_TAP_SCALE = 2.5f
internal const val MIN_SCALE = 1f

/** Floating-point slack for "is this still 1x", so a rounding error does not read as zoomed in. */
internal const val SCALE_EPSILON = 0.01f
