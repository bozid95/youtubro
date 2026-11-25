package pro.iyou.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette as AndroidPalette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pro.iyou.data.model.sized
import pro.iyou.ui.theme.Palette

private val cache = java.util.concurrent.ConcurrentHashMap<String, Color>()

/** A calm colour taken from the cover art, animated as songs change. */
@Composable
fun rememberArtworkColor(url: String): State<Color> {
    val context = LocalContext.current
    val target = remember { mutableStateOf(cache[url] ?: Palette.SurfaceHigh) }

    LaunchedEffect(url) {
        cache[url]?.let { target.value = it; return@LaunchedEffect }
        val color = withContext(Dispatchers.IO) {
            runCatching {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context).data(url.sized(120)).allowHardware(false).build(),
                ) as? SuccessResult ?: return@runCatching null
                val palette = AndroidPalette.from(result.image.toBitmap()).maximumColorCount(16).generate()
                val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                swatch?.rgb?.let { Color(it) }
            }.getOrNull()
        } ?: Palette.SurfaceHigh
        cache[url] = color
        target.value = color
    }
    return animateColorAsState(target.value, tween(600), label = "artColor")
}
