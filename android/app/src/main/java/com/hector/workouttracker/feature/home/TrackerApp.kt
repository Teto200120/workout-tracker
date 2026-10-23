package com.hector.workouttracker.feature.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.hector.workouttracker.R
import com.hector.workouttracker.core.designsystem.InteractionTheme
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.backup.ImportDialog
import com.hector.workouttracker.feature.help.*
import com.hector.workouttracker.feature.history.*
import com.hector.workouttracker.feature.onboarding.SetupScreen
import com.hector.workouttracker.feature.routines.RoutinesScreen
import com.hector.workouttracker.feature.schedule.ScheduleScreen
import com.hector.workouttracker.feature.session.*
import com.hector.workouttracker.feature.settings.PreferencesScreen
import com.hector.workouttracker.feature.settings.SettingsScreen
import com.hector.workouttracker.feature.stats.StatsScreen
import kotlinx.serialization.json.*

@Composable
fun TrackerApp(vm: TrackerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val historyVm: HistoryViewModel = viewModel()
    val helpVm: HelpViewModel = viewModel()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    CompositionLocalProvider(LocalHelp provides helpVm) {
        InteractionTheme(state.interactions) {
            state.importPreview?.let { ImportDialog(it, vm::confirmImport, vm::cancelImport) }
            Scaffold(
                bottomBar = {
                    if (
                        !state.loading &&
                            state.profile.displayName.isNotEmpty() &&
                            route !in
                                listOf(
                                    "session",
                                    "routines",
                                    "schedule",
                                    "workout",
                                    "preferences",
                                    "help",
                                )
                    ) {
                        NavigationBar {
                            listOf(
                                    "home" to "Today",
                                    "history" to "History",
                                    "stats" to "Stats",
                                    "settings" to "You",
                                )
                                .forEach { (key, label) ->
                                    NavigationBarItem(
                                        selected = route == key,
                                        onClick = {
                                            nav.navigate(key) {
                                                popUpTo(nav.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                painterResource(
                                                    when (key) {
                                                        "home" -> R.drawable.ic_home
                                                        "history" -> R.drawable.ic_history
                                                        "stats" -> R.drawable.ic_stats
                                                        else -> R.drawable.ic_person
                                                    }
                                                ),
                                                contentDescription = null,
                                            )
                                        },
                                        label = { Text(label) },
                                    )
                                }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    state.error?.let { message ->
                        Card(
                            Modifier.fillMaxWidth().padding(12.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                                if (state.pending)
                                    TextButton(onClick = vm::retryDraft, enabled = !state.busy) {
                                        Text("Retry saving")
                                    }
                                TextButton(onClick = vm::clearError) { Text("Dismiss") }
                            }
                        }
                    }
                    when {
                        state.loading ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        state.profile.displayName.isEmpty() -> SetupScreen(state, vm)
                        else ->
                            NavHost(
                                nav,
                                startDestination = "home",
                                modifier = Modifier.weight(1f),
                                enterTransition = {
                                    if (state.interactions.animations) fadeIn()
                                    else EnterTransition.None
                                },
                                exitTransition = {
                                    if (state.interactions.animations) fadeOut()
                                    else ExitTransition.None
                                },
                            ) {
                                composable("home") {
                                    HomeScreen(
                                        state,
                                        vm,
                                        { nav.navigate("routines") { launchSingleTop = true } },
                                        { nav.navigate("schedule") { launchSingleTop = true } },
                                    ) {
                                        nav.navigate("session") { launchSingleTop = true }
                                    }
                                }
                                composable("history") {
                                    HistoryScreen(state) { record ->
                                        historyVm.open(record)
                                        nav.navigate("workout") { launchSingleTop = true }
                                    }
                                }
                                composable("stats") { StatsScreen() }
                                composable("workout") {
                                    HistoryWorkoutScreen(state, historyVm) { nav.popBackStack() }
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        state,
                                        vm,
                                        { nav.navigate("schedule") { launchSingleTop = true } },
                                        { nav.navigate("preferences") { launchSingleTop = true } },
                                        { nav.navigate("help") { launchSingleTop = true } },
                                    )
                                }
                                composable("help") { HelpScreen { nav.popBackStack() } }
                                composable("preferences") {
                                    PreferencesScreen({ nav.popBackStack() })
                                }
                                composable("routines") {
                                    RoutinesScreen(state, { nav.popBackStack() })
                                }
                                composable("schedule") {
                                    ScheduleScreen(state, { nav.popBackStack() })
                                }
                                composable("session") {
                                    LaunchedEffect(state.draft, state.busy) {
                                        if (state.draft == null && !state.busy) nav.popBackStack()
                                    }
                                    SessionScreen(state, vm) { nav.popBackStack() }
                                }
                            }
                    }
                }
            }
        }
    }
}
