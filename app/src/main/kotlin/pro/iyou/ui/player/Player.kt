package pro.iyou.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.data.model.formatDuration
import pro.iyou.data.stream.StreamInfo
import pro.iyou.playback.PlayerState
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.components.Artwork
import pro.iyou.ui.components.TrackMenu
import pro.iyou.ui.theme.Palette

/** The floating bar above the tabs. Tap or swipe up to open the full player. */
@Composable
fun MiniPlayer(state: PlayerState, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.current ?: return
    val player = LocalPlayer.current
    val accent by rememberArtworkColor(track.thumbnail)
    val progress = if (state.durationMs > 0) state.positionMs / state.durationMs.toFloat() else 0f

    Box(
        modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(accent.darken(0.55f), Palette.SurfaceHigh)))
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag < -12) onExpand() }
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.thumbnail, Modifier.size(44.dp), RoundedCornerShape(10.dp), px = 120)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayPauseIcon(state, onClick = player::togglePlay, size = 44, filled = false)
            IconButton(onClick = player::next, enabled = state.hasNext) {
                Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = if (state.hasNext) Palette.Text else Palette.TextFaint)
            }
        }
        // hairline progress along the bottom edge
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(Palette.Text.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Palette.Text),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(state: PlayerState, onCollapse: () -> Unit) {
    val track = state.current ?: return
    val player = LocalPlayer.current
    val repo = LocalContext.current.app.container.repository
    val scope = rememberCoroutineScope()
    val liked by remember(track.id) { repo.isLiked(track.id) }.collectAsState(initial = false)
    val accent by rememberArtworkColor(track.thumbnail)
    val artScale by animateFloatAsState(
        if (state.isPlaying || state.isBuffering) 1f else 0.86f,
        spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "art",
    )
    var showQueue by remember { mutableStateOf(false) }
    val settings = LocalContext.current.app.container.settings
    val videoMode by settings.videoMode.collectAsState()
    val landscape = isLandscape()

    // dragging the slider shouldn't fight the 500 ms position ticks
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shownPosition = if (scrubbing) (scrubValue * duration).toLong() else state.positionMs

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .background(
                Brush.verticalGradient(
                    0f to accent.darken(0.35f),
                    0.55f to accent.darken(0.85f).copy(alpha = 0.6f),
                    1f to Palette.Background,
                ),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag > 18) onCollapse() }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Tutup", tint = Palette.Text, modifier = Modifier.size(30.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ModeSwitch(videoMode, onChange = { settings.setVideoMode(it) })
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Antrean", tint = Palette.Text)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (videoMode) {
                    // sideways, the full-screen video takes over; drawing it here too would steal its surface
                    val ratio = state.videoAspect ?: (16f / 9f)
                    if (!landscape) InlineVideo(state, Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f))
                } else Artwork(
                    track.thumbnail,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .scale(artScale)
                        .shadow(40.dp, RoundedCornerShape(24.dp), ambientColor = accent, spotColor = accent),
                    RoundedCornerShape(24.dp),
                    px = 1080,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.Text.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val heart by animateColorAsState(if (liked) Palette.Accent else Palette.Text, tween(250), label = "heart")
                IconButton(onClick = { scope.launch { repo.setLiked(track, !liked) } }) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Suka",
                        tint = heart,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Slider(
                value = (shownPosition / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = {
                    player.seekTo((scrubValue * duration).toLong())
                    scrubbing = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Text,
                    activeTrackColor = Palette.Text,
                    inactiveTrackColor = Palette.Text.copy(alpha = 0.18f),
                ),
                thumb = {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Palette.Text))
                },
                track = { sliderState ->
                    val fraction = sliderState.value
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Palette.Text.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.Text))
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(shownPosition / 1000), style = MaterialTheme.typography.labelMedium, color = Palette.Text.copy(alpha = 0.6f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StreamBadge(track.id) }
                Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.labelMedium, color = Palette.Text.copy(alpha = 0.6f))
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(Icons.Rounded.Shuffle, state.shuffle, "Acak", player::toggleShuffle)
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Palette.Text, modifier = Modifier.size(38.dp))
                }
                PlayPauseIcon(state, onClick = player::togglePlay, size = 76, filled = true)
                IconButton(onClick = player::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = if (state.hasNext) Palette.Text else Palette.TextFaint, modifier = Modifier.size(38.dp))
                }
                ToggleIcon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    state.repeatMode != Player.REPEAT_MODE_OFF,
                    "Ulangi",
                    player::cycleRepeat,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = Palette.Surface,
        ) {
            QueueSheet(state)
        }
    }
}

@Composable
private fun QueueSheet(state: PlayerState) {
    val player = LocalPlayer.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((state.currentIndex - 1).coerceAtLeast(0)) }

    Text(
        "Antrean",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
    )
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        itemsIndexed(state.queue) { i, t ->
            val current = i == state.currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { player.skipTo(i) }
                    .background(if (current) Palette.SurfaceHigh else Color.Transparent)
                    .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(t.thumbnail, Modifier.size(44.dp), RoundedCornerShape(8.dp), px = 120)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (current) Palette.Accent else Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(t.artist, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current) {
                    TrackMenu(t)
                } else {
                    IconButton(onClick = { player.removeAt(i) }) {
                        Icon(Icons.Rounded.Close, "Hapus", tint = Palette.TextFaint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseIcon(state: PlayerState, onClick: () -> Unit, size: Int, filled: Boolean) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) Palette.Text else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (filled) Palette.Background else Palette.Text
        if (state.isBuffering && !state.isPlaying) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size((size * 0.4f).dp))
        } else {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "Jeda" else "Putar",
                tint = tint,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, active: Boolean, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = if (active) Palette.Text else Palette.Text.copy(alpha = 0.45f))
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) Palette.Accent else Color.Transparent),
        )
    }
}

fun Color.darken(amount: Float): Color = Color(
    red = red * (1 - amount),
    green = green * (1 - amount),
    blue = blue * (1 - amount),
    alpha = alpha,
)

/**
 * Which audio is playing ("Opus · 160 kbps"); streams under 128 kbps are flagged, not skipped.
 * Tapping it shows the details and the volume normalisation switch.
 */
@Composable
private fun StreamBadge(videoId: String) {
    val container = LocalContext.current.app.container
    val all by container.streams.info.collectAsState()
    val normalize by container.settings.normalize.collectAsState()
    val info = all[videoId]
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (info?.isLow == true) Palette.Accent.copy(alpha = 0.18f) else Palette.Text.copy(alpha = 0.08f))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (info?.isLow == true) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Palette.Accent, modifier = Modifier.size(12.dp))
            }
            Text(
                when {
                    info == null -> "…"
                    info.isLow -> "${info.label} · rendah"
                    else -> info.label
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (info?.isLow == true) Palette.Accent else Palette.Text.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (info != null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Format  ${info.label}  (itag ${info.itag})", style = MaterialTheme.typography.bodySmall)
                    Text("Sumber  ${info.client}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    info.loudnessDb?.let {
                        Text("Loudness  %+.1f dB".format(it), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    if (info.isLow) {
                        Text(
                            "Di bawah ${StreamInfo.LOW_KBPS} kbps: YouTube hanya memberi versi ini untuk lagu ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Accent,
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text("Normalisasi volume") },
                trailingIcon = {
                    Switch(
                        checked = normalize,
                        onCheckedChange = { container.settings.setNormalize(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent),
                    )
                },
                onClick = { container.settings.setNormalize(!normalize) },
            )
        }
    }
}
