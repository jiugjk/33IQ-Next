package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Pinch-zoom and pan that does not steal the pager's one-finger swipe at 1x.
 *
 * `detectTransformGestures` consumes every drag once touch slop is crossed, so a HorizontalPager
 * wrapping the image could never change page. This detector only consumes:
 *  - two-or-more-finger pinches (always)
 *  - one-finger pans while [canPan] is true (the image is zoomed in)
 *
 * A one-finger drag at 1x is left unconsumed, which is what lets the pager take it.
 */
internal fun Modifier.detectPagerAwareZoomGestures(
    imageUrl: String,
    canPan: () -> Boolean,
    onGesture: (ZoomGesture) -> Unit,
): Modifier =
    pointerInput(imageUrl) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            trackZoomGesture(slop = slop, canPan = canPan, onGesture = onGesture)
        }
    }

private suspend fun AwaitPointerEventScope.trackZoomGesture(
    slop: Float,
    canPan: () -> Boolean,
    onGesture: (ZoomGesture) -> Unit,
) {
    val motion = MotionAccumulator()
    var active = true
    while (active) {
        val event = awaitPointerEvent()
        active = event.isStillActive()
        if (active) {
            motion.dispatch(event = event, slop = slop, canPan = canPan(), onGesture = onGesture)
        }
    }
}

private fun PointerEvent.isStillActive(): Boolean {
    val canceled = changes.any { it.isConsumed }
    val pressed = changes.any { it.pressed }
    return !canceled && pressed
}

/**
 * Accumulates motion until touch slop, then forwards per-event zoom/pan. Kept as a class so the
 * gesture loop stays under NestedBlockDepth.
 */
private class MotionAccumulator {
    private var accumulatedZoom = 1f
    private var accumulatedPan = Offset.Zero
    private var pastSlop = false

    fun dispatch(
        event: PointerEvent,
        slop: Float,
        canPan: Boolean,
        onGesture: (ZoomGesture) -> Unit,
    ) {
        if (!pastSlop) {
            pastSlop = crossedSlop(event = event, slop = slop, canPan = canPan)
        }

        if (pastSlop) {
            emitIfHandled(event = event, canPan = canPan, onGesture = onGesture)
        }
    }

    private fun crossedSlop(
        event: PointerEvent,
        slop: Float,
        canPan: Boolean,
    ): Boolean {
        val zoomChange = event.calculateZoom()
        val panChange = event.calculatePan()
        accumulatedZoom *= zoomChange
        accumulatedPan += panChange
        val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
        val panMotion = accumulatedPan.getDistance()
        val pointerCount = event.changes.count { change -> change.pressed }
        val zoomedPastSlop = zoomMotion > slop
        val twoFingerPan = pointerCount >= 2 && panMotion > slop
        val zoomedPan = canPan && panMotion > slop
        return zoomedPastSlop || twoFingerPan || zoomedPan
    }

    private fun emitIfHandled(
        event: PointerEvent,
        canPan: Boolean,
        onGesture: (ZoomGesture) -> Unit,
    ) {
        val centroid = event.calculateCentroid(useCurrent = false)
        if (centroid == Offset.Unspecified) return

        val zoomChange = event.calculateZoom()
        val panChange = event.calculatePan()
        val pointerCount = event.changes.count { change -> change.pressed }
        val zooming = pointerCount >= 2 || abs(zoomChange - 1f) > SCALE_EPSILON
        val panning = panChange != Offset.Zero && (pointerCount >= 2 || canPan)
        if (!zooming && !panning) return

        onGesture(
            ZoomGesture(
                centroid = centroid,
                pan = if (panning) panChange else Offset.Zero,
                zoomFactor = if (zooming) zoomChange else 1f,
            ),
        )
        event.changes.forEach { change ->
            if (change.positionChanged()) {
                change.consume()
            }
        }
    }
}
