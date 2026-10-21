package com.hector.workouttracker.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFF9B81),
        onPrimary = Color(0xFF381006),
        primaryContainer = Color(0xFF7D2A13),
        onPrimaryContainer = Color(0xFFFFDAD0),
        secondary = Color(0xFFA9C8EF),
        onSecondary = Color(0xFF102C49),
        secondaryContainer = Color(0xFF22354F),
        onSecondaryContainer = Color(0xFFD7E7FF),
        background = Color(0xFF070B14),
        onBackground = Color(0xFFF8FAFC),
        surface = Color(0xFF111827),
        onSurface = Color(0xFFF8FAFC),
        surfaceContainerLowest = Color(0xFF070B14),
        surfaceContainerLow = Color(0xFF111827),
        surfaceContainer = Color(0xFF172033),
        surfaceContainerHigh = Color(0xFF202B42),
        surfaceContainerHighest = Color(0xFF2C3951),
        surfaceDim = Color(0xFF0B111D),
        surfaceBright = Color(0xFF29354C),
        surfaceVariant = Color(0xFF202B42),
        outlineVariant = Color(0xFF334155),
        onSurfaceVariant = Color(0xFFB8C5D8),
        outline = Color(0xFF8A9BB2),
    )
private val LightColors =
    lightColorScheme(
        primary = Color(0xFFA53617),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD0),
        onPrimaryContainer = Color(0xFF381006),
        secondary = Color(0xFF385C82),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7E7FF),
        onSecondaryContainer = Color(0xFF102C49),
        background = Color(0xFFF7F9FF),
        onBackground = Color(0xFF111827),
        surface = Color(0xFFF7F9FF),
        onSurface = Color(0xFF111827),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F4FB),
        surfaceContainer = Color(0xFFECF0F8),
        surfaceContainerHigh = Color(0xFFE2E8F2),
        surfaceContainerHighest = Color(0xFFDCE3EF),
        surfaceDim = Color(0xFFD0D8E5),
        surfaceBright = Color(0xFFF7F9FF),
        surfaceVariant = Color(0xFFDCE3EF),
        outlineVariant = Color(0xFFC1CDDD),
        onSurfaceVariant = Color(0xFF44546B),
        outline = Color(0xFF65758B),
    )

/** Expressive theme stays branded; wallpaper colors never silently replace the palette. */
@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
