package com.hector.workouttracker.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFF8A68),
        onPrimary = Color(0xFF27130C),
        primaryContainer = Color(0xFF633021),
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
        background = Color(0xFFF7F6F3),
        onBackground = Color(0xFF111827),
        surface = Color(0xFFF7F6F3),
        onSurface = Color(0xFF111827),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F0EE),
        surfaceContainer = Color(0xFFE9ECEF),
        surfaceContainerHigh = Color(0xFFE1E5E9),
        surfaceContainerHighest = Color(0xFFD8DFE5),
        surfaceDim = Color(0xFFD0D8E5),
        surfaceBright = Color(0xFFF7F9FF),
        surfaceVariant = Color(0xFFDCE3EF),
        outlineVariant = Color(0xFFC1CDDD),
        onSurfaceVariant = Color(0xFF44546B),
        outline = Color(0xFF65758B),
    )

private val TrackerTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-1).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-.7).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-.5).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-.4).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                letterSpacing = (-.3).sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
    )

private val TrackerShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Expressive theme stays branded; wallpaper colors never silently replace the palette. */
@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = TrackerTypography,
        shapes = TrackerShapes,
        content = content,
    )
}
