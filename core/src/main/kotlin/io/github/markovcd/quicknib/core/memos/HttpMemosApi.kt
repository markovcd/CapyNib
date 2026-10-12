package io.github.markovcd.quicknib.core.memos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/** [MemosApi] over the memos v1 REST API, authenticated with an access token. */
class HttpMemosApi(
    private val server: ServerAddress,
    private val accessToken: String,
    private val client: OkHttpClient = defaultClient,
) : MemosApi {
    private var creator: String? = null

    override suspend fun listAll(): ApiResult<List<ServerMemo>> {
        val me = when (val user = me()) {
            is ApiResult.Ok -> user.value
            is ApiResult.Failed -> return user
            ApiResult.NotFound, ApiResult.AlreadyExists -> return unexpected("auth/me")
        }
        val memos = mutableListOf<ServerMemo>()
        for (state in listOf(STATE_NORMAL, STATE_ARCHIVED)) {
            var pageToken = ""
            do {
                val url = api("memos").newBuilder()
                    .addQueryParameter("pageSize", PAGE_SIZE.toString())
                    .addQueryParameter("state", state)
                    .apply { if (pageToken.isNotEmpty()) addQueryParameter("pageToken", pageToken) }
                    .build()
                val page = when (val result = call<ListMemosDto>(Request.Builder().url(url).get())) {
                    is ApiResult.Ok -> result.value
                    is ApiResult.Failed -> return result
                    ApiResult.NotFound, ApiResult.AlreadyExists -> return unexpected("memos")
                }
                for (dto in page.memos) {
                    // The list also holds other users' public memos; only the user's own are notes.
                    if (dto.creator != me) continue
                    when (val memo = dto.toServerMemo()) {
                        is ApiResult.Ok -> memos += memo.value
                        is ApiResult.Failed -> return memo
                        ApiResult.NotFound, ApiResult.AlreadyExists -> return unexpected("memos")
                    }
                }
                pageToken = page.nextPageToken
            } while (pageToken.isNotEmpty())
        }
        return ApiResult.Ok(memos)
    }

    override suspend fun create(id: String, state: MemoState): ApiResult<ServerMemo> {
        val url = api("memos").newBuilder().addQueryParameter("memoId", id).build()
        val body = buildJsonObject {
            put("content", state.content)
            put("visibility", "PRIVATE")
        }
        val created = call<MemoDto>(Request.Builder().url(url).post(body.toString().toRequestBody(jsonType)))
        if (created !is ApiResult.Ok) return created.mapError()
        // Create ignores pinned and state, so a pinned or archived note needs a second call.
        return if (state.pinned || state.archived) update(id, state) else created.value.toServerMemo()
    }

    override suspend fun update(id: String, state: MemoState): ApiResult<ServerMemo> {
        val url = api("memos/$id").newBuilder().addQueryParameter("updateMask", "content,pinned,state").build()
        val body = buildJsonObject {
            put("name", "memos/$id")
            put("content", state.content)
            put("pinned", state.pinned)
            put("state", if (state.archived) STATE_ARCHIVED else STATE_NORMAL)
        }
        return when (val result = call<MemoDto>(Request.Builder().url(url).patch(body.toString().toRequestBody(jsonType)))) {
            is ApiResult.Ok -> result.value.toServerMemo()
            else -> result.mapError()
        }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        when (val result = call<ErrorDto>(Request.Builder().url(api("memos/$id")).delete())) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            else -> result.mapError()
        }

    override suspend fun currentUser(): ApiResult<String> =
        when (val result = call<CurrentUserDto>(Request.Builder().url(api("auth/me")).get())) {
            is ApiResult.Ok -> result.value.user?.let { ApiResult.Ok(it.username) } ?: unexpected("auth/me")
            else -> result.mapError()
        }

    private suspend fun me(): ApiResult<String> {
        creator?.let { return ApiResult.Ok(it) }
        return when (val result = call<CurrentUserDto>(Request.Builder().url(api("auth/me")).get())) {
            is ApiResult.Ok -> {
                val name = result.value.user?.name ?: return unexpected("auth/me")
                creator = name
                ApiResult.Ok(name)
            }
            else -> result.mapError()
        }
    }

    private fun api(path: String): HttpUrl = server.url.newBuilder().addPathSegments("api/v1/$path").build()

    private suspend inline fun <reified T> call(request: Request.Builder): ApiResult<T> = withContext(Dispatchers.IO) {
        val built = request.header("Authorization", "Bearer $accessToken").header("Accept", "application/json").build()
        val where = "${built.method} ${built.url.encodedPath}"
        try {
            client.newCall(built).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> try {
                        ApiResult.Ok(json.decodeFromString<T>(text.ifEmpty { "{}" }))
                    } catch (e: SerializationException) {
                        ApiResult.Failed(SyncFailure.Server(response.code, "$where: not a memos reply (${e.message})"))
                    } catch (e: IllegalArgumentException) {
                        ApiResult.Failed(SyncFailure.Server(response.code, "$where: not a memos reply (${e.message})"))
                    }
                    response.code == 401 -> ApiResult.Failed(SyncFailure.Unauthorized("$where: ${errorMessage(text)}"))
                    response.code == 404 -> ApiResult.NotFound
                    response.code == 409 -> ApiResult.AlreadyExists
                    else -> ApiResult.Failed(SyncFailure.Server(response.code, "$where: ${errorMessage(text)}"))
                }
            }
        } catch (e: IOException) {
            ApiResult.Failed(SyncFailure.Unreachable("$where: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun errorMessage(text: String): String =
        try {
            json.decodeFromString<ErrorDto>(text).message.ifEmpty { text.take(200) }
        } catch (e: SerializationException) {
            text.take(200)
        } catch (e: IllegalArgumentException) {
            text.take(200)
        }

    private fun MemoDto.toServerMemo(): ApiResult<ServerMemo> {
        val id = name.removePrefix("memos/")
        if (id == name || id.isEmpty()) return unexpected("memo name '$name'")
        val created = parseTime(createTime) ?: return unexpected("memo $name createTime '$createTime'")
        val updated = parseTime(updateTime) ?: return unexpected("memo $name updateTime '$updateTime'")
        return ApiResult.Ok(ServerMemo(id, MemoState(content, pinned, state == STATE_ARCHIVED), created, updated))
    }

    private fun parseTime(text: String): Long? =
        try {
            Instant.parse(text).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }

    private fun <T> ApiResult<*>.mapError(): ApiResult<T> = when (this) {
        is ApiResult.Ok -> error("mapError called on a success")
        ApiResult.NotFound -> ApiResult.NotFound
        ApiResult.AlreadyExists -> ApiResult.AlreadyExists
        is ApiResult.Failed -> this
    }

    private fun unexpected(what: String): ApiResult.Failed =
        ApiResult.Failed(SyncFailure.Server(200, "unexpected reply from ${server}: $what"))

    private companion object {
        const val STATE_NORMAL = "NORMAL"
        const val STATE_ARCHIVED = "ARCHIVED"
        const val PAGE_SIZE = 1000
        val jsonType = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
