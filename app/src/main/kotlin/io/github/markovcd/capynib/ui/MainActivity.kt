package io.github.markovcd.capynib.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.CapynibApp
import io.github.markovcd.capynib.capture.CaptureIntents
import io.github.markovcd.capynib.capture.CapturedNote
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Notes made by quick captures, waiting for the screen to open them. */
    private val captured = Channel<CapturedNote>(Channel.UNLIMITED)

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
        val capture = CaptureIntents.capture(intent) ?: return
        val photos = CaptureIntents.sharedPhotos(intent, packageName)
        if (photos.isEmpty()) {
            captured.trySend(CapturedNote(graph.capture(capture), capture.opening))
            return
        }
        // Read the shared images while the sharing app's grant lasts, which is as long as this activity.
        lifecycleScope.launch { graph.capture(capture, photos)?.let { captured.trySend(CapturedNote(it)) } }
    }
}
