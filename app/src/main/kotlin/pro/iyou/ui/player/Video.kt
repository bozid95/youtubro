package pro.iyou.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import pro.iyou.app
import pro.iyou.data.model.formatDuration
import pro.iyou.playback.PlayerState
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.theme.Palette

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Turn the phone sideways for the video, whatever the rotation lock says (like the YouTube app). */
fun Activity.enterVideoFullscreen() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

/** Where the player draws its picture. Only the most recently attached surface shows video. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                this.player = player
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        onRelease = { view -> view.player = null },
    )
}

/** "Lagu | Video" pill at the top of the full player. */
@Composable
fun ModeSwitch(videoMode: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Palette.Text.copy(alpha = 0.1f))
            .padding(3.dp),
    ) {
        listOf("Lagu" to false, "Video" to true).forEach { (label, value) ->
            val selected = videoMode == value
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Palette.Background else Palette.Text.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Palette.Text else Color.Transparent)
                    .clickable { if (!selected) onChange(value) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Shown over everything while the phone is sideways in video mode: edge-to-edge picture,
 * system bars hidden, controls that fade away after a few seconds.
 */
@Composable
fun VideoFullscreenHost(state: PlayerState, playerVisible: Boolean) {
    val container = LocalContext.current.app.container
    val activity = LocalContext.current.findActivity()
    val videoMode by container.settings.videoMode.collectAsState()
    val landscape = isLandscape()

    // leaving video mode or the player hands rotation back to the system
    LaunchedEffect(videoMode, playerVisible) {
        if (!videoMode || !playerVisible) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    if (videoMode && playerVisible && landscape && state.current != null) {
        VideoFullscreen(
            state,
            onExit = {
                // back to portrait now; after a moment the sensor decides again, so turning the
                // phone sideways later goes straight back to full screen
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            },
        )
    }

    LaunchedEffect(landscape) {
        if (!landscape && activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            delay(2500)
            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

@Composable
private fun VideoFullscreen(state: PlayerState, onExit: () -> Unit) {
    val player = LocalPlayer.current
    val track = state.current ?: return
    val activity = LocalContext.current.findActivity()
    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableStateOf(0L) }

    BackHandler(onBack = onExit)

    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(controlsVisible, lastTouch, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shown = if (scrubbing) (scrubValue * duration).toLong() else state.positionMs

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
                lastTouch = System.currentTimeMillis()
            },
    ) {
        VideoSurface(player.media, Modifier.fillMaxSize())

        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(40.dp).align(Alignment.Center),
            )
        }

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.65f),
                            0.3f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Keluar layar penuh", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { player.previous(); lastTouch = System.currentTimeMillis() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { player.togglePlay(); lastTouch = System.currentTimeMillis() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.isPlaying) "Jeda" else "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    IconButton(
                        onClick = { player.next(); lastTouch = System.currentTimeMillis() },
                        enabled = state.hasNext,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext, "Berikutnya",
                            tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.35f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatDuration(shown / 1000)} / ${formatDuration(state.durationMs / 1000)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onExit) {
                            Icon(Icons.Rounded.FullscreenExit, "Keluar layar penuh", tint = Color.White)
                        }
                    }
                    Slider(
                        value = (shown / duration.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { scrubbing = true; scrubValue = it; lastTouch = System.currentTimeMillis() },
                        onValueChangeFinished = {
                            player.seekTo((scrubValue * duration).toLong())
                            scrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Palette.Accent,
                            activeTrackColor = Palette.Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                    )
                }
            }
        }
    }
}

/** The video in the portrait player, with a button to go full screen. */
@Composable
fun InlineVideo(state: PlayerState, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val activity = LocalContext.current.findActivity()
    Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        VideoSurface(player.media, Modifier.fillMaxSize())
        // until the first frame (or for good, if this song has no video) show the cover
        if (state.videoAspect == null) {
            pro.iyou.ui.components.Artwork(
                state.current?.thumbnail,
                Modifier.fillMaxSize(),
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                px = 720,
            )
        }
        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp).align(Alignment.Center),
            )
        }
        IconButton(
            onClick = { activity?.enterVideoFullscreen() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
        ) {
            Icon(Icons.Rounded.Fullscreen, "Layar penuh", tint = Color.White)
        }
    }
}
