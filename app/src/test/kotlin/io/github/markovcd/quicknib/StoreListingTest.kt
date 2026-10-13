package io.github.markovcd.quicknib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The fastlane store listing F-Droid publishes, held to F-Droid's length limits. */
class StoreListingTest {
    private val listing = File(System.getProperty("quicknib.listing"))
    private val locales = listing.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
    private val versionCode = System.getProperty("quicknib.versionCode")

    @Test
    fun Every_listing_text_fits_its_limit() {
        val limits = mapOf("title.txt" to 50, "short_description.txt" to 80, "full_description.txt" to 4000)
        val tooLong = locales.flatMap { locale ->
            limits.mapNotNull { (name, limit) ->
                val file = File(locale, name)
                val length = text(file)?.length ?: return@mapNotNull null
                "${locale.name}/$name is $length characters, over $limit".takeIf { length > limit }
            } + File(locale, "changelogs").listFiles().orEmpty().mapNotNull { file ->
                val length = text(file)?.length ?: 0
                "${locale.name}/changelogs/${file.name} is $length characters, over 500".takeIf { length > 500 }
            }
        }
        assertEquals(emptyList<String>(), tooLong)
    }

    @Test
    fun The_english_listing_has_a_title_descriptions_and_an_icon() {
        val english = File(listing, "en-US")
        val missing = listOf("title.txt", "short_description.txt", "full_description.txt", "images/icon.png")
            .filterNot { File(english, it).isFile }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun The_app_version_has_an_english_changelog() {
        val changelog = File(listing, "en-US/changelogs/$versionCode.txt")
        assertTrue("$changelog is missing or empty", text(changelog)?.isNotBlank() == true)
    }

    private fun text(file: File): String? = file.takeIf { it.isFile }?.readText()?.trimEnd()
}
