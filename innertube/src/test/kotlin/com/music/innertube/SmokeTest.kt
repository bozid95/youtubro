package com.music.innertube

import com.music.innertube.models.YouTubeLocale
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Live check against YouTube Music, run in CI so we can see what YouTube answers from there.
 * It only prints; YouTube being moody must not fail the build.
 */
class SmokeTest {
    private fun report(label: String, result: Result<*>, summary: (Any?) -> String) {
        val error = result.exceptionOrNull()
        if (error == null) {
            println("SMOKE $label OK ${summary(result.getOrNull())}")
        } else {
            val root = generateSequence(error) { it.cause }.last()
            println("SMOKE $label FAIL ${error::class.java.name}: ${error.message?.take(300)} | root=${root::class.java.name}: ${root.message?.take(300)}")
        }
    }

    @Test
    fun liveYouTubeMusic() = runBlocking {
        for (hl in listOf("id-ID", "id", "en")) {
            YouTube.locale = YouTubeLocale(gl = "ID", hl = hl)
            report("home hl=$hl", YouTube.home()) { page ->
                (page as com.music.innertube.pages.HomePage).sections.joinToString { "${it.title}(${it.items.size})" }.take(300)
            }
        }
        report("visitorData", YouTube.visitorData()) { it.toString().take(20) }
        YouTube.visitorData = YouTube.visitorData().getOrNull()
        YouTube.locale = YouTubeLocale(gl = "ID", hl = "id")
        report("home+visitor", YouTube.home()) { (it as com.music.innertube.pages.HomePage).sections.size.toString() }
        report("search", YouTube.search("lofi", YouTube.SearchFilter.FILTER_SONG)) {
            (it as com.music.innertube.pages.SearchResult).items.size.toString()
        }
    }
}
