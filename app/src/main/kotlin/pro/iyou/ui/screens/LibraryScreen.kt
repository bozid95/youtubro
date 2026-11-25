package pro.iyou.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.data.model.Track
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.components.Message
import pro.iyou.ui.components.TrackRow
import pro.iyou.ui.theme.Palette

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val repo = LocalContext.current.app.container.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    val liked by repo.liked.collectAsState(initial = emptyList())
    val recent by repo.recent.collectAsState(initial = emptyList())
    val list = if (tab == 0) liked else recent

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Koleksi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Segment("Disukai", tab == 0) { tab = 0 }
                Segment("Riwayat", tab == 1) { tab = 1 }
            }
        }
        if (list.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${list.size} lagu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    if (tab == 1) {
                        TextButton(onClick = { scope.launch { repo.clearHistory() } }) {
                            Text("Hapus", color = Palette.TextMuted)
                        }
                    }
                    RoundAction(Icons.Rounded.Shuffle, Palette.SurfaceHigh, Palette.Text) {
                        player.play(list.shuffled())
                    }
                    Spacer(Modifier.width(10.dp))
                    RoundAction(Icons.Rounded.PlayArrow, Palette.Text, Palette.Background) {
                        player.play(list)
                    }
                }
            }
        }
        itemsIndexed(list, key = { _, t -> t.id }) { i, t: Track ->
            TrackRow(t, onClick = { player.play(list, i) })
        }
        if (list.isEmpty()) {
            item {
                Message(
                    if (tab == 0) "Belum ada lagu yang disukai. Ketuk ♡ di pemutar untuk menyimpannya di sini."
                    else "Lagu yang kamu putar akan muncul di sini.",
                )
            }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Palette.Background else Palette.Text,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Palette.Text else Palette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
fun RoundAction(icon: ImageVector, background: Color, tint: Color, size: Int = 44, onClick: () -> Unit) {
    Icon(
        icon, null,
        tint = tint,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding((size / 4.4f).dp),
    )
}
