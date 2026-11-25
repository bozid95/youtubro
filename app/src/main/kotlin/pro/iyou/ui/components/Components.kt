package pro.iyou.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.data.Collection
import pro.iyou.data.model.Track
import pro.iyou.data.model.formatDuration
import pro.iyou.data.model.sized
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.theme.Palette

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    px: Int = 226,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Palette.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Palette.TextFaint, modifier = Modifier.size(24.dp))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url.sized(px),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** A song in a list. Long-press or ⋮ for the small menu. */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val isCurrent = state.current?.id == track.id

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint,
                modifier = Modifier.width(28.dp),
            )
        } else {
            Box {
                Artwork(track.thumbnail, Modifier.size(48.dp), RoundedCornerShape(8.dp), px = 120)
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Palette.Background.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) Palette.Accent else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    track.artist.ifBlank { null },
                    track.durationSec?.let { formatDuration(it.toLong()) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TrackMenu(track)
    }
}

@Composable
fun TrackMenu(track: Track) {
    val player = LocalPlayer.current
    val repo = LocalContext.current.app.container.repository
    val scope = rememberCoroutineScope()
    val liked by remember(track.id) { repo.isLiked(track.id) }.collectAsState(initial = false)
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Menu", tint = Palette.TextMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Putar berikutnya") },
                leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, null) },
                onClick = { player.playNext(track); open = false },
            )
            DropdownMenuItem(
                text = { Text("Tambah ke antrean") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { player.addToQueue(track); open = false },
            )
            DropdownMenuItem(
                text = { Text("Mulai radio") },
                leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                onClick = { player.playRadio(track); open = false },
            )
            DropdownMenuItem(
                text = { Text(if (liked) "Hapus dari disukai" else "Suka") },
                leadingIcon = {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
                },
                onClick = {
                    scope.launch { repo.setLiked(track, !liked) }
                    open = false
                },
            )
        }
    }
}

/** Square tile for horizontal shelves: songs, albums and playlists all use it. */
@Composable
fun Tile(
    title: String,
    subtitle: String?,
    thumbnail: String?,
    onClick: () -> Unit,
    width: Dp = 152.dp,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Artwork(thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), shape, px = 400)
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CollectionTile(c: Collection, onClick: () -> Unit) =
    Tile(c.title, c.subtitle, c.thumbnail, onClick)

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Text, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier, detail: String? = null, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Coba lagi", color = Palette.Text) }
        }
    }
}
