package com.hector.workouttracker.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.R

private val destinations =
    listOf("home" to "Today", "history" to "History", "stats" to "Stats", "settings" to "You")

@Composable
internal fun TrackerNavigationBar(route: String, navigate: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    Surface(
        Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (fontScale <= 1.3f) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    destinations.forEach { (key, label) ->
                        NavigationBarItem(
                            selected = route == key,
                            onClick = { navigate(key) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            icon = { DestinationIcon(key) },
                            label = { Text(label) },
                        )
                    }
                }
            } else {
                val columns = if (maxWidth.value >= 320f * fontScale) 4 else 2
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    destinations.chunked(columns).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { (key, label) ->
                                val selected = route == key
                                Surface(
                                    Modifier.weight(1f)
                                        .heightIn(min = 56.dp)
                                        .fillMaxHeight()
                                        .selectable(
                                            selected,
                                            role = Role.Tab,
                                            onClick = { navigate(key) },
                                        ),
                                    shape = RoundedCornerShape(18.dp),
                                    color =
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                    contentColor =
                                        if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        DestinationIcon(key)
                                        Text(
                                            label,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(key: String) {
    Icon(
        painterResource(
            when (key) {
                "home" -> R.drawable.ic_home
                "history" -> R.drawable.ic_history
                "stats" -> R.drawable.ic_stats
                else -> R.drawable.ic_person
            }
        ),
        null,
        Modifier.size(24.dp),
    )
}
