package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ApiGenerationTest {
    private val web = MockWebServer()
    private val server by lazy { ServerAddress.parse(web.url("/").toString())!! }

    @Before
    fun start() = web.start()

    @After
    fun stop() = web.close()

    private fun reply(code: Int, body: String) = web.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun heard(): RecordedRequest = web.takeRequest(1, TimeUnit.SECONDS)!!

    /** Every request the server heard, in order. */
    private fun heardAll(): List<RecordedRequest> = List(web.requestCount) { heard() }

    private fun paths(): List<String> = heardAll().map { it.url.encodedPath }

    /** A browser that signs in at once and comes back with a code. */
    private val browser: (String) -> Unit = { url ->
        val page = url.toHttpUrl()
        val back = page.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
            .addQueryParameter("code", "abc")
            .addQueryParameter("state", page.queryParameter("state"))
            .build()
        thread { OkHttpClient().newCall(Request.Builder().url(back).build()).execute().close() }
    }

    private val me = """{"user":{"name":"users/me","username":"me"}}"""

    @Test
    fun A_server_that_answers_the_unversioned_profile_is_called_without_v1() = runTest {
        reply(200, """{"version":"26.11.0"}""")
        reply(200, me)

        assertEquals(ApiResult.Ok("me"), HttpMemosApi(server, "t").currentUser())

        assertEquals(listOf("/api/instance/profile", "/api/auth/me"), paths())
    }

    @Test
    fun A_server_that_does_not_know_the_unversioned_profile_is_called_at_v1() = runTest {
        reply(404, """{"message":"Not Found"}""")
        reply(200, me)

        HttpMemosApi(server, "t").currentUser()

        assertEquals(listOf("/api/instance/profile", "/api/v1/auth/me"), paths())
    }

    @Test
    fun A_server_that_answers_the_unversioned_profile_with_its_web_page_is_called_at_v1() = runTest {
        reply(200, "<!doctype html><html></html>")
        reply(200, me)

        HttpMemosApi(server, "t").currentUser()

        assertEquals(listOf("/api/instance/profile", "/api/v1/auth/me"), paths())
    }

    @Test
    fun The_generation_is_asked_once_without_the_token() = runTest {
        reply(200, """{"version":"26.11.0"}""")
        reply(200, me)
        reply(200, """{"memos":[]}""")
        reply(200, """{"memos":[]}""")

        val api = HttpMemosApi(server, "t")
        api.currentUser()
        api.listAll()

        val heard = heardAll()
        assertEquals(listOf("/api/instance/profile", "/api/auth/me", "/api/memos", "/api/memos"), heard.map { it.url.encodedPath })
        assertNull(heard.first().headers["Authorization"])
    }

    @Test
    fun A_server_that_failed_to_answer_the_probe_is_probed_again() = runTest {
        reply(500, """{"message":"starting"}""")
        reply(503, "")
        reply(200, """{"version":"26.11.0"}""")
        reply(200, me)

        val api = HttpMemosApi(server, "t")
        api.currentUser()
        api.currentUser()

        assertEquals(listOf("/api/instance/profile", "/api/v1/auth/me", "/api/instance/profile", "/api/auth/me"), paths())
    }

    @Test
    fun Browser_sign_in_names_the_provider_in_the_field_the_servers_generation_reads() = runTest {
        reply(200, """{"version":"26.11.0"}""")
        reply(200, """{"user":{"name":"users/arek","username":"arek"},"accessToken":"session-jwt"}""")
        reply(200, """{"token":"memos_pat_new"}""")
        val provider = SignInProvider("identity-providers/google", "Google", "https://accounts.example.com/auth", "client-1", emptyList())

        val result = BrowserSignIn(server, "Capynib test", port = 0).signIn(provider, browser)

        assertEquals(SignInResult.SignedIn("arek", "memos_pat_new"), result)
        heard()
        val signIn = heard()
        assertEquals("/api/auth/signin", signIn.url.encodedPath)
        val credentials = Json.parseToJsonElement(signIn.body!!.utf8()).jsonObject["ssoCredentials"]!!.jsonObject
        assertEquals("identity-providers/google", credentials["identityProvider"]!!.jsonPrimitive.content)
        assertNull(credentials["idpName"])
        assertEquals("/api/users/arek/personalAccessTokens", heard().url.encodedPath)
    }
}
