package com.hector.workouttracker.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.R

@Composable
internal fun TrackerNavigationBar(route: String, navigate: (String) -> Unit) {
    val selected = if (route == "history") "stats" else route
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Surface(
        Modifier.fillMaxWidth(),
        color = if (dark) Color(0xFF1B2C42) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .selectableGroup()
                        .testTag("primary-navigation"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("stats" to "Stats", "home" to "Home", "settings" to "Profile").forEach {
                        (key, label) ->
                        val active = selected == key
                        val background by
                            animateColorAsState(
                                if (active) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                label = "navigation-background-$key",
                            )
                        val foreground by
                            animateColorAsState(
                                if (active) MaterialTheme.colorScheme.onPrimary
                                else if (dark) Color(0xFF9AB6D4)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                label = "navigation-foreground-$key",
                            )
                        Surface(
                            Modifier.weight(1f)
                                .heightIn(min = 56.dp)
                                .fillMaxHeight()
                                .selectable(active, role = Role.Tab, onClick = { navigate(key) })
                                .testTag("nav-$key"),
                            shape = RoundedCornerShape(28.dp),
                            color = background,
                            contentColor = foreground,
                        ) {
                            Column(
                                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painterResource(
                                        when (key) {
                                            "home" -> R.drawable.ic_home
                                            "stats" -> R.drawable.ic_stats
                                            else -> R.drawable.ic_person
                                        }
                                    ),
                                    null,
                                    Modifier.size(22.dp),
                                )
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
