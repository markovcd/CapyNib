package io.github.markovcd.quicknib.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.markovcd.quicknib.core.note.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteColorsTest {
    private val colored = NoteColor.entries - NoteColor.Default

    @Test
    fun Every_note_color_but_default_has_a_fill_in_both_themes() {
        assertEquals(colored.toSet(), NoteColors.light.keys)
        assertEquals(colored.toSet(), NoteColors.dark.keys)
    }

    @Test
    fun Card_text_is_readable_on_every_note_color_in_both_themes() {
        val fails = listOf(NoteColors.light to LightColors.onSurface, NoteColors.dark to DarkColors.onSurface)
            .flatMap { (fills, text) -> fills.map { (color, fill) -> color to contrast(fill, text) } }
            .filter { (_, ratio) -> ratio < 4.5 }
        assertEquals("below 4.5:1: $fails", emptyList<Pair<NoteColor, Double>>(), fails)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }
}
