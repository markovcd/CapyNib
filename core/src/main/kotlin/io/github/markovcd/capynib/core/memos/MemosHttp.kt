package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** JSON calls to a memos server's v1 API, each failure turned into an [ApiResult]. */
internal class MemosHttp(val server: ServerAddress, private val client: OkHttpClient) {
    fun api(path: String): HttpUrl = server.url.newBuilder().addPathSegments("api/v1/$path").build()

    /** Sends [request] with [accessToken], if any, and reads the reply as [T]. */
    suspend inline fun <reified T> call(request: Request.Builder, accessToken: String?): ApiResult<T> =
        call(request, accessToken, serializer<T>())

    suspend fun <T> call(request: Request.Builder, accessToken: String?, reply: KSerializer<T>): ApiResult<T> =
        send(request, accessToken) { code, body, where ->
            val text = body.string()
            try {
                ApiResult.Ok(json.decodeFromString(reply, text.ifEmpty { "{}" }))
            } catch (e: SerializationException) {
                ApiResult.Failed(SyncFailure.Server(code, "$where: not a memos reply (${e.message})"))
            } catch (e: IllegalArgumentException) {
                ApiResult.Failed(SyncFailure.Server(code, "$where: not a memos reply (${e.message})"))
            }
        }

    /** Sends [request] with [accessToken] and reads the reply as raw bytes, such as an attachment's file. */
    suspend fun bytes(request: Request.Builder, accessToken: String?): ApiResult<ByteArray> =
        send(request, accessToken) { _, body, _ -> ApiResult.Ok(body.bytes()) }

    /** The file of attachment [id], named [fileName]; memos serves it outside the API. */
    fun file(id: String, fileName: String): HttpUrl =
        server.url.newBuilder().addPathSegment("file").addPathSegment("attachments").addPathSegment(id).addPathSegment(fileName).build()

    private suspend fun <T> send(request: Request.Builder, accessToken: String?, read: (Int, ResponseBody, String) -> ApiResult<T>): ApiResult<T> =
        withContext(Dispatchers.IO) {
            if (accessToken != null) request.header("Authorization", "Bearer $accessToken")
            val built = request.header("Accept", "application/json").build()
            val where = "${built.method} ${built.url.encodedPath}"
            try {
                client.newCall(built).execute().use { response ->
                    if (response.isSuccessful) return@use read(response.code, response.body, where)
                    val text = response.body.string()
                    when (response.code) {
                        401 -> ApiResult.Failed(SyncFailure.Unauthorized("$where: ${errorMessage(text)}"))
                        404 -> ApiResult.NotFound
                        409 -> ApiResult.AlreadyExists
                        else -> ApiResult.Failed(SyncFailure.Server(response.code, "$where: ${errorMessage(text)}"))
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failed(SyncFailure.Unreachable("$where: ${e.message ?: e.javaClass.simpleName}"))
            }
        }

    /**
     * The memos version the server reports, asked without a token, or null when it reports none:
     * memos 0.25 and later answer at `instance/profile`, older releases at `workspace/profile`.
     */
    suspend fun serverVersion(): ApiResult<String?> {
        for (path in listOf("instance/profile", "workspace/profile")) {
            when (val result = call<InstanceProfileDto>(Request.Builder().url(api(path)).get(), null)) {
                is ApiResult.Ok -> return ApiResult.Ok(result.value.version.ifEmpty { null })
                is ApiResult.Failed -> return result
                ApiResult.NotFound, ApiResult.AlreadyExists -> continue
            }
        }
        return ApiResult.Ok(null)
    }

    fun unexpected(what: String): ApiResult.Failed = ApiResult.Failed(SyncFailure.Server(200, "unexpected reply from ${server}: $what"))

    private fun errorMessage(text: String): String =
        try {
            json.decodeFromString<ErrorDto>(text).message.ifEmpty { text.take(200) }
        } catch (e: SerializationException) {
            text.take(200)
        } catch (e: IllegalArgumentException) {
            text.take(200)
        }

    companion object {
        val jsonType = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
