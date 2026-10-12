package io.github.markovcd.quicknib.core.sync

import io.github.markovcd.quicknib.core.memos.SyncFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one sync at a time, when asked. A request made while typing waits [typingDelay] and takes
 * every request made meanwhile with it, so a note syncs about once per [typingDelay] while being
 * written. A failed sync is retried after each of [retryDelays] in turn, except when the server
 * refused the token, which only the user can fix.
 */
class SyncScheduler(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val typingDelay: Duration = 1.seconds,
    private val retryDelays: List<Duration> = listOf(5.seconds, 15.seconds, 1.minutes, 5.minutes),
    private val engine: () -> SyncEngine?,
) {
    private val requests = Channel<Boolean>(Channel.UNLIMITED)
    private val mutableStatus = MutableStateFlow<SyncStatus>(if (engine() == null) SyncStatus.Local else SyncStatus.Waiting)
    private val running = Mutex()
    private var retry: Job? = null
    private var failures = 0

    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch {
            for (immediate in requests) {
                if (!immediate) delay(typingDelay)
                do {
                    val more = requests.tryReceive().isSuccess
                } while (more)
                run()
            }
        }
    }

    /** Asks for a sync: [immediate] for an explicit request, otherwise after the typing delay. */
    fun requestSync(immediate: Boolean = false) {
        requests.trySend(immediate)
    }

    /** Syncs now, after any sync already running, and returns how it ended. For background work. */
    suspend fun syncNow(): SyncStatus {
        run()
        return status.value
    }

    private suspend fun run() = running.withLock {
        retry?.cancel()
        val current = engine()
        if (current == null) {
            mutableStatus.value = SyncStatus.Local
            return@withLock
        }
        mutableStatus.value = SyncStatus.Syncing
        val failure = current.sync().failure
        if (failure == null) {
            failures = 0
            mutableStatus.value = SyncStatus.Synced(clock())
            return@withLock
        }
        mutableStatus.value = SyncStatus.Failed(failure, clock())
        if (failure !is SyncFailure.Unauthorized && retryDelays.isNotEmpty()) {
            val wait = retryDelays[minOf(failures, retryDelays.lastIndex)]
            failures++
            retry = scope.launch {
                delay(wait)
                requestSync(immediate = true)
            }
        }
    }
}
