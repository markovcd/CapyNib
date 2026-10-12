package io.github.markovcd.quicknib.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.markovcd.quicknib.core.note.NoteColor

/** The one place that knows what each note color looks like, in light and dark themes. */
object NoteColors {
    private val light = mapOf(
        NoteColor.Coral to Color(0xFFFAAFA8),
        NoteColor.Peach to Color(0xFFF39F76),
        NoteColor.Sand to Color(0xFFFFF8B8),
        NoteColor.Mint to Color(0xFFE2F6D3),
        NoteColor.Sage to Color(0xFFB4DDD3),
        NoteColor.Fog to Color(0xFFD4E4ED),
        NoteColor.Storm to Color(0xFFAECCDC),
        NoteColor.Dusk to Color(0xFFD3BFDB),
        NoteColor.Blossom to Color(0xFFF6E2DD),
        NoteColor.Clay to Color(0xFFE9E3D4),
        NoteColor.Chalk to Color(0xFFEFEFF1),
    )

    private val dark = mapOf(
        NoteColor.Coral to Color(0xFF77172E),
        NoteColor.Peach to Color(0xFF692B17),
        NoteColor.Sand to Color(0xFF7C4A03),
        NoteColor.Mint to Color(0xFF264D3B),
        NoteColor.Sage to Color(0xFF0C625D),
        NoteColor.Fog to Color(0xFF256377),
        NoteColor.Storm to Color(0xFF284255),
        NoteColor.Dusk to Color(0xFF472E5B),
        NoteColor.Blossom to Color(0xFF6C394F),
        NoteColor.Clay to Color(0xFF4B443A),
        NoteColor.Chalk to Color(0xFF232427),
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
