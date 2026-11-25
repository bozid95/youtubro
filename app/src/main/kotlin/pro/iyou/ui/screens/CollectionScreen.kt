package pro.iyou.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.data.Collection
import pro.iyou.data.CollectionPage
import pro.iyou.data.MusicRepository
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.components.Artwork
import pro.iyou.ui.components.Loading
import pro.iyou.ui.components.Message
import pro.iyou.ui.components.TrackRow
import pro.iyou.ui.theme.Palette

class CollectionViewModel(private val repo: MusicRepository, private val target: Collection) : ViewModel() {
    private val _state = MutableStateFlow<Load<CollectionPage>>(Load.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = Load.Loading
        _state.value = repo.collection(target).fold(
            onSuccess = { Load.Ready(it) },
            onFailure = { Load.Failed("Tidak bisa membuka ${if (target.kind == Collection.Kind.Album) "album" else "playlist"} ini.", it.describe()) },
        )
    }
}

@Composable
fun CollectionScreen(target: Collection, onBack: () -> Unit, contentPadding: PaddingValues) {
    val repo = LocalContext.current.app.container.repository
    val vm = viewModel(key = target.kind.name + target.id) { CollectionViewModel(repo, target) }
    val state by vm.state.collectAsState()
    val player = LocalPlayer.current
    val page = (state as? Load.Ready)?.value
    val isAlbum = target.kind == Collection.Kind.Album

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = 32.dp, end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Artwork(
                        page?.thumbnail ?: target.thumbnail,
                        Modifier
                            .size(232.dp)
                            .shadow(32.dp, RoundedCornerShape(18.dp), ambientColor = Palette.Background),
                        RoundedCornerShape(18.dp),
                        px = 720,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        page?.title ?: target.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = page?.subtitle ?: target.subtitle
                    if (!sub.isNullOrBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (page != null && page.tracks.isNotEmpty()) {
                        Row(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundAction(Icons.Rounded.Shuffle, Palette.SurfaceHigh, Palette.Text, size = 48) {
                                player.play(page.tracks.shuffled())
                            }
                            Spacer(Modifier.width(16.dp))
                            RoundAction(Icons.Rounded.PlayArrow, Palette.Text, Palette.Background, size = 60) {
                                player.play(page.tracks)
                            }
                        }
                    }
                }
            }
            when (val s = state) {
                Load.Loading -> item { Loading() }
                is Load.Failed -> item { Message(s.message, detail = s.detail, onRetry = { vm.load() }) }
                is Load.Ready -> itemsIndexed(s.value.tracks) { i, t ->
                    TrackRow(
                        t,
                        onClick = { player.play(s.value.tracks, i) },
                        index = if (isAlbum) i + 1 else null,
                    )
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Palette.Text)
        }
    }
}
