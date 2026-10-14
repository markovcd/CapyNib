package io.github.markovcd.capynib.cli

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.store.RecordingFiles
import java.io.File
import java.util.Properties

/** Opens the notes database file the command line works on, creating or upgrading it; recordings sit in a folder beside it. */
object StoreFile {
    fun open(path: String): NoteStore =
        NoteStore(JdbcSqliteDriver("jdbc:sqlite:$path", Properties(), NoteStore.schema), RecordingFiles(File("$path-recordings")))
}
