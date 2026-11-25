package pro.iyou.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import pro.iyou.data.model.Track
import pro.iyou.data.model.sized

private const val EXTRA_THUMB = "iyou.thumb"
private const val EXTRA_DURATION = "iyou.duration"

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("${PlaybackService.SCHEME}:$id")
    .setCustomCacheKey(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumbnail.sized(544).toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply {
                putString(EXTRA_THUMB, thumbnail)
                durationSec?.let { putInt(EXTRA_DURATION, it) }
            })
            .build(),
    )
    .build()

fun MediaItem.toTrack(): Track {
    val m = mediaMetadata
    val extras = m.extras
    return Track(
        id = mediaId,
        title = m.title?.toString().orEmpty(),
        artist = m.artist?.toString().orEmpty(),
        thumbnail = extras?.getString(EXTRA_THUMB) ?: m.artworkUri?.toString().orEmpty(),
        durationSec = extras?.takeIf { it.containsKey(EXTRA_DURATION) }?.getInt(EXTRA_DURATION),
    )
}
