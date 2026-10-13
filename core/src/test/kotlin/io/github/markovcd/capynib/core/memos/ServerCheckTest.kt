package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerCheckTest {
    private val server = FakeMemosServer()

    private suspend fun checked(api: MemosApi = server) = (ServerCheck(api, "probe").run() as ApiResult.Ok).value

    private fun ServerReport.problems() = results.associate { it.capability to it.problem }

    /** A server that answers one call with an error, as a memos that changed it would. */
    private class Refusing(private val inner: FakeMemosServer, private val refused: String) : MemosApi by inner {
        private val error = ApiResult.Failed(SyncFailure.Server(400, "$refused is not supported"))

        override suspend fun create(id: String, state: MemoState) = if (refused == "create") error else inner.create(id, state)

        override suspend fun delete(id: String) = if (refused == "delete") error else inner.delete(id)
    }

    @Test
    fun Every_call_works_on_a_server_that_does_them_all_and_the_users_memos_are_left_alone() = runTest {
        server.put("mine", "groceries", pinned = true)
        val before = server.memos.toMap()

        val report = checked()

        assertEquals(Capability.entries.associateWith { null }, report.problems())
        assertEquals("0.31.0", report.version)
        assertEquals(before, server.memos)
    }

    @Test
    fun A_server_that_ignores_the_archived_state_fails_the_update_and_still_deletes_its_memo() = runTest {
        server.ignoresArchive = true

        val report = checked()

        assertEquals(
            listOf(Capability.Update, Capability.ListArchived),
            report.failed.map { it.capability },
        )
        assertEquals(null, report.leftover)
        assertEquals(emptyMap<String, ServerMemo>(), server.memos)
    }

    @Test
    fun A_server_that_cannot_create_under_an_id_writes_nothing_and_skips_the_rest() = runTest {
        val report = checked(Refusing(server, "create"))

        assertEquals("create is not supported", report.problems()[Capability.CreateWithId])
        assertEquals(listOf(Capability.CreateWithId, Capability.Update, Capability.ListArchived, Capability.Delete), report.failed.map { it.capability })
        assertEquals(emptyMap<String, ServerMemo>(), server.memos)
    }

    @Test
    fun A_create_that_wrote_the_memo_and_then_failed_still_deletes_it() = runTest {
        val halfCreating = object : MemosApi by server {
            override suspend fun create(id: String, state: MemoState): ApiResult<ServerMemo> {
                server.create(id, state)
                return ApiResult.Failed(SyncFailure.Server(400, "create_time is not supported"))
            }
        }

        val report = checked(halfCreating)

        assertEquals("create_time is not supported", report.problems()[Capability.CreateWithId])
        assertEquals(null, report.leftover)
        assertEquals(emptyMap<String, ServerMemo>(), server.memos)
    }

    @Test
    fun A_check_memo_a_failed_create_left_that_cannot_be_deleted_is_named_as_left_over() = runTest {
        val stuck = object : MemosApi by server {
            override suspend fun create(id: String, state: MemoState): ApiResult<ServerMemo> {
                server.create(id, state)
                return ApiResult.Failed(SyncFailure.Server(400, "create_time is not supported"))
            }

            override suspend fun delete(id: String): ApiResult<Unit> = ApiResult.Failed(SyncFailure.Server(400, "delete is not supported"))
        }

        assertEquals("probe", checked(stuck).leftover)
    }

    @Test
    fun A_check_memo_that_cannot_be_deleted_is_named_as_left_over() = runTest {
        val report = checked(Refusing(server, "delete"))

        assertEquals(listOf(Capability.Delete), report.failed.map { it.capability })
        assertEquals("probe", report.leftover)
    }

    @Test
    fun An_unreachable_server_is_a_failure_not_a_report() = runTest {
        server.offline = true

        val result = ServerCheck(server, "probe").run()

        assertEquals(ApiResult.Failed(SyncFailure.Unreachable("fake server is offline")), result)
    }

    @Test
    fun Each_check_writes_to_a_memo_of_its_own() = runTest {
        val ids = mutableListOf<String>()
        val recording = object : MemosApi by server {
            override suspend fun create(id: String, state: MemoState) = server.create(id, state).also { ids += id }
        }

        ServerCheck(recording).run()
        ServerCheck(recording).run()

        assertEquals(2, ids.distinct().size)
        assertEquals(true, ids.all { java.util.UUID.fromString(it).toString() == it })
    }
}
