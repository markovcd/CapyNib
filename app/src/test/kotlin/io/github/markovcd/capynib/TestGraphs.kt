package io.github.markovcd.capynib

import androidx.test.core.app.ApplicationProvider
import io.github.markovcd.capynib.core.memos.BrowserSignIn
import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.settings.TokenCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * An [AppGraph] over an in-memory database, a reversible fake cipher, a fake memos server, and
 * browser sign-in against canned memos replies that sign in as `tester`.
 */
object TestGraphs {
    fun create(server: FakeMemosServer = FakeMemosServer()): AppGraph = AppGraph(
        context = ApplicationProvider.getApplicationContext(),
        databaseName = null,
        cipher = ReversingCipher,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        apiFactory = { _, _ -> server },
        signInFactory = { BrowserSignIn(it, "test", port = 0, client = OkHttpClient.Builder().addInterceptor(CannedSignIn).build()) },
    )

    /** The memos replies a browser sign-in reads: one Google provider, a session, then an access token. */
    private object CannedSignIn : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val body = when (chain.request().url.encodedPath) {
                "/api/v1/identity-providers" ->
                    """{"identityProviders":[{"name":"identity-providers/google","type":"OAUTH2","title":"Google",""" +
                        """"config":{"oauth2Config":{"clientId":"c1","authUrl":"https://accounts.example.com/auth","scopes":["email"]}}}]}"""
                "/api/v1/auth/signin" -> """{"user":{"name":"users/tester","username":"tester"},"accessToken":"session-jwt"}"""
                "/api/v1/users/tester/personalAccessTokens" -> """{"token":"memos_pat_from_browser"}"""
                else -> return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("Not Found")
                    .body("{}".toResponseBody()).build()
            }
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    object ReversingCipher : TokenCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()

        override fun decrypt(encrypted: String) = encrypted.removePrefix("enc:").takeIf { encrypted.startsWith("enc:") }?.reversed()
    }
}
