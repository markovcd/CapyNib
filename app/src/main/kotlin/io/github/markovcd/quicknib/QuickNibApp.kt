package io.github.markovcd.quicknib

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.markovcd.quicknib.sync.NetworkWatcher
import io.github.markovcd.quicknib.sync.SyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class QuickNibApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        NetworkWatcher.start(this) { graph.scheduler.requestSync(immediate = true) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundSync())
    }

    /** While the app is on screen, pick up changes made elsewhere: at once, then every minute. */
    private inner class ForegroundSync : DefaultLifecycleObserver {
        private var polling: Job? = null

        override fun onStart(owner: LifecycleOwner) {
            polling = graph.scope.launch {
                while (isActive) {
                    graph.scheduler.requestSync(immediate = true)
                    delay(1.minutes)
                }
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            polling?.cancel()
            graph.scheduler.requestSync(immediate = true)
        }
    }
}
