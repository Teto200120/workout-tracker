package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.R
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.data.PreferenceValues

private data class SettingEntry(
    val label: String,
    val value: String?,
    val icon: Painter,
    val tag: String,
    val open: (() -> Unit)?,
)

internal fun LazyListScope.settingsHub(
    values: PreferenceValues,
    enabled: Boolean,
    edit: (String) -> Unit,
    schedule: () -> Unit,
    backup: () -> Unit,
    help: () -> Unit,
    feedback: () -> Unit,
) {
    item {
        SettingsCategory(
            "Profile",
            listOf(
                SettingEntry(
                    "Edit profile",
                    values.displayName,
                    painterResource(R.drawable.ic_person),
                    "settings-edit-profile",
                ) {
                    edit("profile")
                }
            ),
            enabled,
        )
    }
    item {
        SettingsCategory(
            "Workout preferences",
            listOf(
                SettingEntry(
                    "Weekly goal",
                    "${values.weeklyGoal} / week",
                    rememberVectorPainter(TrackerIcons.Goal),
                    "settings-edit-training",
                ) {
                    edit("training")
                },
                SettingEntry(
                    "Track RPE",
                    if (values.trackRpe) "On" else "Off",
                    rememberVectorPainter(TrackerIcons.Workout),
                    "settings-edit-rpe",
                ) {
                    edit("training")
                },
                SettingEntry(
                    "Weekly schedule",
                    null,
                    rememberVectorPainter(TrackerIcons.Calendar),
                    "settings-schedule",
                    schedule,
                ),
                SettingEntry(
                    "Progression defaults",
                    "Load & reps",
                    rememberVectorPainter(TrackerIcons.Tune),
                    "progression-preferences",
                ) {
                    edit("progression")
                },
            ),
            enabled,
            accent = if (isSystemInDarkTheme()) Color(0xFF80BAFF) else Color(0xFF265E9A),
        )
    }
    item {
        SettingsCategory(
            "App preferences",
            listOf(
                SettingEntry(
                    "Haptics",
                    if (values.haptics) "On" else "Off",
                    rememberVectorPainter(TrackerIcons.Haptics),
                    "settings-edit-feel",
                ) {
                    edit("feel")
                },
                SettingEntry(
                    "Animations",
                    if (values.animations) "On" else "Off",
                    rememberVectorPainter(TrackerIcons.Motion),
                    "settings-edit-animations",
                ) {
                    edit("feel")
                },
                SettingEntry(
                    "App version",
                    BuildConfig.VERSION_NAME,
                    rememberVectorPainter(TrackerIcons.Haptics),
                    "settings-version",
                    null,
                ),
            ),
            enabled,
            accent = if (isSystemInDarkTheme()) Color(0xFFC1A0F4) else Color(0xFF6D439E),
        )
    }
    item {
        SettingsCategory(
            "Help & support",
            listOf(
                SettingEntry(
                    "Backup & data",
                    null,
                    rememberVectorPainter(TrackerIcons.Backup),
                    "settings-backup",
                    backup,
                ),
                SettingEntry(
                    "Help & guidance",
                    null,
                    rememberVectorPainter(TrackerIcons.Help),
                    "open-help",
                    help,
                ),
                SettingEntry(
                    "Beta feedback",
                    null,
                    rememberVectorPainter(TrackerIcons.Feedback),
                    "open-feedback",
                    feedback,
                ),
            ),
            enabled,
            accent = if (isSystemInDarkTheme()) Color(0xFF5AD6D9) else Color(0xFF00696D),
        )
    }
    item {
        SettingsCategory(
            "Danger zone",
            listOf(
                SettingEntry(
                    "Manage local data",
                    "Review options",
                    rememberVectorPainter(TrackerIcons.DataManagement),
                    "settings-manage-data",
                    backup,
                )
            ),
            enabled,
            accent = MaterialTheme.colorScheme.error,
            danger = true,
        )
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    entries: List<SettingEntry>,
    enabled: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary,
    danger: Boolean = false,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = trackerPanelColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Column {
            Text(
                title.uppercase(),
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = .6.sp,
                color = if (danger) accent else MaterialTheme.colorScheme.primary,
            )
            entries.forEach { entry ->
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
                )
                SettingsValueRow(entry, enabled, accent, danger)
            }
        }
    }
}

@Composable
private fun SettingsValueRow(
    entry: SettingEntry,
    enabled: Boolean,
    accent: Color,
    danger: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val large = LocalDensity.current.fontScale > 1.3f || maxWidth < 360.dp
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (entry.open != null)
                        Modifier.clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = entry.open,
                        )
                    else Modifier
                )
                .heightIn(min = 76.dp)
                .testTag(entry.tag)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = if (enabled) .12f else .05f)) {
                Icon(
                    entry.icon,
                    null,
                    Modifier.padding(10.dp).size(20.dp),
                    tint = accent.copy(alpha = if (enabled) 1f else .38f),
                )
            }
            val ink =
                (if (danger) accent else MaterialTheme.colorScheme.onSurface).copy(
                    alpha = if (enabled) 1f else .38f
                )
            val muted =
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
            if (large) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.label, style = MaterialTheme.typography.titleMedium, color = ink)
                    entry.value?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    entry.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                )
                entry.value?.let {
                    Text(
                        it,
                        Modifier.widthIn(max = 112.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.open != null)
                Icon(
                    TrackerIcons.Chevron,
                    null,
                    Modifier.size(18.dp),
                    tint = if (danger) accent else muted,
                )
        }
    }
}
