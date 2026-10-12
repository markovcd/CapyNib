package io.github.markovcd.quicknib.core.note

/**
 * The background a note is shown on. [key] is written to the database and to memos
 * (as the tag `#color/<key>`), so it never changes once shipped.
 */
enum class NoteColor(val key: String) {
    Default("default"),
    Coral("coral"),
    Peach("peach"),
    Sand("sand"),
    Mint("mint"),
    Sage("sage"),
    Fog("fog"),
    Storm("storm"),
    Dusk("dusk"),
    Blossom("blossom"),
    Clay("clay"),
    Chalk("chalk"),
    ;

    companion object {
        /** An unknown key reads as [Default] rather than failing: it may come from a newer version. */
        fun fromKey(key: String): NoteColor = entries.firstOrNull { it.key == key } ?: Default

        fun parseKey(key: String): NoteColor? = entries.firstOrNull { it.key == key }
    }
}
