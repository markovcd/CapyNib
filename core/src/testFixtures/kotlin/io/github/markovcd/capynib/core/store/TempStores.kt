package io.github.markovcd.capynib.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

/** Opens stores in files under a JUnit temp folder, with readable sequential ids. */
class TempStores(private val folder: TemporaryFolder) {
    private var opened = 0

    fun open(idPrefix: String = "n", dispatcher: CoroutineDispatcher = Dispatchers.IO): NoteStore {
        opened++
        var next = 0
        return open(folder.newFile("notes-$opened.db").absolutePath, { "$idPrefix${++next}" }, dispatcher, folder.newFolder("recordings-$opened"))
    }

    companion object {
        /** A store at [path], its recordings in [recordings] or, by default, beside it. */
        fun open(path: String, newId: () -> String, dispatcher: CoroutineDispatcher = Dispatchers.IO, recordings: File = File("$path-recordings")) =
            NoteStore(JdbcSqliteDriver("jdbc:sqlite:$path", Properties(), NoteStore.schema), RecordingFiles(recordings), newId, dispatcher)
    }
}
