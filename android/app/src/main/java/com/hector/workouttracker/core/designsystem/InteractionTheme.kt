package com.hector.workouttracker.core.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.hector.workouttracker.data.InteractionPreferences

internal object StillMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

internal class PreferenceHaptics(
    private val enabled: Boolean,
    private val delegate: HapticFeedback,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (enabled) delegate.performHapticFeedback(hapticFeedbackType)
    }
}

internal val LocalCompletionFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/** System animator/haptic settings still apply. No vibration permission or background work. */
@Composable
internal fun InteractionTheme(
    preferences: InteractionPreferences,
    content: @Composable () -> Unit,
) {
    val platformHaptics = LocalHapticFeedback.current
    val haptics =
        remember(preferences.haptics, platformHaptics) {
            PreferenceHaptics(preferences.haptics, platformHaptics)
        }
    val view = LocalView.current
    val complete: () -> Unit =
        remember(view, preferences.haptics) {
            {
                if (preferences.haptics) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                Unit
            }
        }
    CompositionLocalProvider(
        LocalHapticFeedback provides haptics,
        LocalCompletionFeedback provides complete,
    ) {
        MaterialTheme(
            motionScheme = if (preferences.animations) MaterialTheme.motionScheme else StillMotion,
            content = content,
        )
    }
}
