package ru.vsu.csschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Brick,
    onPrimary = Paper,
    secondary = Forest,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainer = Sand,
    surfaceContainerLow = Paper,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    outline = Mist,
)

private val DarkColors = darkColorScheme(
    primary = DarkBrick,
    secondary = Forest,
    background = DarkPaper,
    onBackground = Paper,
    surface = DarkSurface,
    onSurface = Paper,
    surfaceContainer = DarkSurface,
    surfaceContainerLow = DarkInk,
    secondaryContainer = Ink,
    onSecondaryContainer = Paper,
    outline = DarkMist,
)

@Composable
fun CSScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
