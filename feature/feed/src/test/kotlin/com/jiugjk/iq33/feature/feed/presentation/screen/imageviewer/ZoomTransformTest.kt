package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeLessThan
import org.junit.jupiter.api.Test

class ZoomTransformTest {
    @Test
    fun `a pinch on the right side shifts the image left so the focal point stays put`() {
        val pinched =
            ZoomTransform().pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(RIGHT_CENTROID, CENTRE),
                        pan = Offset.Zero,
                        zoomFactor = 2f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        pinched.scale shouldBeEqualTo 2f
        // Centroid is 250px right of centre; doubling around centre would move that point 250px
        // further right, so the offset compensates by -250.
        pinched.offsetX shouldBeEqualTo -250f
        pinched.offsetY shouldBeEqualTo 0f
    }

    @Test
    fun `a pinch also keeps the pan in screen pixels`() {
        val pinched =
            ZoomTransform().pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(CENTRE, CENTRE),
                        pan = Offset(40f, -15f),
                        zoomFactor = 2f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        pinched.offsetX shouldBeEqualTo 40f
        pinched.offsetY shouldBeEqualTo -15f
    }

    @Test
    fun `scale cannot exceed the maximum`() {
        val pinched =
            ZoomTransform(scale = 4f).pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(CENTRE, CENTRE),
                        pan = Offset.Zero,
                        zoomFactor = 2f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        pinched.scale shouldBeEqualTo MAX_SCALE
    }

    @Test
    fun `scale cannot drop below fit`() {
        val pinched =
            ZoomTransform().pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(CENTRE, CENTRE),
                        pan = Offset.Zero,
                        zoomFactor = 0.5f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        pinched.scale shouldBeEqualTo 1f
        pinched.offsetX shouldBeEqualTo 0f
        pinched.offsetY shouldBeEqualTo 0f
    }

    @Test
    fun `a wide strip cannot be panned into its letterbox`() {
        val wide = Size(2000f, 500f)
        val zoomed =
            ZoomTransform().pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(CENTRE, CENTRE),
                        pan = Offset.Zero,
                        zoomFactor = 2f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = wide,
            )

        val panned =
            zoomed.pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(CENTRE, CENTRE),
                        pan = Offset(0f, 400f),
                        zoomFactor = 1f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = wide,
            )

        // Fitted size is 1000x250; at 2x that is 2000x500, so there is horizontal room and no
        // vertical overflow at all.
        panned.offsetY shouldBeEqualTo 0f
        panned.offsetX shouldBeEqualTo 0f
    }

    @Test
    fun `double-tap on a fitted image zooms in on the tap`() {
        val zoomed =
            ZoomTransform().doubleTap(
                tapOffset = Offset(RIGHT_CENTROID, CENTRE),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        zoomed.scale shouldBeEqualTo DOUBLE_TAP_SCALE
        zoomed.offsetX shouldBeEqualTo (CENTRE - RIGHT_CENTROID) * (DOUBLE_TAP_SCALE - 1f)
        zoomed.offsetY shouldBeEqualTo 0f
    }

    @Test
    fun `double-tap on a zoomed image returns to fit`() {
        val zoomed = ZoomTransform(scale = DOUBLE_TAP_SCALE, offsetX = -100f, offsetY = 40f)

        val reset =
            zoomed.doubleTap(
                tapOffset = Offset(CENTRE, CENTRE),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        reset shouldBeEqualTo ZoomTransform()
    }

    @Test
    fun `an unspecified centroid is ignored so a broken sample cannot jump the image`() {
        val start = ZoomTransform(scale = 2f, offsetX = -30f, offsetY = 10f)

        val unchanged =
            start.pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset.Unspecified,
                        pan = Offset(99f, 99f),
                        zoomFactor = 2f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        unchanged shouldBeEqualTo start
    }

    @Test
    fun `pan is clamped to the scaled image's overflow`() {
        val zoomed = ZoomTransform(scale = 2f)
        val panned =
            zoomed.pinch(
                gesture =
                    ZoomGesture(
                        centroid = Offset(CENTRE, CENTRE),
                        pan = Offset(10_000f, 10_000f),
                        zoomFactor = 1f,
                    ),
                viewport = VIEWPORT,
                intrinsicSize = SQUARE,
            )

        // At 2x a 1000x1000 image in a 1000x1000 viewport overflows by 500px each side.
        panned.offsetX shouldBeEqualTo 500f
        panned.offsetY shouldBeEqualTo 500f
        panned.offsetX shouldBeLessThan 10_000f
    }
}

private val VIEWPORT = Offset(1000f, 1000f)
private val SQUARE = Size(1000f, 1000f)
private const val CENTRE = 500f
private const val RIGHT_CENTROID = 750f
