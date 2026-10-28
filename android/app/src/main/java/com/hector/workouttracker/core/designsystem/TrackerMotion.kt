package com.hector.workouttracker.core.designsystem

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/** Short, shared timings keep navigation, disclosure and reorder motion in step. */
internal object TrackerMotion {
    const val Duration = 220
    const val Quick = 160
    val Easing = CubicBezierEasing(.2f, 0f, 0f, 1f)

    fun enter(forward: Boolean = true): EnterTransition =
        fadeIn(tween(Duration)) +
            slideInHorizontally(tween(Duration, easing = Easing)) {
                if (forward) it / 12 else -it / 24
            }

    fun exit(forward: Boolean = true): ExitTransition =
        fadeOut(tween(Quick)) +
            slideOutHorizontally(tween(Duration, easing = Easing)) {
                if (forward) -it / 24 else it / 12
            }
}
