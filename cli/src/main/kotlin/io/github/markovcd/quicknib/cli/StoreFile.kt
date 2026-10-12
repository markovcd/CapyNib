package io.github.markovcd.quicknib.cli

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.markovcd.quicknib.core.store.NoteStore
import java.util.Properties

/** Opens the notes database file the command line works on, creating or upgrading it. */
object StoreFile {
    fun open(path: String): NoteStore = NoteStore(JdbcSqliteDriver("jdbc:sqlite:$path", Properties(), NoteStore.schema))
}
