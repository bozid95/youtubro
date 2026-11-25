package pro.iyou.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The few switches the app has. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _normalize = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE, true))
    /** Even out loudness between songs, the way the YouTube apps do. */
    val normalize: StateFlow<Boolean> = _normalize.asStateFlow()

    private val _videoMode = MutableStateFlow(prefs.getBoolean(KEY_VIDEO, false))
    /** Play the video along with the audio ("Video" side of the Song / Video switch). */
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    fun setNormalize(on: Boolean) {
        _normalize.value = on
        prefs.edit().putBoolean(KEY_NORMALIZE, on).apply()
    }

    fun setVideoMode(on: Boolean) {
        _videoMode.value = on
        prefs.edit().putBoolean(KEY_VIDEO, on).apply()
    }

    private companion object {
        const val KEY_NORMALIZE = "normalize_volume"
        const val KEY_VIDEO = "video_mode"
    }
}
