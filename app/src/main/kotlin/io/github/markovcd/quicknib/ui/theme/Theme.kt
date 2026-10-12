package io.github.markovcd.quicknib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder palette until the design pass (docs/design-handoff.md): a warm yellow accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6D5E0F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8E287),
    onPrimaryContainer = Color(0xFF221B00),
    secondaryContainer = Color(0xFFEEE2BC),
    onSecondaryContainer = Color(0xFF211B04),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceContainerHigh = Color(0xFFF1EBDA),
    outlineVariant = Color(0xFFCDC6B4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDBC66E),
    onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF534600),
    onPrimaryContainer = Color(0xFFF8E287),
    secondaryContainer = Color(0xFF4E472A),
    onSecondaryContainer = Color(0xFFEEE2BC),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    surfaceContainerHigh = Color(0xFF2B2A25),
    outlineVariant = Color(0xFF4B4739),
)

@Composable
fun QuickNibTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
