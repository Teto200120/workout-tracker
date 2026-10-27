package com.hector.workouttracker.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.TrackerIcons
import com.hector.workouttracker.core.designsystem.TrainingHeading
import com.hector.workouttracker.core.designsystem.trackerPanelColor
import com.hector.workouttracker.core.designsystem.trackerScreenBackground
import com.hector.workouttracker.core.model.visibleName
import com.hector.workouttracker.data.*
import java.math.BigDecimal

@Composable
internal fun PreferencesScreen(
    back: () -> Unit,
    vm: PreferencesViewModel = viewModel(),
    openSchedule: () -> Unit = {},
    openBackup: () -> Unit = {},
    openHelp: () -> Unit = {},
    openFeedback: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        if (vm.state.value.draft?.changed != true) vm.reload()
        onPauseOrDispose {}
    }
    val draft = state.draft
    val values = draft?.values
    var discard by rememberSaveable { mutableStateOf(false) }
    var reload by rememberSaveable { mutableStateOf(false) }
    var warning by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    fun leave() {
        if (!state.busy) {
            if (draft?.changed == true) discard = true else back()
        }
    }
    BackHandler { leave() }
    LaunchedEffect(state.message) { if (state.message != null) list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(trackerScreenBackground())) {
        state.error?.let { message ->
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (state.conflict)
                        TextButton(onClick = { reload = true }, enabled = !state.busy) {
                            Text("Reload saved preferences")
                        }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).testTag("preferences-editor"),
            state = list,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = ::leave, enabled = !state.busy) { Text("Back to Profile") }
                TrainingHeading("Settings", icon = TrackerIcons.Settings)
                state.message?.let {
                    Text(if (it == "Preferences saved.") "Settings saved." else it)
                }
                if (!state.restorable) Text("Save before leaving the app to keep these edits.")
                if (values == null) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else TextButton(onClick = vm::reload) { Text("Retry loading preferences") }
                }
            }
            if (values != null) {
                item {
                    TextField(
                        values.displayName,
                        { value -> vm.edit { it.copy(displayName = value) } },
                        label = { Text("Your name") },
                        shape = RoundedCornerShape(16.dp),
                        isError = !visibleName(values.displayName.trim(), 80),
                        supportingText =
                            if (!visibleName(values.displayName.trim(), 80)) {
                                { Text("Enter a name up to 80 characters.") }
                            } else null,
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("preference-name"),
                    )
                }
                item {
                    SettingsLabel("Training")
                    SettingsPanel {
                        PreferenceNumber(
                            values.weeklyGoal,
                            "Weekly workout goal",
                            "weeklyGoal",
                            !state.busy,
                        ) { value ->
                            vm.edit { it.copy(weeklyGoal = value) }
                        }
                        Text(
                            "Counts workouts in the last 7 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PreferenceToggle(
                            "Track RPE",
                            "Past entries are kept when turned off.",
                            "rpe",
                            values.trackRpe,
                            !state.busy,
                        ) { value ->
                            vm.edit { it.copy(trackRpe = value) }
                        }
                    }
                }
                item {
                    SettingsToolGroup {
                        SettingsTool(
                            "Weekly schedule",
                            "Gym, soccer and rest days",
                            TrackerIcons.Calendar,
                            Modifier.testTag("settings-schedule"),
                            !state.busy,
                            openSchedule,
                        )
                    }
                }
                item {
                    SettingsLabel("App feel")
                    SettingsPanel {
                        PreferenceToggle(
                            "Haptics",
                            "Set completion feedback",
                            "haptics",
                            values.haptics,
                            !state.busy,
                        ) { value ->
                            vm.edit { it.copy(haptics = value) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PreferenceToggle(
                            "Animations",
                            "Transitions and component motion",
                            "animations",
                            values.animations,
                            !state.busy,
                        ) { value ->
                            vm.edit { it.copy(animations = value) }
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = { advanced = !advanced },
                        modifier = Modifier.fillMaxWidth().testTag("progression-preferences"),
                    ) {
                        Text(
                            if (advanced) "Hide progression defaults" else "Progression defaults",
                            Modifier.weight(1f),
                        )
                        Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                    }
                }
                if (advanced) {
                    item {
                        PreferenceNumber(
                            values.numbers.getValue("defaultWeightJump"),
                            "Default load jump (lb)",
                            "defaultWeightJump",
                            !state.busy,
                        ) { value ->
                            vm.edit {
                                it.copy(numbers = it.numbers + ("defaultWeightJump" to value))
                            }
                        }
                    }
                    for ((prefix, label) in
                        listOf(
                            "compound" to "Compound",
                            "pull" to "Pull",
                            "isolation" to "Isolation",
                            "general" to "General",
                        )) {
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors =
                                    CardDefaults.cardColors(containerColor = trackerPanelColor()),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        "$label rep range",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        maxItemsInEachRow =
                                            if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                                    ) {
                                        for ((suffix, title) in
                                            listOf("Min" to "Minimum", "Max" to "Maximum")) {
                                            val key = "$prefix$suffix"
                                            PreferenceNumber(
                                                values.numbers.getValue(key),
                                                title,
                                                key,
                                                !state.busy,
                                                Modifier.weight(1f),
                                            ) { value ->
                                                vm.edit {
                                                    it.copy(numbers = it.numbers + (key to value))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    preferencesError(values)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    SettingsLabel("Data & support")
                    SettingsToolGroup {
                        SettingsTool(
                            "Backup & data",
                            "Import, export and manage local data",
                            TrackerIcons.Backup,
                            Modifier.testTag("settings-backup"),
                            !state.busy,
                            openBackup,
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsTool(
                            "Help & guidance",
                            "Tips and explanations",
                            TrackerIcons.Help,
                            Modifier.testTag("open-help"),
                            onClick = openHelp,
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsTool(
                            "Beta feedback",
                            "Open browser feedback",
                            TrackerIcons.Feedback,
                            Modifier.testTag("open-feedback"),
                            onClick = openFeedback,
                        )
                    }
                }
                item {
                    Text(
                        "Workout Tracker ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (draft?.changed == true || state.busy)
            Button(
                shape = RoundedCornerShape(20.dp),
                onClick = {
                    if (values != null && preferencesWarning(values) != null) warning = true
                    else vm.save()
                },
                enabled =
                    !state.busy &&
                        !state.conflict &&
                        draft?.changed == true &&
                        preferencesError(draft.values) == null,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(20.dp)
                        .heightIn(min = 56.dp)
                        .testTag("save-preferences"),
            ) {
                Text(if (state.busy) "Saving…" else "Save changes")
            }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Discard preference edits?") },
            text = {
                Text(
                    "Your saved preferences have not changed.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        back()
                    }
                ) {
                    Text("Discard edits")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
    if (reload)
        AlertDialog(
            onDismissRequest = { reload = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Reload saved preferences?") },
            text = {
                Text(
                    "This replaces your unsaved entries with the latest saved preferences and goal.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reload = false
                        vm.reload()
                    }
                ) {
                    Text("Reload")
                }
            },
            dismissButton = { TextButton(onClick = { reload = false }) { Text("Keep editing") } },
        )
    if (warning)
        AlertDialog(
            onDismissRequest = { warning = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Review these values") },
            text = {
                Text(
                    values?.let(::preferencesWarning) ?: "Review your goal and rep ranges.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        warning = false
                        vm.save()
                    }
                ) {
                    Text("Save anyway")
                }
            },
            dismissButton = { TextButton(onClick = { warning = false }) { Text("Keep editing") } },
        )
}

@Composable
private fun PreferenceNumber(
    value: String,
    label: String,
    key: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    change: (String) -> Unit,
) {
    val number = value.takeIf { it.length <= 32 }?.trim()?.replace(',', '.')?.toBigDecimalOrNull()
    val minimum = if (key == "defaultWeightJump") BigDecimal("0.5") else BigDecimal.ONE
    val maximum = if (key == "weeklyGoal") BigDecimal(100) else BigDecimal(1000)
    val places = if (key == "defaultWeightJump") 2 else 0
    val invalid =
        value.length > 32 ||
            !Regex("[0-9]+([.,][0-9]+)?").matches(value.trim()) ||
            number == null ||
            number < minimum ||
            number > maximum ||
            number.stripTrailingZeros().scale() > places
    TextField(
        value,
        change,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp),
        isError = invalid,
        supportingText =
            if (invalid) {
                {
                    Text(
                        if (places == 0) "Enter a whole number from $minimum to $maximum."
                        else "Enter $minimum–$maximum, up to 2 decimals."
                    )
                }
            } else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth().testTag("preference-$key"),
    )
}

@Composable
private fun PreferenceToggle(
    label: String,
    detail: String,
    key: String,
    checked: Boolean,
    enabled: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked,
            change,
            enabled = enabled,
            modifier = Modifier.testTag("preference-$key").semantics { contentDescription = label },
        )
    }
}

@Composable
private fun SettingsLabel(label: String) {
    Text(
        label,
        Modifier.padding(start = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
