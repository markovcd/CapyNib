package io.github.markovcd.capynib.cli

import io.github.markovcd.capynib.core.memos.ServerReport
import kotlinx.serialization.Serializable

/** A server check as `server --json` prints it; each check is named by its capability key. */
@Serializable
data class ServerJson(val version: String?, val works: Boolean, val checks: List<ServerCheckJson>, val leftover: String? = null) {
    companion object {
        fun of(report: ServerReport) = ServerJson(
            report.version,
            report.works,
            report.results.map { ServerCheckJson(it.capability.key, it.works, it.problem) },
            report.leftover,
        )
    }
}
