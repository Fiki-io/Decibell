package com.decibell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DecibellDarkColorScheme = darkColorScheme(
    primary = DecibellColors.AccentBlue,
    onPrimary = DecibellColors.TextPrimary,
    secondary = DecibellColors.SurfaceInteractive,
    onSecondary = DecibellColors.TextPrimary,
    tertiary = DecibellColors.AccentBlue,
    background = DecibellColors.BackgroundPrimary,
    onBackground = DecibellColors.TextPrimary,
    surface = DecibellColors.SurfaceCard,
    onSurface = DecibellColors.TextPrimary,
    surfaceVariant = DecibellColors.SurfaceInteractive,
    onSurfaceVariant = DecibellColors.TextSecondary,
    outline = DecibellColors.BorderSubtle
)

@Composable
fun DecibellTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DecibellDarkColorScheme,
        typography = Typography,
        shapes = DecibellShapes,
        content = content
    )
}
