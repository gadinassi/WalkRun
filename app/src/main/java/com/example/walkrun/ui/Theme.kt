package com.example.walkrun.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun WalkRunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WalkRunColors, content = content)
}
