package pro.iyou.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pro.iyou.data.MusicRepository
import pro.iyou.data.model.Track
import timber.log.Timber

@Immutable
data class PlayerState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
)

/** The UI's handle on [PlaybackService], through a Media3 [MediaController]. */
class PlayerConnection(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: MusicRepository,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var radioJob: Job? = null
    private val connected = kotlinx.coroutines.CompletableDeferred<Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        scope.launch(Dispatchers.Main) {
            runCatching { MediaController.Builder(context, token).buildAsync().await() }
                .onSuccess {
                    controller = it
                    it.addListener(listener)
                    sync()
                    connected.complete(Unit)
                }
                .onFailure { Timber.e(it, "MediaController") }
        }
    }


    /** Open a song from a link: look it up, then play it with its radio queued behind. */
    suspend fun playVideoId(videoId: String) {
        connected.await()
        val radio = repository.radio(videoId)
        if (radio.tracks.isEmpty()) return
        play(radio.tracks, radio.currentIndex)
    }

    /** Play a list as the queue, starting at [index]. */
    fun play(tracks: List<Track>, index: Int = 0) {
        radioJob?.cancel()
        start(tracks, index)
    }

    private fun start(tracks: List<Track>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0)
        c.prepare()
        c.play()
    }

    /** Play one song now and fill the queue with YouTube Music's radio for it. */
    fun playRadio(track: Track) {
        radioJob?.cancel()
        if (track.isVideo) {
            // a music video: fetch the radio first, it swaps in the song version when there is one
            radioJob = scope.launch {
                val radio = repository.radio(track.id)
                if (radio.tracks.isEmpty()) start(listOf(track), 0) else start(radio.tracks, radio.currentIndex)
            }
            return
        }
        start(listOf(track), 0)
        radioJob = scope.launch {
            val related = repository.radio(track.id).tracks.filter { it.id != track.id }
            val c = controller ?: return@launch
            if (c.currentMediaItem?.mediaId == track.id && related.isNotEmpty()) {
                c.addMediaItems(related.map { it.toMediaItem() })
            }
        }
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(track.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNext() ?: Unit

    fun previous() {
        val c = controller ?: return
        // like every music app: past 3 s, "previous" restarts the song
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0)
        c.play()
    }

    fun removeAt(index: Int) = controller?.removeMediaItem(index) ?: Unit

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun sync() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }
        _state.value = PlayerState(
            current = c.currentMediaItem?.toTrack(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET }
                ?: c.currentMediaItem.durationMsHint(),
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem(),
        )
        if (c.isPlaying) startTicker() else stopTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val c = controller ?: break
                _state.update {
                    it.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs,
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    fun release() {
        stopTicker()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun MediaItem?.durationMsHint(): Long =
        this?.toTrack()?.durationSec?.times(1000L) ?: 0L

}
