package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.max

/**
 * One zoomable, pannable image inside the full-screen viewer.
 *
 * Gestures:
 *  - pinch to zoom between 1x and [MAX_SCALE], panning with the same gesture
 *  - double-tap to toggle between fit and [DOUBLE_TAP_SCALE], centred on the tap
 *  - single tap to dismiss, but only at 1x - while zoomed in a stray tap should not throw away the
 *    position the user just framed
 *
 * Panning is clamped to the *fitted* image's overflow, not the whole viewport: a wide strip must
 * not be draggable off-screen vertically. At 1x pan is left unconsumed so the pager can switch
 * images; once zoomed in this consumes them instead.
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

        ZoomableImageLayer(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            render = ZoomRender(zoom = zoom, animatedScale = animatedScale, viewport = viewport),
            taps = ImageTaps(onTap = onTap, onLongPress = onLongPress),
        )
    }
}

@Composable
private fun ZoomableImageLayer(
    imageUrl: String,
    contentDescription: String?,
    render: ZoomRender,
    taps: ImageTaps,
) {
    val zoom = render.zoom
    val viewport = render.viewport
    var intrinsicSize by remember(imageUrl) { mutableStateOf(Size.Zero) }
    val transformState =
        rememberTransformableState { zoomChange, panChange, _ ->
            zoom.onTransform(
                pan = panChange,
                zoomFactor = zoomChange,
                viewport = viewport,
                intrinsicSize = intrinsicSize,
            )
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(imageUrl, viewport, intrinsicSize) {
                    detectTapGestures(
                        onTap = { if (zoom.scale <= 1f + SCALE_EPSILON) taps.onTap() },
                        onLongPress = { taps.onLongPress() },
                        onDoubleTap = { tapOffset ->
                            zoom.onDoubleTap(
                                tapOffset = tapOffset,
                                viewport = viewport,
                                intrinsicSize = intrinsicSize,
                            )
                        },
                    )
                }.transformable(
                    state = transformState,
                    canPan = { zoom.scale > 1f + SCALE_EPSILON },
                ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state -> intrinsicSize = state.painter.intrinsicSize },
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = render.animatedScale
                        scaleY = render.animatedScale
                        translationX = zoom.offsetX
                        translationY = zoom.offsetY
                    },
        )
    }
}

private data class ZoomRender(
    val zoom: ZoomState,
    val animatedScale: Float,
    val viewport: Offset,
)

private data class ImageTaps(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
)

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
        intrinsicSize: Size,
    ) {
        if (scale > 1f + SCALE_EPSILON) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            return
        }

        scale = DOUBLE_TAP_SCALE
        offsetX = (viewport.x / 2f - tapOffset.x) * (DOUBLE_TAP_SCALE - 1f)
        offsetY = (viewport.y / 2f - tapOffset.y) * (DOUBLE_TAP_SCALE - 1f)
        clampOffsets(forScale = DOUBLE_TAP_SCALE, viewport = viewport, intrinsicSize = intrinsicSize)
    }

    fun onTransform(
        pan: Offset,
        zoomFactor: Float,
        viewport: Offset,
        intrinsicSize: Size,
    ) {
        val newScale = (scale * zoomFactor).coerceIn(1f, MAX_SCALE)

        if (newScale > 1f + SCALE_EPSILON || abs(zoomFactor - 1f) > SCALE_EPSILON) {
            scale = newScale
            offsetX += pan.x
            offsetY += pan.y
            clampOffsets(forScale = newScale, viewport = viewport, intrinsicSize = intrinsicSize)
        }
    }

    private fun clampOffsets(
        forScale: Float,
        viewport: Offset,
        intrinsicSize: Size,
    ) {
        val fitted = fittedSize(intrinsicSize, viewport)
        val maxX = max(0f, (fitted.x * forScale - viewport.x) / 2f)
        val maxY = max(0f, (fitted.y * forScale - viewport.y) / 2f)

        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }
}

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

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Floating-point slack for "is this still 1x", so a rounding error does not read as zoomed in. */
private const val SCALE_EPSILON = 0.01f
private const val ZOOM_ANIMATION_MILLIS = 200
