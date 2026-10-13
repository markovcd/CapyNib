package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BrowserSignInTest {
    private val memos = MockWebServer()
    private lateinit var signIn: BrowserSignIn
    private val browserClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private val provider = SignInProvider("identity-providers/google", "Google", "https://accounts.example.com/auth", "client-1", listOf("email", "profile"))

    @Before
    fun start() {
        memos.start()
        signIn = BrowserSignIn(ServerAddress.parse(memos.url("/").toString())!!, "Capynib test", port = 0)
    }

    @After
    fun stop() = memos.close()

    private fun reply(code: Int, body: String) = memos.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun heard(): RecordedRequest = memos.takeRequest(1, TimeUnit.SECONDS)!!

    /** What the provider would send the browser back with, as the HTTP status the receiver answered. */
    private fun comeBack(page: HttpUrl, vararg query: Pair<String, String>): Int {
        val back = page.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
        query.forEach { (name, value) -> back.addQueryParameter(name, value) }
        return browserClient.newCall(Request.Builder().url(back.build()).build()).execute().use { it.code }
    }

    /** A browser that signs in and comes back with [query], or with the code `abc` and the right state. */
    private fun browser(seen: MutableList<HttpUrl>, vararg query: Pair<String, String>): (String) -> Unit = { url ->
        val page = url.toHttpUrl()
        seen += page
        thread { comeBack(page, *query.ifEmpty { arrayOf("code" to "abc", "state" to page.queryParameter("state")!!) }) }
    }

    private fun s256(verifier: String) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    @Test
    fun A_browser_sign_in_ends_with_a_new_access_token_for_the_app() = runTest {
        reply(200, """{"user":{"name":"users/arek","username":"arek"},"accessToken":"session-jwt"}""")
        reply(200, """{"personalAccessToken":{"name":"users/arek/personalAccessTokens/1"},"token":"memos_pat_new"}""")
        val pages = mutableListOf<HttpUrl>()

        val result = signIn.signIn(provider, browser(pages))

        assertEquals(SignInResult.SignedIn("arek", "memos_pat_new"), result)
        val page = pages.single()
        assertEquals("accounts.example.com", page.host)
        assertEquals(listOf("client-1", "code", "email profile", "S256"), listOf("client_id", "response_type", "scope", "code_challenge_method").map { page.queryParameter(it) })
        assertTrue(page.queryParameter("redirect_uri")!!.matches(Regex("""http://127\.0\.0\.1:\d+/capynib""")))

        val credentials = Json.parseToJsonElement(heard().body!!.utf8()).jsonObject["ssoCredentials"]!!.jsonObject
        assertEquals("identity-providers/google", credentials["idpName"]!!.jsonPrimitive.content)
        assertEquals("abc", credentials["code"]!!.jsonPrimitive.content)
        assertEquals(page.queryParameter("redirect_uri"), credentials["redirectUri"]!!.jsonPrimitive.content)
        assertEquals(page.queryParameter("code_challenge"), s256(credentials["codeVerifier"]!!.jsonPrimitive.content))

        val token = heard()
        assertEquals("/api/v1/users/arek/personalAccessTokens", token.url.encodedPath)
        assertEquals("Bearer session-jwt", token.headers["Authorization"])
        assertTrue(token.body!!.utf8().contains("\"description\":\"Capynib test\""))
    }

    @Test
    fun Cancelling_in_the_browser_is_a_refusal_and_asks_the_server_nothing() = runTest {
        val pages = mutableListOf<HttpUrl>()
        val result = signIn.signIn(provider) { url ->
            browser(pages, "error" to "access_denied", "state" to url.toHttpUrl().queryParameter("state")!!)(url)
        }

        assertEquals(SignInResult.Refused("Google sign-in: access_denied"), result)
        assertEquals(0, memos.requestCount)
    }

    @Test
    fun A_return_with_the_wrong_state_is_turned_away_and_the_wait_goes_on() = runTest {
        reply(200, """{"user":{"name":"users/arek","username":"arek"},"accessToken":"session-jwt"}""")
        reply(200, """{"token":"memos_pat_new"}""")
        val forged = AtomicInteger()

        val result = signIn.signIn(provider) { url ->
            val page = url.toHttpUrl()
            thread {
                forged.set(comeBack(page, "code" to "evil", "state" to "guessed"))
                comeBack(page, "code" to "abc", "state" to page.queryParameter("state")!!)
            }
        }

        assertEquals(404, forged.get())
        assertEquals(SignInResult.SignedIn("arek", "memos_pat_new"), result)
        assertTrue(heard().body!!.utf8().contains("\"code\":\"abc\""))
    }

    @Test
    fun An_idle_connection_does_not_hold_up_the_browser() = runTest {
        reply(200, """{"user":{"name":"users/arek","username":"arek"},"accessToken":"session-jwt"}""")
        reply(200, """{"token":"memos_pat_new"}""")
        val idle = mutableListOf<Socket>()

        val started = System.nanoTime()
        val result = signIn.signIn(provider) { url ->
            val page = url.toHttpUrl()
            val back = page.queryParameter("redirect_uri")!!.toHttpUrl()
            idle += Socket(back.host, back.port)
            thread { comeBack(page, "code" to "abc", "state" to page.queryParameter("state")!!) }
        }

        assertEquals(SignInResult.SignedIn("arek", "memos_pat_new"), result)
        // The receiver gives an idle connection 10 s to speak; finishing well inside that proves it did not wait on it.
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5))
        idle.forEach { it.close() }
    }

    @Test
    fun A_code_the_server_turns_down_is_a_refusal() = runTest {
        reply(400, """{"code":3,"message":"identity provider not found"}""")

        val result = signIn.signIn(provider, browser(mutableListOf()))

        assertEquals(SignInResult.Refused("POST /api/v1/auth/signin: identity provider not found"), result)
    }

    @Test
    fun A_taken_port_fails_before_the_browser_opens() = runTest {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { taken ->
            val busy = BrowserSignIn(ServerAddress.parse(memos.url("/").toString())!!, "Capynib test", port = taken.localPort)
            var opened: String? = null

            val result = busy.signIn(provider) { opened = it }

            assertTrue(result is SignInResult.Failed && result.failure is SyncFailure.Unreachable)
            assertNull(opened)
        }
    }

    @Test
    fun Only_providers_a_browser_can_sign_in_through_are_offered() = runTest {
        reply(
            200,
            """{"identityProviders":[
                {"name":"identity-providers/google","type":"OAUTH2","title":"Google","config":{"oauth2Config":
                  {"clientId":"c1","clientSecret":"","authUrl":"https://accounts.google.com/o/oauth2/v2/auth","scopes":["email"]}}},
                {"name":"identity-providers/broken","type":"OAUTH2","title":"Broken","config":{"oauth2Config":{"authUrl":""}}},
                {"name":"identity-providers/other","type":"LDAP","title":"Other","config":{}}
            ]}""",
        )

        val providers = signIn.providers()

        assertEquals(
            ApiResult.Ok(listOf(SignInProvider("identity-providers/google", "Google", "https://accounts.google.com/o/oauth2/v2/auth", "c1", listOf("email")))),
            providers,
        )
        assertNull(heard().headers["Authorization"])
    }
}
