package io.github.markovcd.capynib

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.markovcd.capynib.audio.AndroidMicrophone
import io.github.markovcd.capynib.audio.AndroidPlayback
import io.github.markovcd.capynib.audio.Microphone
import io.github.markovcd.capynib.audio.Playback
import io.github.markovcd.capynib.core.memos.BrowserSignIn
import io.github.markovcd.capynib.core.memos.HttpMemosApi
import io.github.markovcd.capynib.core.memos.MemosApi
import io.github.markovcd.capynib.core.memos.ServerAddress
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.core.store.AttachmentFiles
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.sync.SyncEngine
import io.github.markovcd.capynib.core.sync.SyncScheduler
import io.github.markovcd.capynib.photo.PhotoCopier
import io.github.markovcd.capynib.photo.PhotoLoader
import io.github.markovcd.capynib.settings.KeystoreTokenCipher
import io.github.markovcd.capynib.settings.SyncSettings
import io.github.markovcd.capynib.settings.SyncSettingsStore
import io.github.markovcd.capynib.settings.TokenCipher
import io.github.markovcd.capynib.settings.TrashSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The app's long-lived objects, made once per process. Tests make their own with an in-memory
 * database (null [databaseName]), a fake cipher, a fake server, canned sign-in replies, a browser
 * that only records the pages it is asked to open, a fake microphone and speaker, and photo files lent to
 * the camera app by path, since the file provider keeps the first test's folders for the rest.
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
    val microphone: Microphone = AndroidMicrophone(context),
    playback: ((String) -> File) -> Playback = { AndroidPlayback(MainScope(), it) },
    private val lend: (File) -> Uri = { FileProvider.getUriForFile(context, "${context.packageName}.photos", it) },
) {
    val store = NoteStore(
        AndroidSqliteDriver(NoteStore.schema, context, databaseName),
        AttachmentFiles(File(context.filesDir, "attachments"), legacy = File(context.filesDir, "recordings")),
    )

    val playback: Playback = playback(store.attachments::file)

    val photos = PhotoLoader(store.attachments::file)

    private val cache = context.cacheDir

    val resolver = context.contentResolver

    private val copier = PhotoCopier(::newPhotoFile, resolver)

    val settings = SyncSettingsStore(context.getSharedPreferences("sync", Context.MODE_PRIVATE), cipher)

    val trash = TrashSettingsStore(context.getSharedPreferences("trash", Context.MODE_PRIVATE))

    val scheduler = SyncScheduler(scope, clock) { engine() }

    /** A client for [settings], or null when they name no usable server. */
    fun api(settings: SyncSettings): MemosApi? {
        val server = settings.server ?: return null
        return if (settings.isConfigured) apiFactory(server, settings.token) else null
    }

    fun browserSignIn(server: ServerAddress): BrowserSignIn = signInFactory(server)

    /** A file in the cache for a clip being recorded; the store moves it in once the clip is kept. */
    fun newClipFile(): File = File.createTempFile("clip", ".m4a", cache)

    /** A file in the cache for a photo on its way into a note; the store moves it in once the note takes it. */
    fun newPhotoFile(): File = File.createTempFile("photo", ".jpg", File(cache, "photos").apply { mkdirs() })

    /** The address the camera app writes [file] through; `res/xml/photo_paths.xml` lends it the folder. */
    fun photoUri(file: File): Uri = lend(file)

    /** Opens [page] in the browser; false when the phone has none. */
    fun openPage(page: String): Boolean = browser(page)

    /** Makes a note from [capture] and returns its id to open it; one that already holds text is synced at once. */
    fun capture(capture: Capture): String {
        val note = store.create(clock(), title = capture.title, body = capture.body)
        if (!note.isBlank) scheduler.requestSync()
        return note.id
    }

    /**
     * Makes a note from [capture] holding copies of the images at [photos], another app's, and returns
     * its id to open it; null when none could be read and the capture holds nothing else.
     */
    suspend fun capture(capture: Capture, photos: List<Uri>): String? {
        val copied = photos.mapNotNull { copier.copy(it) }
        if (copied.isEmpty() && capture == Capture.EmptyNote) return null
        val note = store.create(clock(), title = capture.title, body = capture.body)
        for (photo in copied) store.attachments.addPhoto(store.attachments.newPhoto(note.id, photo.type, clock()), photo.file)
        scheduler.requestSync()
        return note.id
    }

    /** Deletes for good the notes that have been in the trash as long as the user chose; asks for a sync when any were. */
    fun emptyTrash() {
        if (store.emptyTrash(clock(), trash.days.value) > 0) scheduler.requestSync()
    }

    private fun engine(): SyncEngine? {
        val current = settings.settings.value
        val server = current.server ?: return null
        return api(current)?.let { SyncEngine(store, it, server, trash.days.value, clock) }
    }
}

private fun openInBrowser(context: Context, page: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
