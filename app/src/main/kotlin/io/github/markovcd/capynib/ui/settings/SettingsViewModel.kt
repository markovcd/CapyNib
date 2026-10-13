package io.github.markovcd.capynib.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.core.memos.ApiResult
import io.github.markovcd.capynib.core.memos.ServerAddress
import io.github.markovcd.capynib.core.memos.ServerCheck
import io.github.markovcd.capynib.core.memos.SignInResult
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.store.Trash
import io.github.markovcd.capynib.core.sync.SyncStatus
import io.github.markovcd.capynib.settings.SyncSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    private val atStart = graph.settings.settings.value

    val server = MutableStateFlow(atStart.server?.toString().orEmpty())
    val token = MutableStateFlow(atStart.token)

    private val mutableMessage = MutableStateFlow<SettingsMessage>(SettingsMessage.NotConnected)
    val message: StateFlow<SettingsMessage> = mutableMessage.asStateFlow()

    val status: StateFlow<SyncStatus> = graph.scheduler.status

    private var signingIn: Job? = null

    /** The trash days as typed; kept only while it is a number in [Trash.DAYS]. */
    val trashDays = MutableStateFlow(graph.trash.days.value.toString())

    /** What is saved; the screen offers Disconnect while it names a server, and the ways to connect otherwise. */
    val saved: StateFlow<SyncSettings> = graph.settings.settings

    init {
        if (atStart.isConfigured) check(atStart)
    }

    /**
     * Checks the address and token with the server and keeps them. An unreachable server is kept
     * too (the phone may be offline); a refused token or a server that is not memos is not.
     */
    fun connect() {
        val address = ServerAddress.parse(server.value)
        val settings = when {
            address == null -> return run { mutableMessage.value = SettingsMessage.BadAddress }
            token.value.isBlank() -> return run { mutableMessage.value = SettingsMessage.TokenMissing }
            else -> SyncSettings(address, token.value.trim())
        }
        check(settings, save = true)
    }

    /**
     * Signs in through the server's sign-in provider in the browser and keeps the access token the
     * server makes for the app. Tapping again starts over.
     */
    fun signInWithBrowser() {
        val address = ServerAddress.parse(server.value) ?: return run { mutableMessage.value = SettingsMessage.BadAddress }
        signingIn?.cancel()
        signingIn = viewModelScope.launch {
            mutableMessage.value = SettingsMessage.Checking
            val signIn = graph.browserSignIn(address)
            val provider = when (val providers = signIn.providers()) {
                is ApiResult.Ok -> providers.value.firstOrNull()
                    ?: return@launch run { mutableMessage.value = SettingsMessage.NoSignInProvider }
                is ApiResult.Failed -> return@launch run { mutableMessage.value = SettingsMessage.Failed(providers.failure) }
                ApiResult.NotFound, ApiResult.AlreadyExists ->
                    return@launch run { mutableMessage.value = SettingsMessage.Failed(SyncFailure.Server(404, "not a memos server")) }
            }
            mutableMessage.value = SettingsMessage.WaitingForBrowser(provider.title)
            val result = signIn.signIn(provider) { page ->
                if (!graph.openPage(page)) {
                    mutableMessage.value = SettingsMessage.NoBrowser
                    this@launch.cancel()
                }
            }
            mutableMessage.value = when (result) {
                is SignInResult.SignedIn -> {
                    token.value = result.accessToken
                    graph.settings.save(SyncSettings(address, result.accessToken))
                    graph.scheduler.requestSync(immediate = true)
                    SettingsMessage.Connected(address.host, result.username)
                }
                is SignInResult.Refused -> SettingsMessage.SignInRefused(result.reason)
                is SignInResult.Failed -> SettingsMessage.Failed(result.failure)
            }
        }
    }

    fun setTrashDays(text: String) {
        trashDays.value = text
        val days = Trash.parseDays(text) ?: return
        graph.trash.save(days)
        viewModelScope.launch(Dispatchers.IO) { graph.emptyTrash() }
    }

    /**
     * Tries every call sync makes against the saved server. The writes go to a memo of the check's
     * own, deleted at the end; the user's memos are not touched.
     */
    fun checkServer() {
        val api = graph.api(saved.value) ?: return
        mutableMessage.value = SettingsMessage.Checking
        viewModelScope.launch {
            mutableMessage.value = when (val result = ServerCheck(api).run()) {
                is ApiResult.Ok -> SettingsMessage.Checked(result.value)
                is ApiResult.Failed -> SettingsMessage.Failed(result.failure)
                ApiResult.NotFound, ApiResult.AlreadyExists -> SettingsMessage.Failed(SyncFailure.Server(404, "not a memos server"))
            }
        }
    }

    fun disconnect() {
        graph.settings.save(SyncSettings.None)
        token.value = ""
        mutableMessage.value = SettingsMessage.NotConnected
        graph.scheduler.requestSync(immediate = true)
    }

    private fun check(settings: SyncSettings, save: Boolean = false) {
        val api = graph.api(settings) ?: return
        mutableMessage.value = SettingsMessage.Checking
        viewModelScope.launch {
            val result = api.currentUser()
            mutableMessage.value = when (result) {
                is ApiResult.Ok -> SettingsMessage.Connected(settings.server?.host.orEmpty(), result.value)
                is ApiResult.Failed -> SettingsMessage.Failed(result.failure)
                ApiResult.NotFound, ApiResult.AlreadyExists -> SettingsMessage.Failed(SyncFailure.Server(404, "not a memos server"))
            }
            val keep = result is ApiResult.Ok || (result is ApiResult.Failed && result.failure is SyncFailure.Unreachable)
            if (save && keep) {
                graph.settings.save(settings)
                graph.scheduler.requestSync(immediate = true)
            }
        }
    }
}
