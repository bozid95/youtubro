package pro.iyou

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

class IyouApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        container = AppContainer(this)

        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it.length == 2 } ?: "US",
            hl = locale.toLanguageTag().ifBlank { "en" },
        )

        // A guest "visitor id" keeps YouTube from treating every request as a new bot.
        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        YouTube.visitorData = prefs.getString(KEY_VISITOR, null)
        appScope.launch(Dispatchers.IO) {
            if (YouTube.visitorData == null) {
                YouTube.visitorData().onSuccess {
                    YouTube.visitorData = it
                    prefs.edit().putString(KEY_VISITOR, it).apply()
                }.onFailure { Timber.w(it, "visitorData") }
            }
            container.streams.prewarm()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val KEY_VISITOR = "visitor_data"
    }
}

val android.content.Context.app: IyouApp get() = applicationContext as IyouApp
