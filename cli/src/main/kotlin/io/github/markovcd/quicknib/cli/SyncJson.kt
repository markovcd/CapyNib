package io.github.markovcd.quicknib.cli

import io.github.markovcd.quicknib.core.sync.SyncReport
import kotlinx.serialization.Serializable

/** A sync report as `sync --json` prints it. */
@Serializable
data class SyncJson(
    val received: Int,
    val sent: Int,
    val deletedHere: Int,
    val deletedThere: Int,
    val conflicts: Int,
    val failure: String? = null,
) {
    companion object {
        fun of(report: SyncReport) = SyncJson(
            report.received,
            report.sent,
            report.deletedHere,
            report.deletedThere,
            report.conflicts,
            report.failure?.let { "${it.javaClass.simpleName}: ${it.message}" },
        )
    }
}
