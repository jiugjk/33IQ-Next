package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import kotlin.math.abs

/**
 * One zoomable, pannable image inside the full-screen viewer.
 *
 * Gestures:
 *  - pinch to zoom between 1x and [MAX_SCALE], panning with the same gesture
 *  - double-tap to toggle between fit and [DOUBLE_TAP_SCALE], centred on the tap
 *  - single tap to dismiss, but only at 1x - while zoomed in a stray tap should not throw away the
 *    position the user just framed
 *
 * Panning is clamped to the scaled image's own bounds, so it can never be dragged off screen and
 * leave an empty frame. At 1x there is nothing to pan, which is what lets the pager take horizontal
 * drags and switch images; once zoomed in this consumes them instead.
 */
@Composable
internal fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom = remember(imageUrl) { ZoomState() }
    val animatedScale by animateFloatAsState(
        targetValue = zoom.scale,
        animationSpec = tween(durationMillis = ZOOM_ANIMATION_MILLIS),
        label = "imageScale",
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewport = Offset(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })

        FittedQuestionImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            scale = animatedScale,
            pan = Offset(zoom.offsetX, zoom.offsetY),
            modifier =
                Modifier.fillMaxSize().zoomGestures(
                    imageUrl = imageUrl,
                    zoom = zoom,
                    viewport = viewport,
                    onTap = onTap,
                    onLongPress = onLongPress,
                ),
        )
    }
}

@Composable
private fun FittedQuestionImage(
    imageUrl: String,
    contentDescription: String?,
    scale: Float,
    pan: Offset,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
        )
    }
}

private fun Modifier.zoomGestures(
    imageUrl: String,
    zoom: ZoomState,
    viewport: Offset,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier =
    pointerInput(imageUrl) {
        detectTapGestures(
            onTap = { if (zoom.scale <= 1f + SCALE_EPSILON) onTap() },
            onLongPress = { onLongPress() },
            onDoubleTap = { tapOffset -> zoom.onDoubleTap(tapOffset = tapOffset, viewport = viewport) },
        )
    }.pointerInput(imageUrl) {
        detectTransformGestures { _, pan, zoomFactor, _ ->
            zoom.onTransform(pan = pan, zoomFactor = zoomFactor, viewport = viewport)
        }
    }

/**
 * Mutable zoom/pan for one image. Recreated when [ZoomableImage] is given a new URL so a swipe
 * never inherits the previous picture's scale.
 */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    fun onDoubleTap(
        tapOffset: Offset,
        viewport: Offset,
    ) {
        if (scale > 1f + SCALE_EPSILON) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            return
        }

        scale = DOUBLE_TAP_SCALE
        // Move the tapped point toward the centre, so the double-tap magnifies what was tapped
        // rather than the middle of the image.
        offsetX = (viewport.x / 2f - tapOffset.x) * (DOUBLE_TAP_SCALE - 1f)
        offsetY = (viewport.y / 2f - tapOffset.y) * (DOUBLE_TAP_SCALE - 1f)
        clampOffsets(forScale = DOUBLE_TAP_SCALE, viewport = viewport)
    }

    fun onTransform(
        pan: Offset,
        zoomFactor: Float,
        viewport: Offset,
    ) {
        val newScale = (scale * zoomFactor).coerceIn(1f, MAX_SCALE)

        // A one-finger drag at 1x is left alone so the pager can use it to move between images;
        // a pinch (zoom != 1) is always ours.
        if (newScale > 1f + SCALE_EPSILON || abs(zoomFactor - 1f) > SCALE_EPSILON) {
            scale = newScale
            offsetX += pan.x
            offsetY += pan.y
            clampOffsets(forScale = newScale, viewport = viewport)
        }
    }

    private fun clampOffsets(
        forScale: Float,
        viewport: Offset,
    ) {
        // The image is laid out to fit the viewport, so at scale S the overflow on each axis is
        // (S - 1) * viewport, split evenly either side of centre.
        val maxX = ((forScale - 1f) * viewport.x / 2f).coerceAtLeast(0f)
        val maxY = ((forScale - 1f) * viewport.y / 2f).coerceAtLeast(0f)

        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Floating-point slack for "is this still 1x", so a rounding error does not read as zoomed in. */
private const val SCALE_EPSILON = 0.01f
private const val ZOOM_ANIMATION_MILLIS = 200
