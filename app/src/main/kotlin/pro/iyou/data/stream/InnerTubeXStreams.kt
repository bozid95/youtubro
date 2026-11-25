// Adapted from Mochy Music's InnerTubeXStreams (GPL-3.0), trimmed to anonymous playback.
package pro.iyou.data.stream

import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.music.innertube.YouTube
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import pro.iyou.data.stream.potoken.PoTokenResult
import timber.log.Timber
import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.extraction.AudioQuality as XAudioQuality
import com.metrolist.innertubex.extraction.PoTokenResult as XPoTokenResult
import com.metrolist.innertubex.models.YouTubeLocale as XLocale

/** InnerTubeX's stream finder: tried first, the client walk in [StreamResolver] is the backup. */
internal class InnerTubeXStreams(
    private val okHttp: OkHttpClient,
    private val mintToken: suspend (videoId: String, sessionId: String) -> PoTokenResult?,
) {
    private class Engine(val http: HttpClient, val innerTube: InnerTubeX, val extractor: InnerTubeExtractor)

    @Volatile private var engine: Engine? = null
    @Volatile private var mirroredVisitor: String? = null
    @Volatile private var mirroredLocale: XLocale? = null
    private val lock = Any()

    private val tokens = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true,
        )

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): XPoTokenResult? {
            val token = mintToken(videoId, visitorData) ?: return null
            return XPoTokenResult(token.playerRequestPoToken, token.streamingDataPoToken, visitorData)
        }
    }

    private val logger = InnerTubeLogger { event ->
        val message = event.message + if (event.details.isEmpty()) "" else
            event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        when (event.level) {
            InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
            InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
            InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
            InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
        }
    }

    private fun engine(): Engine = engine ?: synchronized(lock) {
        engine ?: build().also { engine = it }
    }

    private fun build(): Engine {
        val http = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })
            }
            engine { preconfigured = okHttp }
        }
        val innerTube = InnerTubeX(http, logger = logger)
        val configStore = RemotePlayerConfigStore(http, PlayerConfigRepository.disabled(), logger)
        val cipher = YouTubeCipherService(http, configStore, logger)
        val extractor = InnerTubeExtractor(
            configParser = YtConfigParserImpl(http, innerTube, configStore, logger, cipher),
            cipherService = cipher,
            innerTube = innerTube,
            tokenProvider = tokens,
            logger = logger,
        )
        return Engine(http, innerTube, extractor)
    }

    suspend fun prewarm() {
        val e = engine()
        mirrorSession(e.innerTube)
        e.extractor.prewarm()
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    suspend fun resolve(videoId: String): ResolvedStream? {
        val e = engine()
        mirrorSession(e.innerTube)
        val hints = ContentHints(isUploaded = false)
            .withPremium(false)
            // only plain links ExoPlayer can read straight through
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = false)

        val stream = withTimeoutOrNull(GIVE_UP_MS) {
            e.extractor.extract(
                videoId = videoId,
                hints = hints,
                excludedClients = emptySet(),
                audioQuality = XAudioQuality.HIGH,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            )
        } ?: return null
        if (stream.sabrBootstrap != null || stream.audioUrl.isBlank()) return null

        val expiresAt = stream.expiresAt?.toEpochMilliseconds()
            ?: (System.currentTimeMillis() + DEFAULT_LINK_LIFE_MS)
        return ResolvedStream(
            url = stream.audioUrl,
            headers = stream.headers,
            expiresAtMs = expiresAt,
            client = "InnerTubeX · ${stream.clientName}",
            info = StreamInfo(
                itag = stream.itag,
                codec = StreamInfo.codecOf(stream.mimeType ?: stream.codecs, stream.itag),
                kbps = stream.bitrate?.takeIf { it > 0 }?.let { it / 1000 } ?: StreamInfo.KNOWN_KBPS[stream.itag],
                loudnessDb = stream.loudnessDb,
                client = stream.clientName,
            ),
        )
    }

    private fun mirrorSession(innerTube: InnerTubeX) {
        val locale = XLocale(gl = YouTube.locale.gl, hl = YouTube.locale.hl)
        val visitor = YouTube.visitorData
        if (visitor == mirroredVisitor && locale == mirroredLocale) return
        synchronized(this) {
            innerTube.locale = locale
            innerTube.replaceSession(
                cookie = null,
                visitorData = visitor,
                dataSyncId = null,
                authUser = "0",
                useLoginForBrowse = false,
            )
            mirroredVisitor = visitor
            mirroredLocale = locale
        }
    }

    private companion object {
        const val GIVE_UP_MS = 12_000L
        const val DEFAULT_LINK_LIFE_MS = 5 * 60_000L
    }
}
