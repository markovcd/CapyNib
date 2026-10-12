package io.github.markovcd.quicknib.core.memos

/** The calls sync makes to a memos server. The network boundary: tests put a fake behind it. */
interface MemosApi {
    /** Every memo the user owns, normal and archived. */
    suspend fun listAll(): ApiResult<List<ServerMemo>>

    /** Creates a memo with a chosen [id], so a retried create can never make a duplicate. */
    suspend fun create(id: String, state: MemoState): ApiResult<ServerMemo>

    suspend fun update(id: String, state: MemoState): ApiResult<ServerMemo>

    suspend fun delete(id: String): ApiResult<Unit>

    /** The signed-in user's name, to show a working connection. */
    suspend fun currentUser(): ApiResult<String>
}
