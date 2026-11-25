package pro.iyou.data.stream

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What audio is actually playing for a song: shown in the player and used for volume normalisation. */
data class StreamInfo(
    val itag: Int,
    val codec: String,
    val kbps: Int?,
    /** YouTube's loudness of the track relative to its target; positive = louder than target */
    val loudnessDb: Double?,
    val client: String,
) {
    /** Below what YouTube Music gives free accounts at "high" (Opus 251 / AAC 140). Still played, just flagged. */
    val isLow: Boolean
        get() = itag !in FULL_QUALITY_ITAGS && (kbps?.let { it < LOW_KBPS } ?: true)

    val label: String
        get() = listOfNotNull(codec, kbps?.let { "$it kbps" }).joinToString(" · ")

    companion object {
        const val LOW_KBPS = 128
        val FULL_QUALITY_ITAGS = setOf(251, 140)

        /** Rough bitrates for itags when YouTube doesn't tell us (NewPipe's fallback). */
        val KNOWN_KBPS = mapOf(251 to 160, 140 to 128, 250 to 70, 249 to 50, 139 to 48)

        fun codecOf(mimeType: String?, itag: Int): String = when {
            mimeType?.contains("opus", true) == true -> "Opus"
            mimeType?.contains("mp4a", true) == true || mimeType?.contains("audio/mp4", true) == true -> "AAC"
            itag in setOf(249, 250, 251) -> "Opus"
            itag in setOf(139, 140, 141) -> "AAC"
            else -> mimeType?.substringAfter('/')?.substringBefore(';')?.uppercase() ?: "?"
        }
    }
}

/**
 * Remembers [StreamInfo] per song across launches, because a song played from the media cache
 * never asks YouTube again and would otherwise have no format or loudness to show.
 */
class StreamInfoStore(context: Context) {
    private val prefs = context.getSharedPreferences("stream_info", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, StreamInfo>> = _all.asStateFlow()

    fun put(videoId: String, info: StreamInfo) {
        _all.update { it + (videoId to info) }
        val editor = prefs.edit()
        if (prefs.all.size > MAX_ENTRIES) editor.clear()
        editor.putString(videoId, encode(info)).apply()
    }

    private fun load(): Map<String, StreamInfo> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let(::decode)?.let { k to it } }.toMap()

    private fun encode(i: StreamInfo) = listOf(i.itag, i.codec, i.kbps ?: "", i.loudnessDb ?: "", i.client).joinToString("|")

    private fun decode(s: String): StreamInfo? = runCatching {
        val p = s.split("|", limit = 5)
        StreamInfo(p[0].toInt(), p[1], p[2].toIntOrNull(), p[3].toDoubleOrNull(), p[4])
    }.getOrNull()

    private companion object {
        const val MAX_ENTRIES = 1000
    }
}
