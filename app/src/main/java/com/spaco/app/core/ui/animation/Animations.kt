package com.spaco.app.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * A gentle overshoot used by the answer reveal, so the feedback card settles
 * rather than snapping flat.
 */
val SpaCoOvershoot: Easing = Easing { fraction ->
    val c = 1.70158f
    val t = fraction - 1f
    1f + (c + 1f) * t * t * t + c * t * t
}

/**
 * Rotation for the answer-reveal flip. The resting value is always a full turn
 * once revealed, so the value the learner sees is the value the state says.
 *
 * Presentation only: this never gates grading or advancing.
 */
@Composable
fun rememberAnswerRevealRotation(visible: Boolean): Animatable<Float, AnimationVector1D> {
    val rotation = remember { Animatable(if (visible) 360f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            rotation.snapTo(90f)
            rotation.animateTo(360f, animationSpec = tween(durationMillis = 420, easing = SpaCoOvershoot))
        } else {
            rotation.snapTo(0f)
        }
    }
    return rotation
}

/**
 * A springy scale keyed by [key], used for the grading pulse and the due-count
 * badge. Changing the key pulses from 1.0 up and back.
 */
@Composable
fun rememberPulseScale(key: Any?): Float {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key != null) {
            pulse.snapTo(1.25f)
            pulse.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f))
        }
    }
    return pulse.value
}
