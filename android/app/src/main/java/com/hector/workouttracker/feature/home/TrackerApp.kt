package com.hector.workouttracker.feature.home

import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.hector.workouttracker.MainActivity
import com.hector.workouttracker.R
import com.hector.workouttracker.core.designsystem.InteractionTheme
import com.hector.workouttracker.core.designsystem.trackerScreenBackground
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.backup.ImportDialog
import com.hector.workouttracker.feature.feedback.FeedbackScreen
import com.hector.workouttracker.feature.help.*
import com.hector.workouttracker.feature.history.*
import com.hector.workouttracker.feature.onboarding.SetupScreen
import com.hector.workouttracker.feature.routines.RoutinesScreen
import com.hector.workouttracker.feature.schedule.ScheduleScreen
import com.hector.workouttracker.feature.session.*
import com.hector.workouttracker.feature.settings.PreferencesScreen
import com.hector.workouttracker.feature.settings.SettingsScreen
import com.hector.workouttracker.feature.stats.StatsScreen
import com.hector.workouttracker.feature.stats.StatsViewModel
import kotlinx.serialization.json.*

@Composable
fun TrackerApp(vm: TrackerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.restartAfterClear) {
        if (state.restartAfterClear) {
            vm.consumeClearRestart()
            // Finish the old task and its editor ViewModel stores after the atomic clear.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
    // The durable epoch also rejects old navigation restoration after process death mid-clear.
    key(state.dataEpoch) { TrackerAppContent(state, vm) }
}

@Composable
private fun TrackerAppContent(state: TrackerState, vm: TrackerViewModel) {
    val historyVm: HistoryViewModel =
        viewModel(key = if (state.dataEpoch == "initial") null else "history:${state.dataEpoch}")
    val helpVm: HelpViewModel =
        viewModel(key = if (state.dataEpoch == "initial") null else "help:${state.dataEpoch}")
    val statsVm: StatsViewModel = viewModel(key = "progress:${state.dataEpoch}")
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    CompositionLocalProvider(LocalHelp provides helpVm) {
        InteractionTheme(state.interactions) {
            state.importPreview?.let { ImportDialog(it, vm::confirmImport, vm::cancelImport) }
            val brandedVisible =
                route in listOf("home", "session", "history", "workout") &&
                    !state.loading &&
                    state.profile.displayName.isNotEmpty()
            Scaffold(
                modifier =
                    if (brandedVisible)
                        Modifier.background(
                            when (route) {
                                "session" -> sessionBackgroundBrush()
                                "history",
                                "workout" -> trackerScreenBackground()
                                else -> homeBackgroundBrush()
                            }
                        )
                    else Modifier,
                containerColor =
                    if (brandedVisible) Color.Transparent else MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
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
                                    "feedback",
                                )
                    ) {
                        Surface(
                            Modifier.navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 2.dp,
                        ) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            ) {
                                listOf(
                                        "home" to "Today",
                                        "history" to "History",
                                        "stats" to "Stats",
                                        "settings" to "You",
                                    )
                                    .forEach { (key, label) ->
                                        NavigationBarItem(
                                            colors =
                                                NavigationBarItemDefaults.colors(
                                                    indicatorColor =
                                                        MaterialTheme.colorScheme.primary,
                                                    selectedIconColor =
                                                        MaterialTheme.colorScheme.onPrimary,
                                                    selectedTextColor =
                                                        MaterialTheme.colorScheme.onSurface,
                                                ),
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
                },
            ) { padding ->
                Column(
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding()
                ) {
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
                                        statsVm,
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
                                composable("stats") { StatsScreen(statsVm) }
                                composable("workout") {
                                    HistoryWorkoutScreen(state, historyVm) { nav.popBackStack() }
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        state,
                                        vm,
                                        statsVm,
                                        { nav.navigate("schedule") { launchSingleTop = true } },
                                        { nav.navigate("preferences") { launchSingleTop = true } },
                                        { nav.navigate("help") { launchSingleTop = true } },
                                        { nav.navigate("feedback") { launchSingleTop = true } },
                                    )
                                }
                                composable("help") { HelpScreen { nav.popBackStack() } }
                                composable("feedback") { FeedbackScreen({ nav.popBackStack() }) }
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
