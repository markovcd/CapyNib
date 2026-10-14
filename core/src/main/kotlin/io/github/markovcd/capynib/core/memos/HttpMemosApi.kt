package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/** [MemosApi] over the memos v1 REST API, authenticated with an access token. */
class HttpMemosApi(
    server: ServerAddress,
    private val accessToken: String,
    client: OkHttpClient = MemosHttp.defaultClient,
) : MemosApi {
    private val http = MemosHttp(server, client)
    private var user: UserDto? = null

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
            put("createTime", Instant.ofEpochSecond(state.position).toString())
        }
        val created = call<MemoDto>(Request.Builder().url(url).post(body.toString().toRequestBody(jsonType)))
        if (created !is ApiResult.Ok) return created.mapError()
        // Create ignores pinned and state, so a pinned or archived note needs a second call.
        val memo = created.value.toServerMemo()
        return if (memo is ApiResult.Ok && memo.value.state != state) update(id, state) else memo
    }

    override suspend fun update(id: String, state: MemoState): ApiResult<ServerMemo> {
        val url = api("memos/$id").newBuilder().addQueryParameter("updateMask", "content,pinned,state,create_time").build()
        val body = buildJsonObject {
            put("name", "memos/$id")
            put("content", state.content)
            put("pinned", state.pinned)
            put("state", if (state.archived) STATE_ARCHIVED else STATE_NORMAL)
            put("createTime", Instant.ofEpochSecond(state.position).toString())
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

    override suspend fun attach(id: String, memoId: String, fileName: String, type: String, bytes: ByteArray): ApiResult<Unit> {
        val url = api("attachments").newBuilder().addQueryParameter("attachmentId", id).build()
        val body = buildJsonObject {
            put("filename", fileName)
            put("type", type)
            put("content", Base64.getEncoder().encodeToString(bytes))
            put("memo", "memos/$memoId")
        }
        return when (val result = call<AttachmentDto>(Request.Builder().url(url).post(body.toString().toRequestBody(jsonType)))) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            else -> result.mapError()
        }
    }

    override suspend fun download(id: String, fileName: String): ApiResult<ByteArray> =
        http.bytes(Request.Builder().url(http.file(id, fileName)).get(), accessToken)

    override suspend fun detach(id: String): ApiResult<Unit> =
        when (val result = call<ErrorDto>(Request.Builder().url(api("attachments/$id")).delete())) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            else -> result.mapError()
        }

    override suspend fun serverVersion(): ApiResult<String?> = http.serverVersion()

    override suspend fun currentUser(): ApiResult<String> =
        when (val user = signedIn()) {
            is ApiResult.Ok -> ApiResult.Ok(user.value.username)
            else -> user.mapError()
        }

    /** The user's resource name, such as `users/1`, which memos names as each memo's creator. */
    private suspend fun me(): ApiResult<String> =
        when (val user = signedIn()) {
            is ApiResult.Ok -> ApiResult.Ok(user.value.name)
            else -> user.mapError()
        }

    /** The signed-in user, asked once per client. */
    private suspend fun signedIn(): ApiResult<UserDto> {
        user?.let { return ApiResult.Ok(it) }
        return when (val result = call<CurrentUserDto>(Request.Builder().url(api("auth/me")).get())) {
            is ApiResult.Ok -> {
                val signedIn = result.value.user ?: return unexpected("auth/me")
                user = signedIn
                ApiResult.Ok(signedIn)
            }
            else -> result.mapError()
        }
    }

    private fun api(path: String): HttpUrl = http.api(path)

    private suspend inline fun <reified T> call(request: Request.Builder): ApiResult<T> = http.call(request, accessToken)

    private fun MemoDto.toServerMemo(): ApiResult<ServerMemo> {
        val id = name.removePrefix("memos/")
        if (id == name || id.isEmpty()) return unexpected("memo name '$name'")
        val created = parseTime(createTime) ?: return unexpected("memo $name createTime '$createTime'")
        val updated = parseTime(updateTime) ?: return unexpected("memo $name updateTime '$updateTime'")
        val files = attachments.mapNotNull { it.toServerAttachment() }
        return ApiResult.Ok(ServerMemo(id, MemoState(content, pinned, state == STATE_ARCHIVED, created / 1000), created, updated, files))
    }

    /** The attachment, or null for one whose name memos would not accept for a new attachment either. */
    private fun AttachmentDto.toServerAttachment(): ServerAttachment? {
        val id = name.removePrefix("attachments/")
        if (id == name || !ServerAttachment.ID.matches(id)) return null
        return ServerAttachment(id, filename, type, parseTime(createTime) ?: 0)
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

    private fun unexpected(what: String): ApiResult.Failed = http.unexpected(what)

    private companion object {
        const val STATE_NORMAL = "NORMAL"
        const val STATE_ARCHIVED = "ARCHIVED"
        const val PAGE_SIZE = 1000
        val jsonType = MemosHttp.jsonType
    }
}
