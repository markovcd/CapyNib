package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.runBlocking
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
class LiveMemos private constructor(val server: ServerAddress, private val api: String, val token: String) {
    fun api() = HttpMemosApi(server, token)

    /** Sends [request] to the API's [path] as the test user, an admin; fails the test on an error reply. */
    fun admin(path: String, request: Request.Builder): JsonObject = send(request.url("$api$path"), token)

    companion object {
        private const val USERNAME = "capynib-test"
        private const val PASSWORD = "capynib-test-password"
        private val client = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }
        private val jsonType = "application/json".toMediaType()

        /** Skips the calling test when no server is configured; the gate always configures one. */
        fun connect(): LiveMemos {
            val url = System.getenv("MEMOS_TEST_URL").orEmpty()
            assumeTrue("skip when MEMOS_TEST_URL names no memos server", url.isNotEmpty())
            val server = requireNotNull(ServerAddress.parse(url)) { "MEMOS_TEST_URL '$url' is not a URL" }
            val api = "$server/" + runBlocking { MemosHttp(server, client).generation() }.root

            val session = signIn(api) ?: run {
                post(api, "users?userId=$USERNAME", """{"username":"$USERNAME","password":"$PASSWORD","role":"ADMIN"}""", null)
                requireNotNull(signIn(api)) { "could not create the test user on $server" }
            }
            val tokenReply = post(api, "users/$USERNAME/personalAccessTokens", """{"parent":"users/$USERNAME","description":"test"}""", session)
            val token = tokenReply["token"]!!.jsonPrimitive.content

            for (state in listOf("NORMAL", "ARCHIVED")) {
                val listed = get(api, "memos?pageSize=1000&state=$state", token)
                for (memo in listed["memos"]?.jsonArray.orEmpty()) {
                    val name = memo.jsonObject["name"]!!.jsonPrimitive.content
                    send(Request.Builder().url("$api$name").delete(), token)
                }
            }
            return LiveMemos(server, api, token)
        }

        private fun signIn(api: String): String? {
            val body = """{"passwordCredentials":{"username":"$USERNAME","password":"$PASSWORD"}}"""
            val request = Request.Builder().url("${api}auth/signin").post(body.toRequestBody(jsonType)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                return json.parseToJsonElement(response.body.string()).jsonObject["accessToken"]?.jsonPrimitive?.content
            }
        }

        private fun post(api: String, path: String, body: String, token: String?): JsonObject =
            send(Request.Builder().url("$api$path").post(body.toRequestBody(jsonType)), token)

        private fun get(api: String, path: String, token: String): JsonObject =
            send(Request.Builder().url("$api$path").get(), token)

        private fun send(request: Request.Builder, token: String?): JsonObject {
            if (token != null) request.header("Authorization", "Bearer $token")
            client.newCall(request.build()).execute().use { response ->
                val text = response.body.string()
                check(response.isSuccessful) { "${response.request.method} ${response.request.url}: ${response.code} $text" }
                return json.parseToJsonElement(text.ifEmpty { "{}" }).jsonObject
            }
        }
    }
}
