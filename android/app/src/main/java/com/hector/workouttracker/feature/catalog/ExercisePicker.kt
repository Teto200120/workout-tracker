package com.hector.workouttracker.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.catalog.*
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.visibleName
import com.hector.workouttracker.feature.help.CompactHelpTip
import com.hector.workouttracker.feature.session.TrackerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun CatalogWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxSize()
                .background(trackerScreenBackground())
                .safeDrawingPadding()
                .imePadding(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            content()
        }
    }
}

@Composable
internal fun ExercisePicker(
    state: TrackerState,
    onDismiss: () -> Unit,
    onAdd: (String) -> Boolean,
    returnLabel: String = "Back to workout",
    addLabel: String = "Add to workout",
    selectionVerb: String = "Add",
) {
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var filterChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable(query, muscle, equipment, category) { mutableIntStateOf(60) }
    var localLimit by rememberSaveable(query) { mutableIntStateOf(12) }
    var accepted by remember { mutableStateOf(false) }
    val catalog = state.catalog ?: remember { ExerciseCatalog(emptyList(), "") }
    val local =
        remember(state.localNames, state.draft?.exercises) {
            (state.draft?.exercises.orEmpty().map { it.name } + state.localNames).distinctBy {
                catalogKey(it)
            }
        }
    var results by
        remember(catalog, local, query, muscle, equipment, category) {
            mutableStateOf<ExerciseResults?>(null)
        }
    LaunchedEffect(catalog, local, query, muscle, equipment, category) {
        results =
            withContext(Dispatchers.Default) {
                catalog.search(local, query, CatalogFilters(muscle, equipment, category))
            }
    }
    fun add(name: String) {
        if (!accepted && onAdd(name)) {
            accepted = true
            onDismiss()
        }
    }
    val close = { if (guide != null) guide = null else onDismiss() }
    CatalogWindow(close) {
        val guideName = guide
        if (guideName != null) {
            GuideContent(guideName, catalog, close, onAdd = { add(guideName) }, addLabel = addLabel)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().testTag("exercise-picker"),
                state = list,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TextButton(onClick = onDismiss) { Text(returnLabel) }
                    Text("Choose exercise", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your saved names first. Guides available offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    TextField(
                        query,
                        { query = it },
                        label = { Text("Search exercises") },
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(TrackerIcons.Search, null, Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("exercise-search"),
                    )
                    TextButton(
                        onClick = {
                            filtersOpen = !filtersOpen
                            if (filtersOpen) focus.clearFocus()
                        }
                    ) {
                        Text(if (filtersOpen) "Hide filters" else "Show filters")
                    }
                    if (filtersOpen) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = muscle.isNotEmpty(),
                                onClick = { filterChoice = "Muscle" },
                                label = {
                                    Text(if (muscle.isEmpty()) "Muscle" else "Muscle: $muscle")
                                },
                            )
                            FilterChip(
                                selected = equipment.isNotEmpty(),
                                onClick = { filterChoice = "Equipment" },
                                label = {
                                    Text(
                                        if (equipment.isEmpty()) "Equipment"
                                        else "Equipment: $equipment"
                                    )
                                },
                            )
                            FilterChip(
                                selected = category.isNotEmpty(),
                                onClick = { filterChoice = "Category" },
                                label = {
                                    Text(
                                        if (category.isEmpty()) "Category"
                                        else "Category: $category"
                                    )
                                },
                            )
                            if (
                                muscle.isNotEmpty() ||
                                    equipment.isNotEmpty() ||
                                    category.isNotEmpty()
                            )
                                TextButton(
                                    onClick = {
                                        muscle = ""
                                        equipment = ""
                                        category = ""
                                    }
                                ) {
                                    Text("Clear filters")
                                }
                        }
                        Text(
                            "Filters apply to catalog suggestions. Your saved exercises stay available.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (visibleName(query.trim(), 120))
                        OutlinedButton(
                            onClick = { add(query) },
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.busy && !accepted,
                            modifier = Modifier.fillMaxWidth().testTag("add-search-name"),
                        ) {
                            Text("Use name: ${catalog.canonicalName(query, local)}")
                        }
                }
                item { Text("Your exercises", style = MaterialTheme.typography.titleLarge) }
                if (results == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (results!!.local.isEmpty())
                    item { Text("No saved names match. You can add your own above.") }
                else {
                    items(results!!.local.take(localLimit), key = { "local:$it" }) { name ->
                        ExerciseOption(
                            name,
                            "Saved on this device",
                            !state.busy && !accepted,
                            { add(name) },
                            { guide = name },
                            selectionVerb,
                        )
                    }
                    if (results!!.local.size > localLimit)
                        item {
                            TextButton(onClick = { localLimit += 60 }) {
                                Text("Show more saved exercises")
                            }
                        }
                }
                item {
                    Text("Exercise catalog", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Free Exercise DB · Unlicense",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                when {
                    state.catalogLoading -> item { Text("Loading offline catalog…") }
                    state.catalog == null ->
                        item {
                            Text(
                                "The catalog is unavailable. Your saved exercises and custom entry still work."
                            )
                        }
                    query.isBlank() &&
                        muscle.isEmpty() &&
                        equipment.isEmpty() &&
                        category.isEmpty() ->
                        item {
                            Text(
                                "Search by name or choose a filter to explore ${catalog.exercises.size} exercises."
                            )
                        }
                    results?.catalog?.isEmpty() == true ->
                        item { Text("No catalog matches. Try a different name or clear a filter.") }
                    else -> {
                        items(results?.catalog.orEmpty().take(limit), key = { it.id }) { exercise ->
                            ExerciseOption(
                                exercise.name,
                                (exercise.equipment + exercise.primaryMuscles).joinToString(" · "),
                                !state.busy && !accepted,
                                { add(exercise.name) },
                                { guide = exercise.name },
                                selectionVerb,
                            )
                        }
                        if ((results?.catalog?.size ?: 0) > limit)
                            item {
                                TextButton(onClick = { limit += 60 }) {
                                    Text("Show more catalog exercises")
                                }
                            }
                    }
                }
            }
        }
    }
    filterChoice?.let { choice ->
        val options =
            when (choice) {
                "Muscle" -> catalog.muscles
                "Equipment" -> catalog.equipment
                else -> catalog.categories
            }
        AlertDialog(
            onDismissRequest = { filterChoice = null },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(choice) },
            text = {
                LazyColumn {
                    items(listOf("") + options) { value ->
                        TextButton(
                            onClick = {
                                when (choice) {
                                    "Muscle" -> muscle = value
                                    "Equipment" -> equipment = value
                                    else -> category = value
                                }
                                filterChoice = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(value.ifEmpty { "Any ${choice.lowercase()}" })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { filterChoice = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseOption(
    name: String,
    detail: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    onGuide: () -> Unit,
    selectionVerb: String,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "$selectionVerb $name" },
                ) {
                    Text(selectionVerb)
                }
                TextButton(
                    onClick = onGuide,
                    modifier = Modifier.semantics { contentDescription = "Guide for $name" },
                ) {
                    Text("Guide")
                }
            }
        }
    }
}

@Composable
internal fun ExerciseGuide(name: String, catalog: ExerciseCatalog?, onDismiss: () -> Unit) {
    CatalogWindow(onDismiss) { GuideContent(name, catalog, onDismiss) }
}

@Composable
private fun GuideContent(
    name: String,
    catalog: ExerciseCatalog?,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "Add to workout",
) {
    val exercise = remember(name, catalog) { catalog?.guide(name) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).testTag("exercise-guide"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    "Exercise guide",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(name, style = MaterialTheme.typography.headlineMedium)
                if (exercise != null && exercise.name != name)
                    Text(
                        "Reference movement: ${exercise.name}",
                        style = MaterialTheme.typography.labelLarge,
                    )
            }
            item { CompactHelpTip("exerciseGuideBasics", "About exercise guides") }
            if (exercise == null || exercise.instructions.isEmpty())
                item {
                    Text(
                        "No verified guide match for this saved name. Use guidance for your exact movement and equipment."
                    )
                }
            else {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Equipment: ${exercise.equipment.joinToString().ifEmpty { "Not specified" }}"
                            )
                            Text("Primary muscles: ${exercise.primaryMuscles.joinToString()}")
                            if (exercise.secondaryMuscles.isNotEmpty())
                                Text(
                                    "Secondary muscles: ${exercise.secondaryMuscles.joinToString()}"
                                )
                            Text("${exercise.difficulty} · ${exercise.category}")
                        }
                    }
                }
                item { Text("How to perform it", style = MaterialTheme.typography.titleLarge) }
                items(exercise.instructions.withIndex().toList(), key = { it.index }) {
                    (index, instruction) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "${index + 1}",
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            instruction.replace(Regex("\\s+"), " "),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item {
                    Text(
                        "Source: Free Exercise DB by yuhonas and contributors\nhttps://github.com/yuhonas/free-exercise-db\nLicense: Unlicense",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { GuideReminder() }
        }
        if (onAdd != null)
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(20.dp)
                        .heightIn(min = 56.dp)
                        .testTag("add-from-guide"),
            ) {
                Text(addLabel)
            }
    }
}

@Composable
private fun GuideReminder() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Training reminders", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use a load you can control through a comfortable range of motion.\n\nKeep each repetition smooth and stop the set if form breaks down.\n\nStop if the movement causes sharp pain."
            )
        }
    }
}
