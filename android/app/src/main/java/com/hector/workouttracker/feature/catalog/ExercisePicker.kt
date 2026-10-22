package com.hector.workouttracker.feature.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.catalog.*
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.visibleName
import com.hector.workouttracker.feature.session.TrackerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun CatalogWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) { content() }
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
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TextButton(onClick = onDismiss) { Text(returnLabel) }
                    Heading(
                        "Exercise library",
                        "Find your next move.",
                        "Your saved names come first. All guides work offline.",
                    )
                }
                item {
                    OutlinedTextField(
                        query,
                        { query = it },
                        label = { Text("Search exercises") },
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
                            OutlinedButton(onClick = { filterChoice = "Muscle" }) {
                                Text(muscle.ifEmpty { "Muscle" })
                            }
                            OutlinedButton(onClick = { filterChoice = "Equipment" }) {
                                Text(equipment.ifEmpty { "Equipment" })
                            }
                            OutlinedButton(onClick = { filterChoice = "Category" }) {
                                Text(category.ifEmpty { "Category" })
                            }
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
                        Button(
                            onClick = { add(query) },
                            enabled = !state.busy && !accepted,
                            modifier = Modifier.fillMaxWidth().testTag("add-search-name"),
                        ) {
                            Text("$selectionVerb ${catalog.canonicalName(query, local)}")
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAdd, enabled = enabled) {
                    Text("$selectionVerb $name")
                }
                TextButton(onClick = onGuide) { Text("Guide for $name") }
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
    LazyColumn(
        Modifier.fillMaxSize().testTag("exercise-guide"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Heading("Exercise guide", name)
            if (exercise != null && exercise.name != name)
                Text(
                    "Reference movement: ${exercise.name}",
                    style = MaterialTheme.typography.labelLarge,
                )
        }
        if (onAdd != null)
            item {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().testTag("add-from-guide"),
                ) {
                    Text(addLabel)
                }
            }
        if (exercise == null || exercise.instructions.isEmpty())
            item {
                Text(
                    "No verified guide match for this saved name. Use guidance for your exact movement and equipment."
                )
            }
        else {
            item {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Equipment: ${exercise.equipment.joinToString().ifEmpty { "Not specified" }}"
                        )
                        Text("Primary muscles: ${exercise.primaryMuscles.joinToString()}")
                        if (exercise.secondaryMuscles.isNotEmpty())
                            Text("Secondary muscles: ${exercise.secondaryMuscles.joinToString()}")
                        Text("${exercise.difficulty} · ${exercise.category}")
                    }
                }
            }
            items(exercise.instructions.withIndex().toList(), key = { it.index }) {
                (index, instruction) ->
                Text("${index + 1}. ${instruction.replace(Regex("\\s+"), " ")}")
            }
            item {
                Text(
                    "Source: Free Exercise DB by yuhonas and contributors\nhttps://github.com/yuhonas/free-exercise-db\nLicense: Unlicense",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            HorizontalDivider()
            Text("Training reminders", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use a load you can control through a comfortable range of motion.\n\nKeep each repetition smooth and stop the set if form breaks down.\n\nStop if the movement causes sharp pain."
            )
        }
    }
}
