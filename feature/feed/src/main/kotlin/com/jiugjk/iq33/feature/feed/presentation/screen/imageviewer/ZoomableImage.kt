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
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableFloatStateOf(0f) }

    // Animated so a double-tap eases rather than snapping; a pinch drives scale directly and the
    // animation simply keeps up.
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = ZOOM_ANIMATION_MILLIS),
        label = "imageScale",
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportWidth = with(density) { maxWidth.toPx() }
        val viewportHeight = with(density) { maxHeight.toPx() }

        fun clampOffsets(forScale: Float) {
            // The image is laid out to fit the viewport, so at scale S the overflow on each axis is
            // (S - 1) * viewport, split evenly either side of centre.
            val maxX = ((forScale - 1f) * viewportWidth / 2f).coerceAtLeast(0f)
            val maxY = ((forScale - 1f) * viewportHeight / 2f).coerceAtLeast(0f)

            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(imageUrl) {
                        detectTapGestures(
                            onTap = { if (scale <= 1f + SCALE_EPSILON) onTap() },
                            onLongPress = { onLongPress() },
                            onDoubleTap = { tapOffset ->
                                if (scale > 1f + SCALE_EPSILON) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = DOUBLE_TAP_SCALE
                                    // Move the tapped point toward the centre, so the double-tap
                                    // magnifies what was tapped rather than the middle of the image.
                                    offsetX = (viewportWidth / 2f - tapOffset.x) * (DOUBLE_TAP_SCALE - 1f)
                                    offsetY = (viewportHeight / 2f - tapOffset.y) * (DOUBLE_TAP_SCALE - 1f)
                                    clampOffsets(DOUBLE_TAP_SCALE)
                                }
                            },
                        )
                    }.pointerInput(imageUrl) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)

                            // A one-finger drag at 1x is left alone so the pager can use it to move
                            // between images; a pinch (zoom != 1) is always ours.
                            if (newScale > 1f + SCALE_EPSILON || abs(zoom - 1f) > SCALE_EPSILON) {
                                scale = newScale
                                offsetX += pan.x
                                offsetY += pan.y
                                clampOffsets(newScale)
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = offsetX
                            translationY = offsetY
                        },
            )
        }
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Floating-point slack for "is this still 1x", so a rounding error does not read as zoomed in. */
private const val SCALE_EPSILON = 0.01f
private const val ZOOM_ANIMATION_MILLIS = 200
