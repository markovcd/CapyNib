package io.github.markovcd.capynib.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.core.memos.ApiResult
import io.github.markovcd.capynib.core.memos.ServerAddress
import io.github.markovcd.capynib.core.memos.SignInResult
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.sync.SyncStatus
import io.github.markovcd.capynib.settings.SyncSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    private val saved = graph.settings.settings.value

    val server = MutableStateFlow(saved.server?.toString().orEmpty())
    val token = MutableStateFlow(saved.token)

    private val mutableMessage = MutableStateFlow<SettingsMessage>(SettingsMessage.NotConnected)
    val message: StateFlow<SettingsMessage> = mutableMessage.asStateFlow()

    val status: StateFlow<SyncStatus> = graph.scheduler.status

    private val mutableSignInPage = MutableStateFlow<String?>(null)

    /** A sign-in page for the screen to open in the browser, until it says it has. */
    val signInPage: StateFlow<String?> = mutableSignInPage.asStateFlow()

    private var signingIn: Job? = null

    val isConnected: Boolean get() = graph.settings.settings.value.isConfigured

    init {
        if (saved.isConfigured) check(saved)
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
            val result = signIn.signIn(provider) { mutableSignInPage.value = it }
            mutableSignInPage.value = null
            mutableMessage.value = when (result) {
                is SignInResult.SignedIn -> {
                    token.value = result.accessToken
                    graph.settings.save(SyncSettings(address, result.accessToken))
                    graph.scheduler.requestSync(immediate = true)
                    SettingsMessage.Connected(result.username)
                }
                is SignInResult.Refused -> SettingsMessage.SignInRefused(result.reason)
                is SignInResult.Failed -> SettingsMessage.Failed(result.failure)
            }
        }
    }

    fun signInPageOpened() {
        mutableSignInPage.value = null
    }

    fun signInPageFailed() {
        signingIn?.cancel()
        mutableSignInPage.value = null
        mutableMessage.value = SettingsMessage.NoBrowser
    }

    fun disconnect() {
        graph.settings.save(SyncSettings.None)
        token.value = ""
        mutableMessage.value = SettingsMessage.NotConnected
        graph.scheduler.requestSync(immediate = true)
    }

    fun syncNow() = graph.scheduler.requestSync(immediate = true)

    private fun check(settings: SyncSettings, save: Boolean = false) {
        val api = graph.api(settings) ?: return
        mutableMessage.value = SettingsMessage.Checking
        viewModelScope.launch {
            val result = api.currentUser()
            mutableMessage.value = when (result) {
                is ApiResult.Ok -> SettingsMessage.Connected(result.value)
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
