package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Browser sign-in against a real memos server, with a fake sign-in provider standing in for Google.
 * The server must reach the provider's token and user-info addresses on this machine, at the host
 * named by `MEMOS_TEST_REACH_BACK`; the gate sets it.
 */
class BrowserSignInServerTest {
    private val provider = MockWebServer()
    private val browser = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private var live: LiveMemos? = null

    @After
    fun stop() {
        live?.admin("identity-providers/$PROVIDER_ID", Request.Builder().delete())
        provider.close()
    }

    @Test
    fun Signing_in_through_a_provider_gives_an_access_token_the_server_accepts() = runTest {
        val reachBack = System.getenv("MEMOS_TEST_REACH_BACK").orEmpty()
        val memos = LiveMemos.connect()
        assumeTrue("skip when MEMOS_TEST_REACH_BACK names no host the memos server can reach this machine at", reachBack.isNotEmpty())
        provider.dispatcher = FakeProvider()
        provider.start(InetAddress.getByName("0.0.0.0"), 0)
        addProvider(memos, reachBack)
        live = memos

        val signIn = BrowserSignIn(memos.server, "Capynib test", port = 0)
        val offered = (signIn.providers() as ApiResult.Ok).value.single { it.name == "identity-providers/$PROVIDER_ID" }
        val result = signIn.signIn(offered) { page ->
            thread { browser.newCall(Request.Builder().url(page).build()).execute().close() }
        }

        assertTrue("signed in: $result", result is SignInResult.SignedIn)
        result as SignInResult.SignedIn
        assertEquals(ApiResult.Ok(result.username), HttpMemosApi(memos.server, result.accessToken).currentUser())
    }

    private fun addProvider(memos: LiveMemos, reachBack: String) {
        val config = buildJsonObject {
            put("type", "OAUTH2")
            put("title", "Fake")
            putJsonObject("config") {
                putJsonObject("oauth2Config") {
                    put("clientId", "capynib-client")
                    put("clientSecret", "capynib-secret")
                    // The browser (this test) opens the sign-in page; the server calls the other two.
                    put("authUrl", "http://127.0.0.1:${provider.port}/authorize")
                    put("tokenUrl", "http://$reachBack:${provider.port}/token")
                    put("userInfoUrl", "http://$reachBack:${provider.port}/userinfo")
                    putJsonArray("scopes") { add("email") }
                    putJsonObject("fieldMapping") { put("identifier", "email") }
                }
            }
        }
        val json = "application/json".toMediaType()
        try {
            memos.admin("identity-providers/$PROVIDER_ID", Request.Builder().delete())
        } catch (e: IllegalStateException) {
            // Not there from an earlier run.
        }
        memos.admin("identity-providers?identityProviderId=$PROVIDER_ID", Request.Builder().post(config.toString().toRequestBody(json)))
    }

    /** Hands out one code, and a token for it only with the PKCE verifier that matches its challenge. */
    private class FakeProvider : Dispatcher() {
        @Volatile
        private var challenge = ""

        override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
            "/authorize" -> {
                challenge = request.url.queryParameter("code_challenge").orEmpty()
                val back = request.url.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
                    .addQueryParameter("code", CODE)
                    .addQueryParameter("state", request.url.queryParameter("state"))
                    .build()
                MockResponse.Builder().code(302).addHeader("Location", back.toString()).build()
            }
            "/token" -> {
                val form = "http://x/?${request.body!!.utf8()}".toHttpUrl()
                val verifier = form.queryParameter("code_verifier").orEmpty()
                if (form.queryParameter("code") == CODE && s256(verifier) == challenge) {
                    json("""{"access_token":"$ACCESS","token_type":"Bearer","expires_in":3600}""")
                } else {
                    MockResponse.Builder().code(400).body("""{"error":"invalid_grant"}""").addHeader("Content-Type", "application/json").build()
                }
            }
            "/userinfo" ->
                if (request.headers["Authorization"] == "Bearer $ACCESS") json("""{"email":"$EMAIL"}""") else MockResponse.Builder().code(401).build()
            else -> MockResponse.Builder().code(404).build()
        }

        private fun json(body: String) = MockResponse.Builder().body(body).addHeader("Content-Type", "application/json").build()

        private fun s256(verifier: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    }

    private companion object {
        const val PROVIDER_ID = "capynib-fake"
        const val CODE = "fake-code"
        const val ACCESS = "fake-provider-token"
        const val EMAIL = "browser-sign-in@example.com"
    }
}
