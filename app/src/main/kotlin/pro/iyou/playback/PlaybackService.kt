package pro.iyou.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pro.iyou.MainActivity
import pro.iyou.R
import pro.iyou.app
import timber.log.Timber
import java.io.IOException

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

    override fun onCreate() {
        super.onCreate()
        val container = app.container

        cache = SimpleCache(
            cacheDir.resolve("media"),
            LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
            StandaloneDatabaseProvider(this),
        )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory()))
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
                val track = mediaItem?.toTrack() ?: return
                scope.launch(Dispatchers.IO) { container.repository.markPlayed(track) }
            }

            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                Timber.w(error, "Playback error on $id")
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
        cache.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val SCHEME = "yt"
    }
}
