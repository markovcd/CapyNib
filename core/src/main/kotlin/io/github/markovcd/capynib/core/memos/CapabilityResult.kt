package io.github.markovcd.capynib.core.memos

/** How one [Capability] fared: [problem] is null when it works, and says what went wrong otherwise. */
data class CapabilityResult(val capability: Capability, val problem: String?) {
    val works: Boolean get() = problem == null
}
