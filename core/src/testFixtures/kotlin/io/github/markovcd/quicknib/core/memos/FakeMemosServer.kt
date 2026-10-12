package io.github.markovcd.quicknib.core.memos

/**
 * An in-memory memos server answering as [HttpMemosApi] does against the real one (a duplicate id
 * is refused, a missing memo is NotFound). It can be taken offline or made to lose a reply.
 */
class FakeMemosServer : MemosApi {
    val memos = linkedMapOf<String, ServerMemo>()
    var offline = false
    var loseNextCreateReply = false
    var calls = 0
        private set
    private var clock = 1_000L

    /** A memo created on the server by someone else, such as the memos web UI. */
    fun put(id: String, content: String, pinned: Boolean = false, archived: Boolean = false) {
        memos[id] = ServerMemo(id, MemoState(content, pinned, archived), ++clock, clock)
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
        memos[id] = memo.copy(state = state, updatedAt = ++clock)
        return ApiResult.Ok(memos.getValue(id))
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        calls++
        if (offline) return unreachable
        return if (memos.remove(id) == null) ApiResult.NotFound else ApiResult.Ok(Unit)
    }

    override suspend fun currentUser(): ApiResult<String> = if (offline) unreachable else ApiResult.Ok("tester")

    private val unreachable = ApiResult.Failed(SyncFailure.Unreachable("fake server is offline"))
}
