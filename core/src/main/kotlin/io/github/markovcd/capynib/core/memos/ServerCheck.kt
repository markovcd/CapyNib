package io.github.markovcd.capynib.core.memos

import java.util.UUID

/**
 * Tries each call sync makes against a memos server and reports which work, whatever version it
 * runs. Reading touches nothing; every write goes to one private memo of the check's own, [probeId],
 * deleted at the end. The user's memos are never changed. The id has the shape note ids have, so the
 * check tries the ids sync sends.
 */
class ServerCheck(private val api: MemosApi, private val probeId: String = UUID.randomUUID().toString()) {
    /** The report, or the failure when the server cannot be reached or refuses the access token. */
    suspend fun run(): ApiResult<ServerReport> {
        val version = (api.serverVersion() as? ApiResult.Ok)?.value
        val results = mutableListOf<CapabilityResult>()
        fun record(capability: Capability, problem: String?) = (problem == null).also { results += CapabilityResult(capability, problem) }

        when (val user = api.currentUser()) {
            is ApiResult.Ok -> record(Capability.SignedInUser, null)
            is ApiResult.Failed -> when (user.failure) {
                is SyncFailure.Unreachable, is SyncFailure.Unauthorized -> return user
                is SyncFailure.Server -> record(Capability.SignedInUser, user.failure.message)
            }
            else -> record(Capability.SignedInUser, "unexpected reply: $user")
        }
        record(Capability.ListMemos, problem(api.listAll()))

        val created = MemoState(PROBE_TEXT, pinned = false, archived = false, position = PROBE_POSITION)
        val createdOk = record(Capability.CreateWithId, problem(api.create(probeId, created)) { it.id == probeId && it.state == created })
        if (!createdOk) {
            for (rest in listOf(Capability.Update, Capability.ListArchived, Capability.Delete)) record(rest, NEEDS_CREATE)
            // A create can write the memo and still fail, such as when the call that sets its place is refused.
            val cleared = when (api.delete(probeId)) {
                is ApiResult.Ok, ApiResult.NotFound -> true
                is ApiResult.Failed, ApiResult.AlreadyExists -> false
            }
            return ApiResult.Ok(ServerReport(version, results, leftover = probeId.takeUnless { cleared }))
        }

        val changed = MemoState("$PROBE_TEXT Changed.", pinned = true, archived = true, position = PROBE_POSITION + 3_600)
        val updatedOk = record(Capability.Update, problem(api.update(probeId, changed)) { it.state == changed })
        if (updatedOk) {
            val listed = api.listAll()
            record(Capability.ListArchived, problem(listed) { memos -> memos.any { it.id == probeId && it.state == changed } })
        } else {
            record(Capability.ListArchived, "not checked: needs a memo archived by an update")
        }

        val deleted = api.delete(probeId)
        val gone = (api.listAll() as? ApiResult.Ok)?.value?.none { it.id == probeId }
        val deleteProblem = problem(deleted) ?: if (gone == false) "the memo was still listed after deleting it" else null
        record(Capability.Delete, deleteProblem)
        return ApiResult.Ok(ServerReport(version, results, leftover = probeId.takeIf { deleteProblem != null }))
    }

    private fun <T> problem(result: ApiResult<T>, holds: (T) -> Boolean = { true }): String? = when (result) {
        is ApiResult.Ok -> if (holds(result.value)) null else "the server answered, but not with what was asked for"
        is ApiResult.Failed -> result.failure.message
        ApiResult.NotFound -> "the server said the memo does not exist"
        ApiResult.AlreadyExists -> "the server said the memo already exists"
    }

    private companion object {
        const val PROBE_TEXT = "Capynib server check. Safe to delete."

        /** The test memo's create time, 2023-11-14, low in the server's list while it exists. */
        const val PROBE_POSITION = 1_700_000_000L
        const val NEEDS_CREATE = "not checked: needs a memo created under Capynib's own id"
    }
}
