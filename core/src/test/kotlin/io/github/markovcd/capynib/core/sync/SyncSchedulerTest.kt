package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.store.TempStores
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SyncSchedulerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = FakeMemosServer()
    private val store by lazy { TempStores(folder).open() }
    private var configured = true

    private fun TestScope.scheduler() = SyncScheduler(
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
        typingDelay = 1.seconds,
        retryDelays = listOf(5.seconds, 30.seconds),
    ) { if (configured) SyncEngine(store, server) { testScheduler.currentTime } else null }

    @Test
    fun Typing_syncs_about_once_a_second_not_once_a_keystroke() = runTest {
        val scheduler = scheduler()
        val note = store.create(0, body = "a")

        repeat(5) { i ->
            store.save(store.get(note.id)!!.copy(body = "a".repeat(i + 2)), 0)
            scheduler.requestSync()
            advanceTimeBy(100.milliseconds)
        }
        runCurrent()
        assertEquals("nothing sent before the typing delay", 0, server.memos.size)

        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals("aaaaaa", server.memos.getValue(note.id).state.content)
        assertEquals(SyncStatus.Synced(1000), scheduler.status.value)
    }

    @Test
    fun An_explicit_sync_runs_at_once() = runTest {
        val scheduler = scheduler()
        store.create(0, body = "now")

        scheduler.requestSync(immediate = true)
        runCurrent()

        assertEquals(1, server.memos.size)
    }

    @Test
    fun A_failed_sync_is_retried_until_the_server_is_back() = runTest {
        val scheduler = scheduler()
        store.create(0, body = "later")
        server.offline = true

        scheduler.requestSync(immediate = true)
        runCurrent()
        assertTrue(scheduler.status.value is SyncStatus.Failed)

        server.offline = false
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(1, server.memos.size)
        assertEquals(SyncStatus.Synced(5000), scheduler.status.value)
    }

    @Test
    fun Retries_wait_longer_each_time() = runTest {
        val scheduler = scheduler()
        store.create(0, body = "x")
        server.offline = true

        scheduler.requestSync(immediate = true)
        runCurrent()
        advanceTimeBy(5.seconds)
        runCurrent()
        val callsAfterFirstRetry = server.calls
        advanceTimeBy(29.seconds)
        runCurrent()

        assertEquals("no retry before 30 s", callsAfterFirstRetry, server.calls)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(callsAfterFirstRetry + 1, server.calls)
    }

    @Test
    fun A_refused_token_is_not_retried() = runTest {
        val refusing = object : io.github.markovcd.capynib.core.memos.MemosApi by server {
            override suspend fun listAll() =
                io.github.markovcd.capynib.core.memos.ApiResult.Failed(SyncFailure.Unauthorized("token refused"))
        }
        val scheduler = SyncScheduler(backgroundScope, { testScheduler.currentTime }, 1.seconds, listOf(5.seconds)) {
            SyncEngine(store, refusing) { 0 }
        }

        scheduler.requestSync(immediate = true)
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals(SyncStatus.Failed(SyncFailure.Unauthorized("token refused"), 0), scheduler.status.value)
    }

    @Test
    fun Without_a_server_notes_stay_local_and_nothing_is_tried() = runTest {
        configured = false
        val scheduler = scheduler()

        scheduler.requestSync(immediate = true)
        runCurrent()

        assertEquals(SyncStatus.Local, scheduler.status.value)
        assertEquals(0, server.calls)
    }

    @Test
    fun Background_work_can_sync_and_learn_how_it_went() = runTest {
        val scheduler = scheduler()
        store.create(0, body = "from the worker")

        val status = scheduler.syncNow()

        assertEquals(SyncStatus.Synced(0), status)
        assertEquals(1, server.memos.size)
    }
}
