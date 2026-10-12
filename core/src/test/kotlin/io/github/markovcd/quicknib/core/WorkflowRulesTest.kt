package io.github.markovcd.quicknib.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The rules in docs/agents/rules/pipeline.md that a machine can hold, over every workflow file. */
class WorkflowRulesTest {
    private val workflows = File("../.github/workflows").listFiles { file -> file.extension in setOf("yml", "yaml") }.orEmpty().toList()

    private fun violations(rule: (File, List<String>) -> List<String>) =
        workflows.flatMap { file -> rule(file, file.readLines()) }

    @Test
    fun There_are_workflows_to_check() {
        assertEquals(true, workflows.isNotEmpty())
    }

    @Test
    fun Every_action_is_pinned_to_a_commit() {
        val found = violations { file, lines ->
            lines.withIndex().filter { (_, line) ->
                val uses = Regex("""uses:\s*(\S+)""").find(line)?.groupValues?.get(1) ?: return@filter false
                !uses.startsWith("./") && !Regex("""@[0-9a-f]{40}$""").containsMatchIn(uses)
            }.map { "${file.name}:${it.index + 1}: ${it.value.trim()}" }
        }
        assertEquals("first: ${found.take(3)}", 0, found.size)
    }

    @Test
    fun Every_checkout_drops_its_token() {
        val found = violations { file, lines ->
            lines.withIndex().filter { (_, line) -> "actions/checkout@" in line }
                .filter { (i, _) -> lines.drop(i + 1).take(3).none { "persist-credentials: false" in it } }
                .map { "${file.name}:${it.index + 1}" }
        }
        assertEquals("first: ${found.take(3)}", 0, found.size)
    }

    @Test
    fun No_run_script_splices_an_expression_into_its_text() {
        val found = violations { file, lines ->
            lines.withIndex().filter { (_, line) -> Regex("""^\s*(- )?run:.*\$\{\{""").containsMatchIn(line) }
                .map { "${file.name}:${it.index + 1}" }
        }
        assertEquals("first: ${found.take(3)}", 0, found.size)
    }

    @Test
    fun Every_job_has_a_timeout_and_every_workflow_declares_permissions() {
        val found = violations { file, lines ->
            val text = lines.joinToString("\n")
            val jobs = text.substringAfter("\njobs:\n", "").lines().count { Regex("""^  [a-zA-Z0-9_-]+:\s*$""").matches(it) }
            val timeouts = lines.count { "timeout-minutes:" in it }
            listOfNotNull(
                "${file.name}: no permissions".takeIf { "\npermissions:" !in text },
                "${file.name}: $jobs jobs, $timeouts timeouts".takeIf { timeouts < jobs },
            )
        }
        assertEquals("first: ${found.take(3)}", 0, found.size)
    }
}
