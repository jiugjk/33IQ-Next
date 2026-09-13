package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val FEEDBACK_ANIM_MS = 600
private const val CORRECT_SCALE_PEAK = 1.06f
private const val CORRECT_SCALE_PEAK_AT_MS = 150
private const val SHAKE_LARGE = 10f
private const val SHAKE_MEDIUM = 8f
private const val SHAKE_SMALL = 4f
private const val SHAKE_AT_50 = 50
private const val SHAKE_AT_100 = 100
private const val SHAKE_AT_150 = 150
private const val SHAKE_AT_200 = 200
private const val SHAKE_AT_250 = 250
private const val SHAKE_AT_300 = 300

internal suspend fun runCorrectFeedback(
    scale: Animatable<Float, *>,
    floatY: Animatable<Float, *>,
) {
    coroutineScope {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec =
                    keyframes {
                        durationMillis = FEEDBACK_ANIM_MS
                        1f at 0
                        CORRECT_SCALE_PEAK at CORRECT_SCALE_PEAK_AT_MS using FastOutSlowInEasing
                        1f at FEEDBACK_ANIM_MS using FastOutSlowInEasing
                    },
            )
        }
        launch {
            floatY.snapTo(0f)
            floatY.animateTo(
                targetValue = -48f,
                animationSpec = tween(durationMillis = FEEDBACK_ANIM_MS, easing = FastOutSlowInEasing),
            )
        }
    }
}

internal suspend fun runWrongFeedback(shakeX: Animatable<Float, *>) {
    shakeX.animateTo(
        targetValue = 0f,
        animationSpec =
            keyframes {
                durationMillis = FEEDBACK_ANIM_MS
                0f at 0
                -SHAKE_LARGE at SHAKE_AT_50
                SHAKE_LARGE at SHAKE_AT_100
                -SHAKE_MEDIUM at SHAKE_AT_150
                SHAKE_MEDIUM at SHAKE_AT_200
                -SHAKE_SMALL at SHAKE_AT_250
                SHAKE_SMALL at SHAKE_AT_300
                0f at FEEDBACK_ANIM_MS
            },
    )
}

internal fun View.performAnswerHaptic(
    correct: Boolean,
    enabled: Boolean,
) {
    if (!enabled) return
    val feedback =
        if (correct) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
    performHapticFeedback(feedback)
}

@Composable
internal fun KnowledgeFloatLabel(
    delta: Int?,
    visible: Boolean,
    offsetY: Float,
    modifier: Modifier = Modifier,
) {
    if (!visible || delta == null) return
    val sign = if (delta >= 0) "+" else ""
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "学识 $sign$delta",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.offset { IntOffset(0, offsetY.roundToInt()) },
        )
    }
}

@Composable
internal fun StreakBanner(
    streak: Int,
    modifier: Modifier = Modifier,
) {
    if (streak < 2) return
    Text(
        text = "连对 $streak 题 \uD83D\uDD25",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/**
 * Plays answer feedback for a submission that finished *here*, exactly once.
 *
 * Driven by [SubmissionState.Done.completionToken], not by the result's contents: a result restored
 * from local history carries no token, so re-opening an answered question (or rebuilding the
 * composition around a surviving view model) never replays haptics, the shake/scale animation or
 * the 学识 float for a reward that was granted in the past. The handled token is remembered through
 * `rememberSaveable`, so a configuration change cannot replay it either.
 */
@Composable
internal fun rememberFeedbackAnimation(
    submission: SubmissionState,
    animationsEnabled: Boolean,
    hapticsEnabled: Boolean,
): FeedbackAnimState {
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    val shakeX = remember { Animatable(0f) }
    val floatY = remember { Animatable(0f) }
    var showFloat by remember { mutableStateOf(false) }
    var handledToken by rememberSaveable { mutableLongStateOf(0L) }
    val token = (submission as? SubmissionState.Done)?.completionToken

    LaunchedEffect(token, animationsEnabled, hapticsEnabled) {
        val result = (submission as? SubmissionState.Done)?.result
        val fresh = token != null && token != handledToken && result != null

        if (fresh) {
            handledToken = requireNotNull(token)

            when (result) {
                is SubmitAnswerResult.Correct -> {
                    view.performAnswerHaptic(correct = true, enabled = hapticsEnabled)
                    showFloat = result.scoreDelta != null
                    if (animationsEnabled) {
                        runCorrectFeedback(scale, floatY)
                    }
                    showFloat = false
                    floatY.snapTo(0f)
                }
                is SubmitAnswerResult.Wrong -> {
                    view.performAnswerHaptic(correct = false, enabled = hapticsEnabled)
                    if (animationsEnabled) {
                        runWrongFeedback(shakeX)
                    }
                }
                else -> { }
            }
        }
    }

    return FeedbackAnimState(scale.value, shakeX.value, floatY.value, showFloat)
}

internal data class FeedbackAnimState(
    val scale: Float,
    val shakeX: Float,
    val floatY: Float,
    val showFloat: Boolean,
)
