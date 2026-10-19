package io.github.markovcd.capynib.core.memos

/** The calls sync makes to a memos server. The network boundary: tests put a fake behind it. */
interface MemosApi {
    /** Every memo the user owns, normal and archived. */
    suspend fun listAll(): ApiResult.Answer<List<ServerMemo>>

    /** Creates a memo with a chosen [id], so a retried create can never make a duplicate. */
    suspend fun create(id: String, state: MemoState): ApiResult<ServerMemo>

    suspend fun update(id: String, state: MemoState): ApiResult.Lookup<ServerMemo>

    suspend fun delete(id: String): ApiResult.Lookup<Unit>

    /** Uploads [bytes] as attachment [id] of memo [memoId], with a chosen id as [create] has. */
    suspend fun attach(id: String, memoId: String, fileName: String, type: String, bytes: ByteArray): ApiResult<Unit>

    /** The bytes of attachment [id], named [fileName]. */
    suspend fun download(id: String, fileName: String): ApiResult.Lookup<ByteArray>

    suspend fun detach(id: String): ApiResult.Lookup<Unit>

    /** The memos version the server reports, or null when it reports none. Shown, never relied on. */
    suspend fun serverVersion(): ApiResult.Answer<String?>

    /** The signed-in user's name, to show a working connection and to tell one account from another. */
    suspend fun currentUser(): ApiResult.Answer<String>
}
