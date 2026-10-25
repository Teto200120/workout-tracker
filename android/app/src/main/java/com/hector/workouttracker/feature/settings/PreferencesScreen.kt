package com.hector.workouttracker.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.visibleName
import com.hector.workouttracker.data.*
import java.math.BigDecimal

@Composable
internal fun PreferencesScreen(back: () -> Unit, vm: PreferencesViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
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
    Column(Modifier.fillMaxSize()) {
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
                TextButton(onClick = ::leave, enabled = !state.busy) { Text("Back to You") }
                Heading("Preferences", "Make it yours.", "Save changes when you're ready.")
                state.message?.let { Text(it) }
                if (!state.restorable) Text("Save before leaving the app to keep these edits.")
                if (values == null) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else TextButton(onClick = vm::reload) { Text("Retry loading preferences") }
                }
            }
            if (values != null) {
                item {
                    OutlinedTextField(
                        values.displayName,
                        { value -> vm.edit { it.copy(displayName = value) } },
                        label = { Text("Your name") },
                        isError = !visibleName(values.displayName.trim(), 80),
                        supportingText = {
                            if (!visibleName(values.displayName.trim(), 80))
                                Text("Enter a name up to 80 characters.")
                        },
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("preference-name"),
                    )
                }
                item {
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Weekly goal", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Workouts in the last seven days. Multiple workouts on one day count separately."
                            )
                            PreferenceNumber(
                                values.weeklyGoal,
                                "Workouts per week",
                                "weeklyGoal",
                                !state.busy,
                            ) { value ->
                                vm.edit { it.copy(weeklyGoal = value) }
                            }
                        }
                    }
                }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("Workout & feel", style = MaterialTheme.typography.titleLarge)
                            PreferenceToggle(
                                "Track RPE",
                                "Hide entry fields without deleting past or active RPE values.",
                                "rpe",
                                values.trackRpe,
                                !state.busy,
                            ) { value ->
                                vm.edit { it.copy(trackRpe = value) }
                            }
                            PreferenceToggle(
                                "Haptics",
                                "Touch feedback for set completion; Android's system setting also applies.",
                                "haptics",
                                values.haptics,
                                !state.busy,
                            ) { value ->
                                vm.edit { it.copy(haptics = value) }
                            }
                            PreferenceToggle(
                                "Animations",
                                "App transitions and component motion. Android's reduced-motion setting also applies.",
                                "animations",
                                values.animations,
                                !state.busy,
                            ) { value ->
                                vm.edit { it.copy(animations = value) }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { advanced = !advanced },
                        modifier = Modifier.fillMaxWidth().testTag("progression-preferences"),
                    ) {
                        Text(if (advanced) "Hide progression defaults" else "Progression defaults")
                    }
                    if (advanced)
                        Text(
                            "These defaults control suggested loads and rep ranges. Suggestions use your saved working sets and RPE preference."
                        )
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
                            Card {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        "$label rep range",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Button(
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
            Text(if (state.busy) "Saving…" else "Save preferences")
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
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        isError = invalid,
        supportingText = {
            if (invalid)
                Text(
                    if (places == 0) "Enter a whole number from $minimum to $maximum."
                    else "Enter $minimum–$maximum, up to 2 decimals."
                )
        },
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
                style = MaterialTheme.typography.bodyMedium,
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
