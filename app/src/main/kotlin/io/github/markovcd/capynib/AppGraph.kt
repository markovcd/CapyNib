package io.github.markovcd.capynib

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.markovcd.capynib.core.memos.BrowserSignIn
import io.github.markovcd.capynib.core.memos.HttpMemosApi
import io.github.markovcd.capynib.core.memos.MemosApi
import io.github.markovcd.capynib.core.memos.ServerAddress
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.sync.SyncEngine
import io.github.markovcd.capynib.core.sync.SyncScheduler
import io.github.markovcd.capynib.settings.KeystoreTokenCipher
import io.github.markovcd.capynib.settings.SyncSettings
import io.github.markovcd.capynib.settings.SyncSettingsStore
import io.github.markovcd.capynib.settings.TokenCipher
import io.github.markovcd.capynib.settings.TrashSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's long-lived objects, made once per process. Tests make their own with an in-memory
 * database (null [databaseName]), a fake cipher, a fake server, canned sign-in replies and a browser
 * that only records the pages it is asked to open.
 */
class AppGraph(
    context: Context,
    databaseName: String? = "capynib.db",
    cipher: TokenCipher = KeystoreTokenCipher(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val clock: () -> Long = System::currentTimeMillis,
    private val apiFactory: (ServerAddress, String) -> MemosApi = ::HttpMemosApi,
    private val signInFactory: (ServerAddress) -> BrowserSignIn = { BrowserSignIn(it, "Capynib on ${Build.MODEL}") },
    private val browser: (String) -> Boolean = { openInBrowser(context, it) },
) {
    val store = NoteStore(AndroidSqliteDriver(NoteStore.schema, context, databaseName))

    val settings = SyncSettingsStore(context.getSharedPreferences("sync", Context.MODE_PRIVATE), cipher)

    val trash = TrashSettingsStore(context.getSharedPreferences("trash", Context.MODE_PRIVATE))

    val scheduler = SyncScheduler(scope, clock) { engine() }

    /** A client for [settings], or null when they name no usable server. */
    fun api(settings: SyncSettings): MemosApi? {
        val server = settings.server ?: return null
        return if (settings.isConfigured) apiFactory(server, settings.token) else null
    }

    fun browserSignIn(server: ServerAddress): BrowserSignIn = signInFactory(server)

    /** Opens [page] in the browser; false when the phone has none. */
    fun openPage(page: String): Boolean = browser(page)

    /** Deletes for good the notes that have been in the trash as long as the user chose; asks for a sync when any were. */
    fun emptyTrash() {
        if (store.emptyTrash(clock(), trash.days.value) > 0) scheduler.requestSync()
    }

    private fun engine(): SyncEngine? = api(settings.settings.value)?.let { SyncEngine(store, it, trash.days.value, clock) }
}

private fun openInBrowser(context: Context, page: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
