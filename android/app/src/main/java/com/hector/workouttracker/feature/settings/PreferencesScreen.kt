package com.hector.workouttracker.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.core.designsystem.TrackerIcons
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
    val focus = LocalFocusManager.current
    var page by rememberSaveable { mutableStateOf("home") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var reload by rememberSaveable { mutableStateOf(false) }
    var warning by rememberSaveable { mutableStateOf(false) }
    val hubList = rememberLazyListState()
    val profileList = rememberLazyListState()
    val trainingList = rememberLazyListState()
    val feelList = rememberLazyListState()
    val progressionList = rememberLazyListState()
    val list =
        when (page) {
            "profile" -> profileList
            "training" -> trainingList
            "feel" -> feelList
            "progression" -> progressionList
            else -> hubList
        }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    fun leave() {
        if (!state.busy) {
            focus.clearFocus()
            if (draft?.changed == true) discard = true
            else if (page == "home") back() else page = "home"
        }
    }
    BackHandler { leave() }
    LaunchedEffect(state.message) { if (state.message != null) list.scrollToItem(0) }
    Column(
        Modifier.fillMaxSize()
            .background(trackerScreenBackground())
            .testTag(if (page == "home") "settings-hub" else "settings-editor-$page")
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = ::leave,
                enabled = !state.busy,
                modifier = Modifier.size(48.dp).testTag("settings-back"),
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = trackerPanelColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    TrackerIcons.Back,
                    if (page == "home") "Back to Profile" else "Back to settings",
                    Modifier.size(24.dp),
                )
            }
            Text(
                when (page) {
                    "profile" -> "Edit profile"
                    "training" -> "Workout preferences"
                    "feel" -> "App behavior"
                    "progression" -> "Progression defaults"
                    else -> "Settings"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        state.error?.let { message ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.message != null || !state.restorable || state.busy || values == null)
                item {
                    state.message?.let {
                        Text(
                            if (it == "Preferences saved.") "Settings saved." else it,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!state.restorable) Text("Save before leaving the app to keep these edits.")
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (values == null && !state.busy)
                        TextButton(onClick = vm::reload) { Text("Retry loading preferences") }
                }
            if (values != null) {
                when (page) {
                    "home" -> {
                        settingsHub(
                            values,
                            !state.busy,
                            { page = it },
                            openSchedule,
                            openBackup,
                            openHelp,
                            openFeedback,
                        )
                    }
                    "profile" -> {
                        item {
                            SettingsLabel("Your profile")
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
                    }
                    "training" -> {
                        item {
                            SettingsLabel("Weekly goal")
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
                            }
                        }
                        item {
                            SettingsLabel("Effort tracking")
                            SettingsPanel {
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
                    }
                    "feel" -> {
                        item {
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
                    }
                    "progression" -> {
                        item {
                            SettingsLabel("Load progression")
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
                                SettingsPanel {
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
            }
        }
        if (page != "home" && (draft?.changed == true || state.busy))
            Button(
                shape = RoundedCornerShape(24.dp),
                onClick = {
                    focus.clearFocus()
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
                        if (page == "home") back()
                        else {
                            vm.reload()
                            page = "home"
                        }
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
