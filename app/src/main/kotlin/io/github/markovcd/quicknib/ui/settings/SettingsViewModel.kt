package io.github.markovcd.quicknib.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.quicknib.AppGraph
import io.github.markovcd.quicknib.core.memos.ApiResult
import io.github.markovcd.quicknib.core.memos.ServerAddress
import io.github.markovcd.quicknib.core.memos.SyncFailure
import io.github.markovcd.quicknib.core.sync.SyncStatus
import io.github.markovcd.quicknib.settings.SyncSettings
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
