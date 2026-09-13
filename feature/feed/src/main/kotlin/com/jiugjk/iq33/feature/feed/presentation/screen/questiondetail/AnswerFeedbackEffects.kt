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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                        1.06f at 150 using FastOutSlowInEasing
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

internal suspend fun runWrongFeedback(
    shakeX: Animatable<Float, *>,
) {
    shakeX.animateTo(
        targetValue = 0f,
        animationSpec =
            keyframes {
                durationMillis = FEEDBACK_ANIM_MS
                0f at 0
                -10f at 50
                10f at 100
                -8f at 150
                8f at 200
                -4f at 250
                4f at 300
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
    var lastDoneKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(submission, animationsEnabled, hapticsEnabled) {
        val done = submission as? SubmissionState.Done ?: return@LaunchedEffect
        val key = "${done.submittedAnswer}:${done.result}"
        if (key == lastDoneKey) return@LaunchedEffect
        lastDoneKey = key
        when (val result = done.result) {
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
            else -> Unit
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
