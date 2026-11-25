package pro.iyou.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pro.iyou.MainActivity
import pro.iyou.R
import pro.iyou.app
import timber.log.Timber
import java.io.IOException
import kotlin.math.pow

/**
 * Owns the player. Because it is a foreground [MediaSessionService], music keeps going with the
 * screen off or the app in the background, and Media3 draws the media notification for us.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var cache: SimpleCache

    /** media ids already retried once after a stream error, so a dead song can't loop forever */
    private val retried = mutableSetOf<String>()

    /** boosts quiet songs when normalising; loud ones are simply turned down with the player volume */
    private var enhancer: LoudnessEnhancer? = null

    override fun onCreate() {
        super.onCreate()
        val container = app.container

        cache = SimpleCache(
            cacheDir.resolve("media"),
            LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
            StandaloneDatabaseProvider(this),
        )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(SongSourceFactory(DefaultMediaSourceFactory(dataSourceFactory()), videoDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retried.clear()
                applyLoudness()
                val track = mediaItem?.toTrack() ?: return
                scope.launch(Dispatchers.IO) { container.repository.markPlayed(track) }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachEnhancer(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                Timber.w(error, "Playback error on $id")
                if (container.settings.videoMode.value && container.streams.hasVideo(id)) {
                    // most likely the video half; carry on with the song as audio only
                    container.streams.markVideoFailed(id)
                    rebuildQueue(playWhenReady = true)
                    return
                }
                if (retried.add(id)) {
                    // stream links expire or get refused; fetch a fresh one and pick up where we were
                    container.streams.invalidate(id)
                    val position = player.currentPosition
                    player.seekTo(player.currentMediaItemIndex, position)
                    player.prepare()
                    player.play()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        attachEnhancer(player.audioSessionId)
        // switching Song / Video rebuilds the queue so each item gets the right kind of source
        scope.launch {
            container.settings.videoMode.drop(1).collect { rebuildQueue(player.playWhenReady) }
        }
        // the loudness of a song is known once its stream is resolved, which can be after it started
        scope.launch {
            combine(container.streams.info, container.settings.normalize) { _, _ -> }.collect { applyLoudness() }
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
    }

    /**
     * Each song's URI is `yt:<videoId>`. Right before ExoPlayer opens it, we swap in a real
     * googlevideo link (resolved through Mochy's InnerTube layer). Bytes already in the cache
     * are played without asking YouTube again.
     */
    private fun dataSourceFactory(): ResolvingDataSource.Factory {
        val http = OkHttpDataSource.Factory(app.container.http)
        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ResolvingDataSource.Factory(cached) { spec: DataSpec ->
            if (spec.uri.scheme != SCHEME) return@Factory spec
            val videoId = spec.key ?: spec.uri.schemeSpecificPart
            val wanted = if (spec.length >= 0) spec.length else 1L
            if (cache.isCached(videoId, spec.position, wanted)) return@Factory spec

            val stream = try {
                runBlocking { app.container.streams.resolve(videoId) }
            } catch (e: Exception) {
                throw HttpDataSource.HttpDataSourceException(
                    IOException(e.message, e), spec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            spec.withUri(stream.url.toUri())
                .withRequestHeaders(spec.httpRequestHeaders + stream.headers)
        }
    }

    private fun rebuildQueue(playWhenReady: Boolean) {
        if (player.mediaItemCount == 0) return
        val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        player.setMediaItems(items, index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /**
     * Audio as before; in video mode the song's video-only stream is merged in next to it.
     * Video bytes skip the media cache so they can't push songs out of it.
     */
    private inner class SongSourceFactory(
        private val audio: DefaultMediaSourceFactory,
        video: DataSource.Factory,
    ) : MediaSource.Factory {
        private val videoSources = ProgressiveMediaSource.Factory(video)

        override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider) = apply {
            audio.setDrmSessionManagerProvider(provider)
            videoSources.setDrmSessionManagerProvider(provider)
        }

        override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy) = apply {
            audio.setLoadErrorHandlingPolicy(policy)
            videoSources.setLoadErrorHandlingPolicy(policy)
        }

        override fun getSupportedTypes(): IntArray = audio.supportedTypes

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val audioSource = audio.createMediaSource(mediaItem)
            val id = mediaItem.mediaId
            val container = app.container
            if (!container.settings.videoMode.value || !container.streams.hasVideo(id)) return audioSource
            val videoSource = videoSources.createMediaSource(
                MediaItem.Builder().setMediaId(id).setUri("$VIDEO_SCHEME:$id").build(),
            )
            return MergingMediaSource(/* adjustPeriodTimeOffsets = */ true, /* clipDurations = */ true, audioSource, videoSource)
        }
    }

    private fun videoDataSourceFactory(): ResolvingDataSource.Factory {
        val upstream = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(app.container.http))
        return ResolvingDataSource.Factory(upstream) { spec: DataSpec ->
            if (spec.uri.scheme != VIDEO_SCHEME) return@Factory spec
            val videoId = spec.uri.schemeSpecificPart
            val video = try {
                runBlocking { app.container.streams.resolveVideo(videoId) }
            } catch (e: Exception) {
                throw HttpDataSource.HttpDataSourceException(
                    IOException(e.message, e), spec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            spec.withUri(video.url.toUri()).withRequestHeaders(spec.httpRequestHeaders + video.headers)
        }
    }

    private fun attachEnhancer(sessionId: Int) {
        enhancer?.release()
        enhancer = if (sessionId == C.AUDIO_SESSION_ID_UNSET) null else
            runCatching { LoudnessEnhancer(sessionId) }.onFailure { Timber.w(it, "LoudnessEnhancer") }.getOrNull()
        applyLoudness()
    }

    /**
     * Volume normalisation like the YouTube apps: YouTube reports how far each track sits from its
     * loudness target. Louder tracks are turned down by that much; quieter ones get up to +6 dB back.
     */
    private fun applyLoudness() {
        val container = app.container
        val id = player.currentMediaItem?.mediaId
        val loudness = if (container.settings.normalize.value && id != null) {
            container.streams.info.value[id]?.loudnessDb
        } else null
        val gainDb = loudness?.let { (-it).coerceIn(-MAX_CUT_DB, MAX_BOOST_DB) } ?: 0.0

        player.volume = if (gainDb < 0) 10.0.pow(gainDb / 20).toFloat() else 1f
        enhancer?.runCatching {
            setTargetGain(if (gainDb > 0) (gainDb * 100).toInt() else 0)
            enabled = gainDb > 0
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // swiping the app away while paused ends the session; while playing, music carries on
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        enhancer?.release()
        enhancer = null
        cache.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val SCHEME = "yt"
        const val VIDEO_SCHEME = "ytv"
        const val MAX_CUT_DB = 15.0
        const val MAX_BOOST_DB = 6.0
    }
}
