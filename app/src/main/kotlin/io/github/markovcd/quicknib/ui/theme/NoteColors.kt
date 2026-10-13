package io.github.markovcd.quicknib.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.markovcd.quicknib.core.note.NoteColor

/** The one place that knows what each note color looks like, in light and dark themes. */
object NoteColors {
    internal val light = mapOf(
        NoteColor.Coral to Color(0xFFFFC7BE),
        NoteColor.Peach to Color(0xFFFFE0C8),
        NoteColor.Sand to Color(0xFFFFECC4),
        NoteColor.Mint to Color(0xFFB9EAD4),
        NoteColor.Sage to Color(0xFFD7E1C1),
        NoteColor.Fog to Color(0xFFE7F3F9),
        NoteColor.Storm to Color(0xFFB2CEF5),
        NoteColor.Dusk to Color(0xFFE1D7FF),
        NoteColor.Blossom to Color(0xFFFFE0EC),
        NoteColor.Clay to Color(0xFFE7BAA6),
        NoteColor.Chalk to Color(0xFFFCF2E7),
    )

    internal val dark = mapOf(
        NoteColor.Coral to Color(0xFF683A33),
        NoteColor.Peach to Color(0xFF6C4520),
        NoteColor.Sand to Color(0xFF655012),
        NoteColor.Mint to Color(0xFF274B3D),
        NoteColor.Sage to Color(0xFF3D4531),
        NoteColor.Fog to Color(0xFF4D565A),
        NoteColor.Storm to Color(0xFF253A54),
        NoteColor.Dusk to Color(0xFF423C5A),
        NoteColor.Blossom to Color(0xFF644152),
        NoteColor.Clay to Color(0xFF482F23),
        NoteColor.Chalk to Color(0xFF5E5952),
    )

    /** The background of a note card and of the open note. Default follows the theme's surface. */
    @Composable
    @ReadOnlyComposable
    fun background(color: NoteColor): Color {
        val surface = MaterialTheme.colorScheme.surface
        return (if (surface.luminance() < 0.5f) dark else light)[color] ?: surface
    }

    /** Whether a card of this color needs an outline to stand out from the board. */
    fun needsOutline(color: NoteColor): Boolean = color == NoteColor.Default
}
