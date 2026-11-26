package com.example.walkrun.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.walkrun.data.ThemeMode
import androidx.compose.ui.graphics.Color

/** Main brand color: dark blue. Also used for the route line on the map. */
val DarkBlue = Color(0xFF0D47A1)

private val Navy = Color(0xFF0A2A5C)
private val Background = Color(0xFFF6F8FC)

/**
 * Dark blue theme. The neutral surfaces have a light blue tint, so no purple
 * from the Material default remains (cards, top bar, switches).
 */
private val WalkRunColors = lightColorScheme(
    primary = DarkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3F8),
    onPrimaryContainer = Navy,
    inversePrimary = Color(0xFFA9C7F5),
    secondary = Color(0xFF34507A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5F3),
    onSecondaryContainer = Navy,
    tertiary = Color(0xFF00658E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC7E7FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = Background,
    onBackground = Color(0xFF181C22),
    surface = Background,
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFDFE3EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceTint = DarkBlue,
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3F9),
    surfaceContainer = Color(0xFFEAEEF5),
    surfaceContainerHigh = Color(0xFFE4E8F0),
    surfaceContainerHighest = Color(0xFFDEE3EB),
    surfaceDim = Color(0xFFD7DBE3),
    surfaceBright = Background
)

/** Dark theme: deep navy surfaces, light blue accents. */
private val WalkRunDarkColors = darkColorScheme(
    primary = Color(0xFFA9C7F5),
    onPrimary = Color(0xFF0A2A5C),
    primaryContainer = Color(0xFF16407E),
    onPrimaryContainer = Color(0xFFD5E3F8),
    inversePrimary = DarkBlue,
    secondary = Color(0xFFB8C8E3),
    onSecondary = Color(0xFF213551),
    secondaryContainer = Color(0xFF34507A),
    onSecondaryContainer = Color(0xFFDCE5F3),
    tertiary = Color(0xFF85CFFF),
    onTertiary = Color(0xFF00344B),
    tertiaryContainer = Color(0xFF004C6C),
    onTertiaryContainer = Color(0xFFC7E7FF),
    background = Color(0xFF0E1420),
    onBackground = Color(0xFFE1E6EF),
    surface = Color(0xFF0E1420),
    onSurface = Color(0xFFE1E6EF),
    surfaceVariant = Color(0xFF3A4250),
    onSurfaceVariant = Color(0xFFC3C9D4),
    surfaceTint = Color(0xFFA9C7F5),
    outline = Color(0xFF8D94A0),
    outlineVariant = Color(0xFF3A4250),
    surfaceContainerLowest = Color(0xFF090E17),
    surfaceContainerLow = Color(0xFF141B28),
    surfaceContainer = Color(0xFF18202E),
    surfaceContainerHigh = Color(0xFF212A39),
    surfaceContainerHighest = Color(0xFF2B3444),
    surfaceDim = Color(0xFF0E1420),
    surfaceBright = Color(0xFF333C4C)
)

/** True when the app shows its dark theme. Used by the map (dark tiles) and the system bars. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun WalkRunTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = if (dark) WalkRunDarkColors else WalkRunColors, content = content)
    }
}
