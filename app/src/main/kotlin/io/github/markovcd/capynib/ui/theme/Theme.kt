package io.github.markovcd.capynib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Seed #2E6B65, Tonal Spot. No dynamic color: the cards carry the color, the chrome stays calm.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF006A63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF2E8),
    onPrimaryContainer = Color(0xFF00504A),
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF324B48),
    background = Color(0xFFF4FBF9),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF9),
    onSurface = Color(0xFF161D1C),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    error = Color(0xFFBA1A1A),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF81D5CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF9DF2E8),
    secondaryContainer = Color(0xFF324B48),
    onSecondaryContainer = Color(0xFFCCE8E4),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E2),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerHigh = Color(0xFF252B2A),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CapynibTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CapynibType.typography,
        content = content,
    )
}
