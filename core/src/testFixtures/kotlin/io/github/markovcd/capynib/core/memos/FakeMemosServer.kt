package io.github.markovcd.capynib.core.memos

/**
 * An in-memory memos server answering as [HttpMemosApi] does against the real one (a duplicate id
 * is refused, a missing memo is NotFound). It can be taken offline or made to lose a reply.
 */
class FakeMemosServer : MemosApi {
    val memos = linkedMapOf<String, ServerMemo>()

    /** Each attachment's bytes, by attachment id. */
    val files = linkedMapOf<String, ByteArray>()
    var offline = false

    /** The memos version the server reports; null reports none. */
    var version: String? = "0.31.0"
    var loseNextCreateReply = false
    var loseNextAttachReply = false

    /** The signed-in user's name. */
    var user = "tester"

    /** Answers updates as a memos that ignores the archived state would. */
    var ignoresArchive = false

    var calls = 0
        private set
    private var clock = 1_000L

    /** A memo created or edited on the server by someone else, such as the memos web UI. An edit keeps its position; a new memo goes on top. */
    fun put(id: String, content: String, pinned: Boolean = false, archived: Boolean = false) {
        val position = memos[id]?.state?.position ?: ++clock
        memos[id] = ServerMemo(id, MemoState(content, pinned, archived, position), ++clock, clock, memos[id]?.attachments.orEmpty())
    }

    /** A memo moved on the server by someone else: its create time changed. */
    fun move(id: String, position: Long) {
        val memo = memos.getValue(id)
        memos[id] = memo.copy(state = memo.state.copy(position = position), updatedAt = ++clock)
    }

    override suspend fun listAll(): ApiResult<List<ServerMemo>> {
        calls++
        if (offline) return unreachable
        return ApiResult.Ok(memos.values.toList())
    }

    override suspend fun create(id: String, state: MemoState): ApiResult<ServerMemo> {
        calls++
        if (offline) return unreachable
        if (id in memos) return ApiResult.AlreadyExists
        memos[id] = ServerMemo(id, state, ++clock, clock)
        if (loseNextCreateReply) {
            loseNextCreateReply = false
            return unreachable
        }
        return ApiResult.Ok(memos.getValue(id))
    }

    override suspend fun update(id: String, state: MemoState): ApiResult<ServerMemo> {
        calls++
        if (offline) return unreachable
        val memo = memos[id] ?: return ApiResult.NotFound
        memos[id] = memo.copy(state = if (ignoresArchive) state.copy(archived = memo.state.archived) else state, updatedAt = ++clock)
        return ApiResult.Ok(memos.getValue(id))
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        calls++
        if (offline) return unreachable
        val memo = memos.remove(id) ?: return ApiResult.NotFound
        memo.attachments.forEach { files.remove(it.id) }
        return ApiResult.Ok(Unit)
    }

    /** memos answers a taken attachment id with a server error, not as a taken memo id. */
    override suspend fun attach(id: String, memoId: String, fileName: String, type: String, bytes: ByteArray): ApiResult<Unit> {
        calls++
        if (offline) return unreachable
        val memo = memos[memoId] ?: return ApiResult.NotFound
        if (id in files) return ApiResult.Failed(SyncFailure.Server(500, "attachment $id exists"))
        files[id] = bytes
        memos[memoId] = memo.copy(attachments = memo.attachments + ServerAttachment(id, fileName, type, ++clock))
        if (loseNextAttachReply) {
            loseNextAttachReply = false
            return unreachable
        }
        return ApiResult.Ok(Unit)
    }

    override suspend fun download(id: String, fileName: String): ApiResult<ByteArray> {
        calls++
        if (offline) return unreachable
        return files[id]?.let { ApiResult.Ok(it) } ?: ApiResult.NotFound
    }

    override suspend fun detach(id: String): ApiResult<Unit> {
        calls++
        if (offline) return unreachable
        if (files.remove(id) == null) return ApiResult.NotFound
        memos.replaceAll { _, memo -> memo.copy(attachments = memo.attachments.filterNot { it.id == id }) }
        return ApiResult.Ok(Unit)
    }

    /** The bytes of each attachment of memo [id], by attachment id. */
    fun filesOf(id: String): Map<String, String> =
        memos.getValue(id).attachments.associate { it.id to files.getValue(it.id).decodeToString() }

    override suspend fun serverVersion(): ApiResult<String?> = if (offline) unreachable else ApiResult.Ok(version)

    override suspend fun currentUser(): ApiResult<String> {
        calls++
        return if (offline) unreachable else ApiResult.Ok(user)
    }

    companion object {
        /** The address a test syncs with when it does not matter which. */
        val ADDRESS = requireNotNull(ServerAddress.parse("https://memos.test"))
    }

    private val unreachable = ApiResult.Failed(SyncFailure.Unreachable("fake server is offline"))
}
