package pro.iyou.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.data.Collection
import pro.iyou.data.MusicRepository
import pro.iyou.data.Shelf
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.components.CollectionTile
import pro.iyou.ui.components.Loading
import pro.iyou.ui.components.Message
import pro.iyou.ui.components.SectionTitle
import pro.iyou.ui.components.Tile
import pro.iyou.ui.components.TrackRow
import pro.iyou.ui.theme.Palette
import java.util.Calendar

/** Short technical reason shown under an error, so problems can be reported. */
fun Throwable.describe(): String {
    timber.log.Timber.w(this)
    val root = generateSequence(this) { it.cause }.last()
    return "${root::class.java.simpleName}: ${root.message.orEmpty().take(180)}"
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String, val detail: String? = null) : Load<Nothing>
}

class HomeViewModel(private val repo: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<Shelf>>>(Load.Loading)
    val state = _state.asStateFlow()
    val refreshing = MutableStateFlow(false)

    init { load() }

    fun load(pull: Boolean = false) = viewModelScope.launch {
        if (pull) refreshing.value = true else _state.value = Load.Loading
        _state.value = repo.home().fold(
            onSuccess = { Load.Ready(it) },
            onFailure = { Load.Failed("Tidak bisa memuat beranda.", it.describe()) },
        )
        refreshing.value = false
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun HomeScreen(onOpen: (Collection) -> Unit, contentPadding: PaddingValues) {
    val repo = LocalContext.current.app.container.repository
    val vm = viewModel { HomeViewModel(repo) }
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val player = LocalPlayer.current

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.load(pull = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                Text(
                    greeting(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp),
                )
                Text(
                    "Musik tanpa iklan, tetap jalan di latar belakang.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                )
            }
            when (val s = state) {
                Load.Loading -> item { Loading() }
                is Load.Failed -> item { Message(s.message, detail = s.detail, onRetry = { vm.load() }) }
                is Load.Ready -> s.value.forEachIndexed { i, shelf ->
                    item(key = "title$i") { SectionTitle(shelf.title) }
                    if (shelf.tracks.isNotEmpty()) {
                        item(key = "tracks$i") {
                            if (i == 0) {
                                // the first shelf ("Quick picks") reads best as a paged list
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(4),
                                    modifier = Modifier.fillMaxWidth().height(64.dp * 4),
                                ) {
                                    itemsIndexed(shelf.tracks) { _, t ->
                                        TrackRow(
                                            track = t,
                                            onClick = { player.playRadio(t) },
                                            modifier = Modifier.width(320.dp),
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(shelf.tracks) { t ->
                                        Tile(t.title, t.artist, t.thumbnail, onClick = { player.playRadio(t) })
                                    }
                                }
                            }
                        }
                    }
                    if (shelf.collections.isNotEmpty()) {
                        item(key = "collections$i") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(top = if (shelf.tracks.isNotEmpty()) 16.dp else 0.dp),
                            ) {
                                items(shelf.collections) { c ->
                                    CollectionTile(c) { onOpen(c) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
