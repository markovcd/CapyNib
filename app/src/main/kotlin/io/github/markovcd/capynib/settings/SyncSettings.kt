package io.github.markovcd.capynib.settings

import io.github.markovcd.capynib.core.memos.ServerAddress

/** The memos server the app syncs with. Null server means notes stay on the phone. */
data class SyncSettings(val server: ServerAddress?, val token: String) {
    val isConfigured: Boolean get() = server != null && token.isNotEmpty()

    companion object {
        val None = SyncSettings(null, "")
    }
}
