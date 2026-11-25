package pro.iyou.data.model

import androidx.compose.runtime.Immutable
import com.music.innertube.models.SongItem
import pro.iyou.data.db.SongEntity

/** The one song shape the UI and the player share. */
@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val durationSec: Int? = null,
)

fun SongItem.toTrack() = Track(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    thumbnail = thumbnail,
    durationSec = duration,
)

fun SongEntity.toTrack() = Track(id, title, artist, thumbnail, durationSec)

fun Track.toEntity() = SongEntity(id = id, title = title, artist = artist, thumbnail = thumbnail, durationSec = durationSec)

/** YouTube thumbnails carry their size in the URL; ask for a sharper one. */
fun String.sized(px: Int): String = when {
    contains("googleusercontent.com") || contains("ggpht.com") ->
        replace(Regex("=w\d+-h\d+.*$"), "=w$px-h$px-l90-rj")
    contains("i.ytimg.com") -> replace(Regex("/(default|mqdefault|hqdefault|sddefault)\.jpg"), "/hqdefault.jpg")
    else -> this
}

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
