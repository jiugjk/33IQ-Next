package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * One zoomable, pannable image inside the full-screen viewer.
 *
 * Gestures:
 *  - pinch to zoom between 1x and 5x, panning with the same gesture, around the fingers
 *  - double-tap to toggle between fit and 2.5x, centred on the tap
 *  - single tap to dismiss, but only at 1x - while zoomed in a stray tap should not throw away the
 *    position the user just framed
 *
 * Scale and offset are applied immediately. The previous `animateFloatAsState(tween(200))` on every
 * pinch sample left the image lagging behind the fingers; a double-tap snap is the cost of keeping
 * pinch on the fingers.
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
    val session = remember(imageUrl) { ZoomSession() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportWidth = with(density) { maxWidth.toPx() }
        val viewportHeight = with(density) { maxHeight.toPx() }
        session.viewport = Offset(viewportWidth, viewportHeight)

        ZoomableImageLayer(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            session = session,
            taps = ImageTaps(onTap = onTap, onLongPress = onLongPress),
        )
    }
}

@Composable
private fun ZoomableImageLayer(
    imageUrl: String,
    contentDescription: String?,
    session: ZoomSession,
    taps: ImageTaps,
) {
    val transform = session.transform

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(imageUrl) {
                    detectTapGestures(
                        onTap = { if (!session.isZoomed) taps.onTap() },
                        onLongPress = { taps.onLongPress() },
                        onDoubleTap = session::doubleTap,
                    )
                }.detectPagerAwareZoomGestures(
                    imageUrl = imageUrl,
                    canPan = { session.isZoomed },
                    onGesture = session::pinch,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state -> session.intrinsicSize = state.painter.intrinsicSize },
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                    },
        )
    }
}

private data class ImageTaps(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
)

/**
 * Mutable zoom/pan for one image. Recreated when [ZoomableImage] is given a new URL so a swipe
 * never inherits the previous picture's scale. Viewport and intrinsic size are written every
 * composition and read at gesture time, so the pointerInput lambdas are not keyed on them and a
 * pinch is not cancelled when Coil reports the real size.
 */
private class ZoomSession {
    var transform by mutableStateOf(ZoomTransform())
    var viewport: Offset = Offset.Zero
    var intrinsicSize: Size = Size.Zero

    val isZoomed: Boolean get() = transform.isZoomed

    fun pinch(gesture: ZoomGesture) {
        transform = transform.pinch(gesture = gesture, viewport = viewport, intrinsicSize = intrinsicSize)
    }

    fun doubleTap(tapOffset: Offset) {
        transform =
            transform.doubleTap(tapOffset = tapOffset, viewport = viewport, intrinsicSize = intrinsicSize)
    }
}
