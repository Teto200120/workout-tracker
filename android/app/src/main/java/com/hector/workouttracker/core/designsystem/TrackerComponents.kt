package com.hector.workouttracker.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.domain.ActivityDay

internal object TrackerPalette {
    val Coral = Color(0xFFFF6B45)
    val OnCoral = Color(0xFF27130C)
    val Navy = Color(0xFF172A42)
    val OnNavy = Color(0xFFF8FAFC)
    val MutedNavy = Color(0xFFB9CBE0)
}

private fun symbol(name: String, draw: PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }
        .build()

internal object TrackerIcons {
    val Add =
        symbol("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    val Remove =
        symbol("Remove") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    val Check =
        symbol("Check") {
            moveTo(5f, 12f)
            lineTo(10f, 17f)
            lineTo(20f, 7f)
        }
    val Chevron =
        symbol("Open") {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    val Back =
        symbol("Back") {
            moveTo(15f, 6f)
            lineTo(9f, 12f)
            lineTo(15f, 18f)
        }
    val Workout =
        symbol("Workout") {
            moveTo(8f, 12f)
            lineTo(16f, 12f)
            moveTo(5f, 7f)
            lineTo(5f, 17f)
            moveTo(8f, 5f)
            lineTo(8f, 19f)
            moveTo(16f, 5f)
            lineTo(16f, 19f)
            moveTo(19f, 7f)
            lineTo(19f, 17f)
        }
    val Calendar =
        symbol("Schedule") {
            moveTo(5f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
            moveTo(5f, 10f)
            lineTo(19f, 10f)
            moveTo(8f, 3f)
            lineTo(8f, 7f)
            moveTo(16f, 3f)
            lineTo(16f, 7f)
        }
    val Tune =
        symbol("Preferences") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            moveTo(8f, 4f)
            lineTo(8f, 10f)
            moveTo(16f, 14f)
            lineTo(16f, 20f)
        }
    val Help =
        symbol("Help") {
            moveTo(8f, 8f)
            curveTo(8f, 3f, 17f, 3f, 17f, 8f)
            curveTo(17f, 11f, 12f, 11f, 12f, 15f)
            moveTo(12f, 19f)
            lineTo(12f, 19.1f)
        }
    val Feedback =
        symbol("Feedback") {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 16f)
            lineTo(10f, 16f)
            lineTo(5f, 21f)
            lineTo(5f, 16f)
            lineTo(4f, 16f)
            close()
            moveTo(8f, 8f)
            lineTo(16f, 8f)
            moveTo(8f, 12f)
            lineTo(13f, 12f)
        }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ActionRow(
    label: String,
    detail: String,
    icon: ImageVector,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick,
        modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Row(
            Modifier.padding(16.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                TrackerIcons.Chevron,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WeekStrip(days: List<ActivityDay>, today: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { day ->
            val current = day.key == today
            Surface(
                Modifier.weight(1f).semantics {
                    contentDescription =
                        "${day.key}, ${day.title}, ${day.status}${if (current) ", Today" else ""}"
                },
                shape = RoundedCornerShape(16.dp),
                color =
                    if (day.complete) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                border =
                    if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(day.label.take(1), style = MaterialTheme.typography.labelMedium)
                    if (day.complete)
                        Icon(
                            TrackerIcons.Check,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    else
                        Text(
                            day.key.takeLast(2),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
}
