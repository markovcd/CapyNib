package io.github.markovcd.capynib.core.memos

/**
 * What [ServerCheck] found: the version the server reports (shown, never relied on) and every
 * [Capability]. [leftover] names the check's own memo when it could not be deleted.
 */
data class ServerReport(val version: String?, val results: List<CapabilityResult>, val leftover: String?) {
    val works: Boolean get() = results.all { it.works }

    val failed: List<CapabilityResult> get() = results.filterNot { it.works }
}
