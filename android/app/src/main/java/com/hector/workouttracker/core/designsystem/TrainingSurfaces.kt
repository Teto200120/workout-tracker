package com.hector.workouttracker.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The same quiet contour motif ties together the three primary destinations. */
@Composable
internal fun TrainingHero(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                topStart = 32.dp,
                topEnd = 32.dp,
                bottomEnd = 32.dp,
                bottomStart = 12.dp,
            ),
        color = trackerPanelColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
        shadowElevation = 6.dp,
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF314C68), trackerPanelColor(), Color(0xFF182B42))
                    else listOf(Color(0xFFF4E6DF), Color(0xFFE5EDF7), Color(0xFFD8E5F3))
                )
            )
        ) {
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                val center = Offset(size.width + 13.dp.toPx(), 29.dp.toPx())
                drawCircle(accent.copy(alpha = .06f), 72.dp.toPx(), center)
                listOf(48.dp, 82.dp, 116.dp).forEach { radius ->
                    drawCircle(
                        accent.copy(alpha = if (dark) .19f else .12f),
                        radius.toPx(),
                        center,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun TrainingEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier.size(size).clearAndSetSemantics {},
        shape =
            RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomEnd = 24.dp,
                bottomStart = 8.dp,
            ),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

/** Counts beside the ring provide the accessible, unrounded source values. */
@Composable
internal fun TrainingGoalRing(completed: Int, goal: Int) {
    val progress by
        animateFloatAsState(
            (completed.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f),
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "Training goal",
        )
    Box(Modifier.size(88.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 7.dp,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
        )
        Icon(
            if (completed >= goal && goal > 0) TrackerIcons.Check else TrackerIcons.Workout,
            null,
            Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A compact form heading or a more prominent summary/library heading. */
@Composable
internal fun TrainingHeading(
    title: String,
    detail: String? = null,
    icon: ImageVector,
    hero: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrainingEmblem(size = 40.dp) { Icon(icon, null, Modifier.size(22.dp)) }
                Text(
                    title,
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (hero) {
        TrainingHero { Box(Modifier.fillMaxWidth().padding(20.dp)) { content() } }
    } else {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { content() }
    }
}
