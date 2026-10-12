package io.github.markovcd.quicknib.cli

/** Command-line words split into positionals, `--flag`s and `--option value`s. */
class Arguments private constructor(
    val positionals: List<String>,
    private val options: Map<String, String>,
    private val flags: Set<String>,
) {
    fun option(name: String): String? = options[name]

    fun flag(name: String): Boolean = name in flags

    /** Names given that the command does not know, for a usage error. */
    fun unknown(knownOptions: Set<String>, knownFlags: Set<String>): List<String> =
        (options.keys - knownOptions).map { "--$it" } + (flags - knownFlags).map { "--$it" }

    companion object {
        /** [valued] lists the options that take a value; any other `--name` is a flag. */
        fun parse(words: List<String>, valued: Set<String>): Result {
            val positionals = mutableListOf<String>()
            val options = mutableMapOf<String, String>()
            val flags = mutableSetOf<String>()
            var i = 0
            while (i < words.size) {
                val word = words[i]
                if (word.startsWith("--")) {
                    val name = word.removePrefix("--")
                    if (name in valued) {
                        val value = words.getOrNull(i + 1) ?: return Result.Error("--$name needs a value")
                        options[name] = value
                        i++
                    } else {
                        flags += name
                    }
                } else {
                    positionals += word
                }
                i++
            }
            return Result.Parsed(Arguments(positionals, options, flags))
        }
    }

    sealed interface Result {
        data class Parsed(val arguments: Arguments) : Result

        data class Error(val message: String) : Result
    }
}
