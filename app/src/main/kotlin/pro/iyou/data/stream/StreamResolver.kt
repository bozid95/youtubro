package pro.iyou.data.stream

import android.content.Context
import android.net.Uri
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pro.iyou.data.stream.potoken.PoTokenGenerator
import pro.iyou.data.stream.potoken.PoTokenResult
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val client: String,
)

/**
 * Turns a video id into a playable audio URL.
 *
 * Order, following what Mochy Music learned the hard way:
 * 1. InnerTubeX's extractor (handles ciphers, n-param and PoTokens itself).
 * 2. A short client walk: VISIONOS serves full tracks without a PoToken; the web clients
 *    follow with a BotGuard PoToken and NewPipe's signature / n-param solving.
 * 3. NewPipe's full StreamInfo as the last resort.
 */
class StreamResolver(context: Context, private val http: OkHttpClient) {

    private val poTokens = PoTokenGenerator(context)
    private val innerTubeX = InnerTubeXStreams(http) { videoId, sessionId ->
        poTokens.poTokenFor(videoId, sessionId)
    }
    private val cache = ConcurrentHashMap<String, ResolvedStream>()

    suspend fun prewarm() {
        runCatching { innerTubeX.prewarm() }.onFailure { Timber.w(it, "InnerTubeX prewarm failed") }
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    suspend fun resolve(videoId: String): ResolvedStream = withContext(Dispatchers.IO) {
        cache[videoId]?.takeIf { it.expiresAtMs - System.currentTimeMillis() > SAFETY_MARGIN_MS }
            ?.let { return@withContext it }

        val resolved = runCatching { innerTubeX.resolve(videoId) }
            .onFailure { Timber.w(it, "InnerTubeX failed for $videoId") }
            .getOrNull()
            ?: clientWalk(videoId)
            ?: newPipeFallback(videoId)
            ?: throw IOException("No playable stream for $videoId")

        Timber.i("Stream for $videoId via ${resolved.client}")
        cache[videoId] = resolved
        resolved
    }

    private suspend fun clientWalk(videoId: String): ResolvedStream? {
        var poToken: PoTokenResult? = null
        for (client in WALK_CLIENTS) {
            try {
                if (client.useWebPoTokens && poToken == null) {
                    val session = YouTube.visitorData ?: continue
                    poToken = poTokens.poTokenFor(videoId, session)
                }
                val sigTimestamp = if (client.useSignatureTimestamp) {
                    NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                } else null
                val response = YouTube.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = sigTimestamp,
                    poToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null,
                ).getOrNull() ?: continue
                if (response.playabilityStatus.status != "OK") {
                    Timber.d("${client.clientName}: ${response.playabilityStatus.status} ${response.playabilityStatus.reason}")
                    continue
                }
                val streaming = response.streamingData ?: continue
                val headers = client.streamHeaders()

                for (format in audioCandidates(response)) {
                    var url = if (client.useWebPoTokens || format.url == null) {
                        NewPipeExtractor.getStreamUrl(format, videoId)
                    } else {
                        format.url
                    } ?: continue
                    if (client.useWebPoTokens) {
                        val pot = poToken?.streamingDataPoToken ?: continue
                        url += (if ("?" in url) "&" else "?") + "pot=" + Uri.encode(pot)
                    }
                    if (!probe(url, headers, format.contentLength)) continue
                    return ResolvedStream(
                        url = url,
                        headers = headers,
                        expiresAtMs = System.currentTimeMillis() + streaming.expiresInSeconds * 1000L,
                        client = client.clientName,
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Client ${client.clientName} failed")
            }
        }
        return null
    }

    private fun newPipeFallback(videoId: String): ResolvedStream? {
        val streams = runCatching { YouTube.getNewPipeStreamUrls(videoId) }.getOrDefault(emptyList())
        val url = PREFERRED_AUDIO_ITAGS.firstNotNullOfOrNull { itag -> streams.find { it.first == itag }?.second }
            ?: return null
        return ResolvedStream(
            url = url,
            headers = mapOf("User-Agent" to YouTubeClient.USER_AGENT_WEB),
            expiresAtMs = System.currentTimeMillis() + 5 * 60_000L,
            client = "NewPipe",
        )
    }

    /** Best audio-only formats first: Opus, then AAC, highest bitrate within each. */
    private fun audioCandidates(response: PlayerResponse): List<PlayerResponse.StreamingData.Format> =
        response.streamingData?.adaptiveFormats.orEmpty()
            .filter { it.isAudio && it.isOriginal }
            .sortedWith(
                compareByDescending<PlayerResponse.StreamingData.Format> { it.mimeType.contains("opus") }
                    .thenByDescending { it.bitrate },
            )

    /**
     * googlevideo hands out links that look fine and then stop after ~1 MB when a token is
     * missing. Asking for the last byte catches those before the player does.
     */
    private fun probe(url: String, headers: Map<String, String>, contentLength: Long?): Boolean {
        val offset = contentLength?.takeIf { it > 1 }?.minus(1) ?: PROBE_FALLBACK_BYTE
        val request = Request.Builder().url(url).get()
            .header("Range", "bytes=$offset-$offset")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return runCatching {
            http.newCall(request).execute().use { r ->
                r.isSuccessful || r.code == 206 || (r.code == 416 && contentLength == null)
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val SAFETY_MARGIN_MS = 60_000L
        const val PROBE_FALLBACK_BYTE = 2_000_000L

        val WALK_CLIENTS = listOf(YouTubeClient.VISIONOS, YouTubeClient.WEB_REMIX, YouTubeClient.WEB)

        // 251/250/249 = Opus, 140/139 = AAC
        val PREFERRED_AUDIO_ITAGS = listOf(251, 140, 250, 249, 139)

        fun YouTubeClient.streamHeaders(): Map<String, String> = buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            if (clientName == "WEB_REMIX") {
                put("Referer", "https://music.youtube.com/")
                put("Origin", "https://music.youtube.com")
            } else {
                put("Referer", "https://www.youtube.com/")
                put("Origin", "https://www.youtube.com")
            }
        }
    }
}
