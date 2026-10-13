package io.github.markovcd.capynib.core.note

/**
 * A note's tags: the `#tag`s in its title and body, found the way memos finds them in the memo, so
 * they are known offline and agree with the server. `#a/b` is the tag `a/b` inside the tag `a`.
 */
object NoteTags {
    /** The tags written in [note], in order, each once. The color is not a tag here, though memos lists it as one. */
    fun of(note: Note): List<String> = written(MemoCodec.encode(MemoContent(note.title, note.body, NoteColor.Default)))

    /** The tags memos lists for a memo with this [markdown]: each tag written, with the tags it is inside. */
    fun inMemo(markdown: String): List<String> = written(markdown).flatMap(::withParents).distinct()

    /** Whether [note] has [tag], written or as the parent of one written. */
    fun has(note: Note, tag: String): Boolean = of(note).any { it == tag || it.startsWith("$tag/") }

    /** `a/b/c` is in `a/b`, which is in `a`. */
    fun withParents(tag: String): List<String> =
        tag.indices.filter { tag[it] == '/' }.map { tag.substring(0, it) } + tag

    private fun written(markdown: String): List<String> =
        MemoText.literalRuns(markdown).flatMap { run -> TagLexer.find(run).map { it.value } }.distinct()
}
