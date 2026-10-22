package com.hector.workouttracker.feature.home

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
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.backup.ImportDialog
import com.hector.workouttracker.feature.history.HistoryScreen
import com.hector.workouttracker.feature.onboarding.SetupScreen
import com.hector.workouttracker.feature.routines.RoutinesScreen
import com.hector.workouttracker.feature.schedule.ScheduleScreen
import com.hector.workouttracker.feature.session.*
import com.hector.workouttracker.feature.settings.SettingsScreen
import kotlinx.serialization.json.*

@Composable
fun TrackerApp(vm: TrackerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    state.importPreview?.let { ImportDialog(it, vm::confirmImport, vm::cancelImport) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    Scaffold(
        bottomBar = {
            if (
                !state.loading &&
                    state.profile.displayName.isNotEmpty() &&
                    route !in listOf("session", "routines", "schedule")
            ) {
                NavigationBar {
                    listOf("home" to "Today", "history" to "History", "settings" to "You")
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
                    NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
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
                        composable("history") { HistoryScreen(state) }
                        composable("settings") {
                            SettingsScreen(state, vm) {
                                nav.navigate("schedule") { launchSingleTop = true }
                            }
                        }
                        composable("routines") { RoutinesScreen(state, { nav.popBackStack() }) }
                        composable("schedule") { ScheduleScreen(state, { nav.popBackStack() }) }
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
