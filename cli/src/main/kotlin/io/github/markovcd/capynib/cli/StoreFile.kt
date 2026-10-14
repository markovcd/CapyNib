package io.github.markovcd.capynib.cli

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.markovcd.capynib.core.store.AttachmentFiles
import io.github.markovcd.capynib.core.store.NoteStore
import java.io.File
import java.util.Properties

/** Opens the notes database file the command line works on, creating or upgrading it; recordings and photos sit in a folder beside it. */
object StoreFile {
    fun open(path: String): NoteStore =
        NoteStore(JdbcSqliteDriver("jdbc:sqlite:$path", Properties(), NoteStore.schema), AttachmentFiles(File("$path-attachments"), legacy = File("$path-recordings")))
}
