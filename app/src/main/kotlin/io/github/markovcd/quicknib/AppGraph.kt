package io.github.markovcd.quicknib

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.markovcd.quicknib.core.memos.HttpMemosApi
import io.github.markovcd.quicknib.core.memos.MemosApi
import io.github.markovcd.quicknib.core.memos.ServerAddress
import io.github.markovcd.quicknib.core.store.NoteStore
import io.github.markovcd.quicknib.core.sync.SyncEngine
import io.github.markovcd.quicknib.core.sync.SyncScheduler
import io.github.markovcd.quicknib.settings.KeystoreTokenCipher
import io.github.markovcd.quicknib.settings.SyncSettings
import io.github.markovcd.quicknib.settings.SyncSettingsStore
import io.github.markovcd.quicknib.settings.TokenCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's long-lived objects, made once per process. Tests make their own with an in-memory
 * database (null [databaseName]), a fake cipher and a fake server.
 */
class AppGraph(
    context: Context,
    databaseName: String? = "quicknib.db",
    cipher: TokenCipher = KeystoreTokenCipher(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val clock: () -> Long = System::currentTimeMillis,
    private val api: (ServerAddress, String) -> MemosApi = ::HttpMemosApi,
) {
    val store = NoteStore(AndroidSqliteDriver(NoteStore.schema, context, databaseName))

    val settings = SyncSettingsStore(context.getSharedPreferences("sync", Context.MODE_PRIVATE), cipher)

    val scheduler = SyncScheduler(scope, clock) { engine() }

    /** A client for [settings], or null when they name no usable server. */
    fun api(settings: SyncSettings): MemosApi? {
        val server = settings.server ?: return null
        return if (settings.isConfigured) api(server, settings.token) else null
    }

    private fun engine(): SyncEngine? = api(settings.settings.value)?.let { SyncEngine(store, it, clock) }
}
