package io.github.markovcd.capynib.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.CapynibApp
import io.github.markovcd.capynib.capture.CaptureIntents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    /** Notes made by quick captures, waiting for the screen to open them. */
    private val captured = Channel<String>(Channel.UNLIMITED)

    private val graph: AppGraph get() = (application as CapynibApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) capture(intent)
        val opens = captured.receiveAsFlow()
        setContent { CapynibRoot(graph, opens) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        capture(intent)
    }

    private fun capture(intent: Intent) {
        CaptureIntents.capture(intent)?.let { captured.trySend(graph.capture(it)) }
    }
}
