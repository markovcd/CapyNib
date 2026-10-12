package io.github.markovcd.quicknib.core.sync

import io.github.markovcd.quicknib.core.memos.MemoState
import io.github.markovcd.quicknib.core.note.MemoCodec
import io.github.markovcd.quicknib.core.note.MemoContent
import io.github.markovcd.quicknib.core.note.Note

/** The memo a note is written to the server as. */
fun Note.toMemoState(): MemoState =
    MemoState(MemoCodec.encode(MemoContent(title, body, color)), pinned, archived)
