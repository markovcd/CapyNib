package io.github.markovcd.quicknib.core.memos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue

/**
 * A real memos server for integration tests, named by `MEMOS_TEST_URL`. Signs in as a test user
 * (creating it on a fresh server), makes an access token, and empties the user's memos.
 */
class LiveMemos private constructor(val server: ServerAddress, val token: String) {
    fun api() = HttpMemosApi(server, token)

    companion object {
        private const val USERNAME = "quicknib-test"
        private const val PASSWORD = "quicknib-test-password"
        private val client = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }
        private val jsonType = "application/json".toMediaType()

        /** Skips the calling test when no server is configured; the gate always configures one. */
        fun connect(): LiveMemos {
            val url = System.getenv("MEMOS_TEST_URL").orEmpty()
            assumeTrue("skip when MEMOS_TEST_URL names no memos server", url.isNotEmpty())
            val server = requireNotNull(ServerAddress.parse(url)) { "MEMOS_TEST_URL '$url' is not a URL" }

            val session = signIn(server) ?: run {
                post(server, "users?userId=$USERNAME", """{"username":"$USERNAME","password":"$PASSWORD","role":"ADMIN"}""", null)
                requireNotNull(signIn(server)) { "could not create the test user on $server" }
            }
            val tokenReply = post(server, "users/$USERNAME/personalAccessTokens", """{"parent":"users/$USERNAME","description":"test"}""", session)
            val token = tokenReply["token"]!!.jsonPrimitive.content

            for (state in listOf("NORMAL", "ARCHIVED")) {
                val listed = get(server, "memos?pageSize=1000&state=$state", token)
                for (memo in listed["memos"]?.jsonArray.orEmpty()) {
                    val name = memo.jsonObject["name"]!!.jsonPrimitive.content
                    send(server, Request.Builder().url("$server/api/v1/$name").delete(), token)
                }
            }
            return LiveMemos(server, token)
        }

        private fun signIn(server: ServerAddress): String? {
            val body = """{"passwordCredentials":{"username":"$USERNAME","password":"$PASSWORD"}}"""
            val request = Request.Builder().url("$server/api/v1/auth/signin").post(body.toRequestBody(jsonType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                return json.parseToJsonElement(response.body.string()).jsonObject["accessToken"]?.jsonPrimitive?.content
            }
        }

        private fun post(server: ServerAddress, path: String, body: String, token: String?): JsonObject =
            send(server, Request.Builder().url("$server/api/v1/$path").post(body.toRequestBody(jsonType)), token)

        private fun get(server: ServerAddress, path: String, token: String): JsonObject =
            send(server, Request.Builder().url("$server/api/v1/$path").get(), token)

        private fun send(server: ServerAddress, request: Request.Builder, token: String?): JsonObject {
            if (token != null) request.header("Authorization", "Bearer $token")
            client.newCall(request.build()).execute().use { response ->
                val text = response.body.string()
                check(response.isSuccessful) { "${response.request.method} ${response.request.url} on $server: ${response.code} $text" }
                return json.parseToJsonElement(text.ifEmpty { "{}" }).jsonObject
            }
        }
    }
}
