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

    fun setNormalize(on: Boolean) {
        _normalize.value = on
        prefs.edit().putBoolean(KEY_NORMALIZE, on).apply()
    }

    private companion object {
        const val KEY_NORMALIZE = "normalize_volume"
    }
}
