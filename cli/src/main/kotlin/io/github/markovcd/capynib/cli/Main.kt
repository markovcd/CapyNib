package io.github.markovcd.capynib.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val code = Cli(System.getenv(), System::currentTimeMillis).run(args.toList(), System.out, System.err)
    System.out.flush()
    exitProcess(code)
}
