package com.hector.workouttracker.feature.session

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.TrackerPalette

/** Values from the rendered PWA, including its later CSS overrides. Scoped to workouts. */
internal object WorkoutMotion {
    const val Press = 150
    const val Color = 170
    const val Chevron = 180
    const val Detail = 220
    const val Disclosure = 280
    const val Reorder = 220
    val Easing = CubicBezierEasing(.2f, .86f, .2f, 1f)
    val ControlEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)

    fun enter(): EnterTransition = fadeIn(tween(Detail, easing = Easing))

    fun exit(): ExitTransition = fadeOut(tween(Press, easing = Easing))

    fun disclosureEnter(shift: Int): EnterTransition =
        expandVertically(tween(Disclosure, easing = Easing), expandFrom = Alignment.Top) +
            fadeIn(tween(Disclosure, easing = Easing)) +
            slideInVertically(tween(Disclosure, easing = Easing)) { -shift }

    fun disclosureExit(shift: Int): ExitTransition =
        shrinkVertically(tween(Disclosure, easing = Easing), shrinkTowards = Alignment.Top) +
            fadeOut(tween(Disclosure, easing = Easing)) +
            slideOutVertically(tween(Disclosure, easing = Easing)) { -shift }
}

/** Follow the measured card as another card above it shrinks; a fixed scroll delta goes stale. */
internal suspend fun LazyListState.focusWorkoutCard(
    key: String,
    desiredTop: Float,
    animations: Boolean,
) {
    val initialTop = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset ?: return
    scroll {
        if (animations) {
            animate(
                0f,
                1f,
                animationSpec = tween(WorkoutMotion.Disclosure, easing = WorkoutMotion.Easing),
            ) { fraction, _ ->
                layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == key }
                    ?.let { item ->
                        val frameTop = initialTop + (desiredTop - initialTop) * fraction
                        scrollBy(item.offset - frameTop)
                    }
            }
        } else {
            scrollBy(initialTop - desiredTop)
        }
    }
}

/** PWA paths and 2.25-unit rounded strokes; close/back also follow its session markup. */
private fun workoutSymbol(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.25f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }
        .build()

internal object WorkoutIcons {
    val Check =
        workoutSymbol("Workout check") {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
    val Info =
        workoutSymbol("Exercise information") {
            moveTo(21f, 12f)
            curveTo(21f, 7.029f, 16.971f, 3f, 12f, 3f)
            curveTo(7.029f, 3f, 3f, 7.029f, 3f, 12f)
            curveTo(3f, 16.971f, 7.029f, 21f, 12f, 21f)
            curveTo(16.971f, 21f, 21f, 16.971f, 21f, 12f)
            close()
            moveTo(12f, 16f)
            lineTo(12f, 12f)
            moveTo(12f, 8f)
            lineTo(12.01f, 8f)
        }
    val Chevron =
        workoutSymbol("Workout disclosure") {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    val Next =
        workoutSymbol("Next exercise") {
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }
    val Grip =
        workoutSymbol("Reorder exercise") {
            for (y in listOf(6f, 12f, 18f)) for (x in listOf(9f, 15f)) {
                moveTo(x, y)
                lineTo(x + .01f, y)
            }
        }
    val Plus =
        workoutSymbol("Increase") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    val Minus =
        workoutSymbol("Decrease") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    val Close =
        workoutSymbol("Close workout view") {
            moveTo(3.5f, 3.5f)
            lineTo(20.5f, 20.5f)
            moveTo(3.5f, 20.5f)
            lineTo(20.5f, 3.5f)
        }
    val Back =
        workoutSymbol("Back to workout") {
            moveTo(15f, 18f)
            lineTo(9f, 12f)
            lineTo(15f, 6f)
            moveTo(10f, 12f)
            lineTo(20f, 12f)
        }
}

@Composable
internal fun workoutAccent(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < .5f) TrackerPalette.Coral
    else MaterialTheme.colorScheme.primary

@Composable
internal fun Modifier.workoutPressFeedback(
    source: MutableInteractionSource,
    animations: Boolean,
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            if (pressed && animations) .975f else 1f,
            animationSpec =
                if (animations) tween(WorkoutMotion.Press, easing = WorkoutMotion.ControlEasing)
                else snap(),
            label = "workout-press",
        )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
internal fun WorkoutChevron(expanded: Boolean, animations: Boolean, modifier: Modifier = Modifier) {
    val rotation =
        animateFloatAsState(
            if (expanded) 180f else 0f,
            animationSpec =
                if (animations) tween(WorkoutMotion.Chevron, easing = WorkoutMotion.ControlEasing)
                else snap(),
            label = "workout-chevron",
        )
    Icon(
        WorkoutIcons.Chevron,
        null,
        modifier.size(20.dp).graphicsLayer { rotationZ = rotation.value },
    )
}

@Composable
internal fun WorkoutIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animations: Boolean,
    accent: Boolean = false,
    detail: Boolean = false,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val tint = if (accent) workoutAccent() else colors.onSurfaceVariant
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = if (detail) RoundedCornerShape(18.dp) else CircleShape,
        border = BorderStroke(1.dp, tint.copy(alpha = if (accent) .23f else .18f)),
        colors =
            IconButtonDefaults.outlinedIconButtonColors(
                containerColor =
                    if (accent) tint.copy(alpha = .11f) else colors.secondary.copy(alpha = .12f),
                contentColor = tint,
            ),
        interactionSource = source,
        modifier = modifier.size(50.dp).workoutPressFeedback(source, animations),
        content = content,
    )
}

@Composable
internal fun WorkoutStatus(position: Int, complete: Boolean, active: Boolean, animations: Boolean) {
    val colors = MaterialTheme.colorScheme
    val green = if (colors.background.luminance() < .5f) Color(0xFF3BE27B) else Color(0xFF146C34)
    val target =
        when {
            complete -> green
            active -> workoutAccent()
            else -> colors.onSurfaceVariant
        }
    val tint by
        animateColorAsState(
            target,
            animationSpec =
                if (animations) tween(WorkoutMotion.Color, easing = WorkoutMotion.Easing)
                else snap(),
            label = "workout-status-color",
        )
    Surface(shape = CircleShape, color = tint.copy(alpha = .15f), contentColor = tint) {
        Box(
            Modifier.size(44.dp).testTag("session-status-$position"),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                complete,
                animationSpec = if (animations) tween(WorkoutMotion.Press) else snap(),
                label = "workout-status",
            ) { done ->
                if (done) Icon(WorkoutIcons.Check, null, Modifier.size(24.dp))
                else Text("${position + 1}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Disclosure already animates row height. Animating its neighbors again would lag behind normal
 * layout and overlap the unanimated form rows beneath the cards.
 */
internal fun LazyItemScope.workoutPlacement(
    animations: Boolean,
    draggedKey: String?,
    itemKey: String,
): Modifier =
    Modifier.animateItem(
        fadeInSpec = null,
        placementSpec =
            if (animations && draggedKey != null && draggedKey != itemKey)
                tween(WorkoutMotion.Reorder, easing = WorkoutMotion.ControlEasing)
            else null,
        fadeOutSpec = null,
    )
