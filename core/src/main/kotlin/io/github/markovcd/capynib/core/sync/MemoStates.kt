package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.note.MemoCodec
import io.github.markovcd.capynib.core.note.MemoContent
import io.github.markovcd.capynib.core.note.Note

/** The memo a note is written to the server as. Memos has no trash, so a note in the trash is archived there (ADR 0006). */
fun Note.toMemoState(): MemoState =
    MemoState(MemoCodec.encode(MemoContent(title, body, color)), pinned, archived || inTrash)
