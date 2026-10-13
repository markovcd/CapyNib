package io.github.markovcd.capynib.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/** Calls back when the phone gets a network, so edits made offline go out as soon as they can. */
object NetworkWatcher {
    fun start(context: Context, onNetwork: () -> Unit) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onNetwork()
            },
        )
    }
}
