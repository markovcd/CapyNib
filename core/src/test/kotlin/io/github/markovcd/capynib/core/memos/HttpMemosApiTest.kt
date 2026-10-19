package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpMemosApiTest {
    private val web = MockWebServer()
    private lateinit var api: HttpMemosApi

    @Before
    fun start() {
        web.start()
        api = HttpMemosApi(ServerAddress.parse(web.url("/").toString())!!, "secret-token")
    }

    @After
    fun stop() = web.close()

    private fun reply(code: Int, body: String) = web.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun heard(): RecordedRequest = web.takeRequest(1, TimeUnit.SECONDS)!!

    private fun memoJson(id: String, creator: String = "users/me", content: String = "c", state: String = "NORMAL", pinned: Boolean = false) =
        """{"name":"memos/$id","state":"$state","creator":"$creator","createTime":"2026-10-12T20:00:00Z",""" +
            """"updateTime":"2026-10-12T20:00:01Z","content":"$content","pinned":$pinned,"extra":{"ignored":true}}"""

    /** The create time [memoJson] answers with, which is the memo's position. */
    private val created = java.time.Instant.parse("2026-10-12T20:00:00Z").epochSecond

    private val me = """{"user":{"name":"users/me","username":"me"}}"""

    @Test
    fun Listing_returns_only_the_users_own_memos_from_every_page_and_state() = runTest {
        reply(200, me)
        reply(200, """{"memos":[${memoJson("a")},${memoJson("other", creator = "users/someone")}],"nextPageToken":"p2"}""")
        reply(200, """{"memos":[${memoJson("b")}]}""")
        reply(200, """{"memos":[${memoJson("c", state = "ARCHIVED")}]}""")

        val listed = api.listAll()

        assertEquals(listOf("a", "b", "c"), (listed as ApiResult.Ok).value.map { it.id })
        assertTrue(listed.value.last().state.archived)
        assertEquals(java.time.Instant.parse("2026-10-12T20:00:01Z").toEpochMilli(), listed.value.first().updatedAt)
        assertEquals(created, listed.value.first().state.position)
        assertEquals("/api/v1/auth/me", heard().url.encodedPath)
        assertEquals("NORMAL", heard().url.queryParameter("state"))
        assertEquals("p2", heard().url.queryParameter("pageToken"))
        assertEquals("ARCHIVED", heard().url.queryParameter("state"))
    }

    @Test
    fun Asking_for_the_user_then_listing_asks_the_server_for_the_user_once() = runTest {
        reply(200, me)
        reply(200, """{"memos":[]}""")
        reply(200, """{"memos":[]}""")

        assertEquals(ApiResult.Ok("me"), api.currentUser())
        api.listAll()

        assertEquals(3, web.requestCount)
    }

    @Test
    fun Every_call_carries_the_access_token() = runTest {
        reply(200, me)

        api.currentUser()

        assertEquals("Bearer secret-token", heard().headers["Authorization"])
    }

    @Test
    fun A_note_is_created_under_its_own_id_as_a_private_memo_with_its_position_as_create_time() = runTest {
        reply(200, memoJson("n1", content = "hi"))

        val memo = api.create("n1", MemoState("hi", pinned = false, archived = false, position = created))

        assertEquals("n1", (memo as ApiResult.Ok).value.id)
        val request = heard()
        assertEquals("POST", request.method)
        assertEquals("n1", request.url.queryParameter("memoId"))
        assertEquals("""{"content":"hi","visibility":"PRIVATE","createTime":"2026-10-12T20:00:00Z"}""", request.body!!.utf8())
        assertEquals(1, web.requestCount)
    }

    @Test
    fun Creating_a_pinned_archived_note_sets_both_with_a_second_call() = runTest {
        reply(200, memoJson("n1"))
        reply(200, memoJson("n1", state = "ARCHIVED", pinned = true))

        val memo = api.create("n1", MemoState("c", pinned = true, archived = true, position = created))

        assertEquals(MemoState("c", pinned = true, archived = true, position = created), (memo as ApiResult.Ok).value.state)
        heard()
        val update = heard()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/memos/n1", update.url.encodedPath)
        assertEquals("content,pinned,state,create_time", update.url.queryParameter("updateMask"))
        assertEquals(
            """{"name":"memos/n1","content":"c","pinned":true,"state":"ARCHIVED","createTime":"2026-10-12T20:00:00Z"}""",
            update.body!!.utf8(),
        )
    }

    @Test
    fun A_refused_token_is_reported_as_unauthorized() = runTest {
        reply(401, """{"code": 16, "message": "authentication required"}""")

        val result = api.listAll()

        val failure = (result as ApiResult.Failed).failure
        assertTrue(failure is SyncFailure.Unauthorized)
        assertEquals("GET /api/v1/auth/me: authentication required", failure.message)
    }

    @Test
    fun Missing_and_duplicate_memos_are_told_apart_from_errors() = runTest {
        reply(404, """{"code":5,"message":"memo not found"}""")
        reply(409, """{"code":6,"message":"memo with ID \"x\" already exists"}""")
        reply(500, """{"code":13,"message":"database is locked"}""")

        assertEquals(ApiResult.NotFound, api.update("x", MemoState("", false, false, 0)))
        assertEquals(ApiResult.AlreadyExists, api.create("x", MemoState("", false, false, 0)))
        assertEquals(
            ApiResult.Failed(SyncFailure.Server(500, "DELETE /api/v1/memos/x: database is locked")),
            api.delete("x"),
        )
    }

    @Test
    fun A_server_with_no_signed_in_user_to_ask_for_is_not_a_memos_server() = runTest {
        reply(404, """{"code":5,"message":"not found"}""")

        val server = ServerAddress.parse(web.url("/").toString())!!
        assertEquals(ApiResult.Failed(SyncFailure.Server(404, "$server is not a memos server: GET /api/v1/auth/me was not found")), api.currentUser())
    }

    @Test
    fun A_delete_answered_as_a_duplicate_is_an_unexpected_reply() = runTest {
        reply(409, """{"code":6,"message":"already exists"}""")

        assertTrue(api.delete("x") is ApiResult.Failed)
    }

    @Test
    fun A_server_that_is_not_memos_is_reported_with_the_call_that_failed() = runTest {
        reply(200, "<html>login page</html>")

        val failure = (api.currentUser() as ApiResult.Failed).failure

        assertTrue(failure.message, failure is SyncFailure.Server && failure.message.startsWith("GET /api/v1/auth/me: not a memos reply"))
    }

    @Test
    fun A_server_that_cannot_be_reached_is_reported_as_unreachable() = runTest {
        web.close()

        val failure = (api.listAll() as ApiResult.Failed).failure

        assertTrue(failure.toString(), failure is SyncFailure.Unreachable)
    }

    @Test
    fun The_server_version_is_read_from_the_instance_profile_without_the_token() = runTest {
        reply(200, """{"version":"0.31.0","demo":false,"instanceUrl":""}""")

        assertEquals(ApiResult.Ok("0.31.0"), api.serverVersion())
        val request = heard()
        assertEquals("/api/v1/instance/profile", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun A_server_older_than_the_instance_profile_is_read_from_its_workspace_profile() = runTest {
        reply(404, """{"code":5,"message":"Not Found"}""")
        reply(200, """{"owner":"","version":"0.24.4","mode":"prod"}""")

        assertEquals(ApiResult.Ok("0.24.4"), api.serverVersion())
        assertEquals("/api/v1/instance/profile", heard().url.encodedPath)
        assertEquals("/api/v1/workspace/profile", heard().url.encodedPath)
    }

    @Test
    fun A_server_that_reports_no_version_has_none() = runTest {
        reply(404, "{}")
        reply(404, "{}")

        assertEquals(ApiResult.Ok(null), api.serverVersion())
    }

    @Test
    fun A_server_address_without_a_scheme_means_https_and_junk_is_refused() {
        assertEquals("https://memos.example.com", ServerAddress.parse(" memos.example.com/ ").toString())
        assertEquals("http://10.0.0.2:5230", ServerAddress.parse("http://10.0.0.2:5230").toString())
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("ftp://x"))
        assertNull(ServerAddress.parse("not a url at all"))
    }
}
