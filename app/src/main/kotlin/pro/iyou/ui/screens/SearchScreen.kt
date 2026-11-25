package pro.iyou.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.data.Collection
import pro.iyou.data.MusicRepository
import pro.iyou.data.SearchKind
import pro.iyou.data.SearchResults
import pro.iyou.ui.LocalPlayer
import pro.iyou.ui.components.Artwork
import pro.iyou.ui.components.Loading
import pro.iyou.ui.components.Message
import pro.iyou.ui.components.TrackRow
import pro.iyou.ui.theme.Palette

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repo: MusicRepository,
    private val prefs: android.content.SharedPreferences,
) : ViewModel() {
    val query = MutableStateFlow("")
    val kind = MutableStateFlow(SearchKind.Songs)
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    private val _results = MutableStateFlow<Load<SearchResults>?>(null)
    val results = _results.asStateFlow()
    val history = MutableStateFlow(readHistory())
    private var searchJob: Job? = null
    private var lastSubmitted: String? = null

    init {
        query.debounce(250).distinctUntilChanged().onEach { q ->
            suggestions.value = if (q.isBlank() || q == lastSubmitted) emptyList() else repo.suggestions(q)
        }.launchIn(viewModelScope)
    }

    fun submit(q: String = query.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        query.value = text
        suggestions.value = emptyList()
        lastSubmitted = text
        saveToHistory(text)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _results.value = Load.Loading
            _results.value = repo.search(text, kind.value).fold(
                onSuccess = { Load.Ready(it) },
                onFailure = { Load.Failed("Pencarian gagal.", it.describe()) },
            )
        }
    }

    fun setKind(k: SearchKind) {
        kind.value = k
        if (_results.value != null) submit()
    }

    fun clear() {
        query.value = ""
        _results.value = null
        suggestions.value = emptyList()
    }

    fun forget(q: String) {
        history.value = history.value - q
        prefs.edit().putString(KEY, history.value.joinToString("\n")).apply()
    }

    private fun saveToHistory(q: String) {
        history.value = (listOf(q) + history.value.filter { !it.equals(q, ignoreCase = true) }).take(12)
        prefs.edit().putString(KEY, history.value.joinToString("\n")).apply()
    }

    private fun readHistory(): List<String> =
        prefs.getString(KEY, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    private companion object { const val KEY = "history" }
}

@Composable
fun SearchScreen(
    onOpen: (Collection) -> Unit,
    contentPadding: PaddingValues,
    request: String? = null,
    onRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm = viewModel {
        SearchViewModel(
            context.app.container.repository,
            context.getSharedPreferences("search", android.content.Context.MODE_PRIVATE),
        )
    }
    val query by vm.query.collectAsState()
    val kind by vm.kind.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.history.collectAsState()
    val player = LocalPlayer.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(request) {
        if (request != null) {
            vm.submit(request)
            focus.clearFocus()
            onRequestHandled()
        } else if (results == null) {
            focusRequester.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Cari",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        )

        // the search pill
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.SurfaceHigh)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Palette.TextMuted)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Lagu, artis, album…", color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, "Hapus",
                    tint = Palette.TextMuted,
                    modifier = Modifier.clip(CircleShape).clickable { vm.clear(); focusRequester.requestFocus() }.padding(4.dp),
                )
            }
        }

        // filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SearchKind.entries) { k ->
                val selected = k == kind
                Text(
                    k.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Palette.Background else Palette.Text,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) Palette.Text else Palette.Surface)
                        .clickable { vm.setKind(k) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            when {
                suggestions.isNotEmpty() -> items(suggestions) { s ->
                    SuggestionRow(s, Icons.Rounded.Search, onClick = { vm.submit(s); focus.clearFocus() })
                }
                results == null -> {
                    if (history.isEmpty()) {
                        item { Message("Cari lagu favoritmu — tanpa iklan, bisa diputar di latar belakang.") }
                    } else {
                        items(history) { h ->
                            SuggestionRow(h, Icons.Rounded.History, onClick = { vm.submit(h); focus.clearFocus() }, onRemove = { vm.forget(h) })
                        }
                    }
                }
                else -> when (val r = results) {
                    Load.Loading -> item { Loading() }
                    is Load.Failed -> item { Message(r.message, detail = r.detail, onRetry = { vm.submit() }) }
                    is Load.Ready -> {
                        val tracks = r.value.tracks
                        if (tracks.isEmpty() && r.value.collections.isEmpty()) {
                            item { Message("Tidak ada hasil.") }
                        }
                        itemsIndexed(tracks) { _, t ->
                            TrackRow(t, onClick = { player.playRadio(t) })
                        }
                        items(r.value.collections) { c -> CollectionRow(c) { onOpen(c) } }
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.TextFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        if (onRemove != null) {
            Icon(
                Icons.Rounded.Close, "Hapus",
                tint = Palette.TextFaint,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRemove).padding(4.dp),
            )
        } else {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CollectionRow(c: Collection, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(c.thumbnail, Modifier.size(56.dp), RoundedCornerShape(10.dp), px = 160)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                c.subtitle ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                maxLines = 1,
            )
        }
    }
}

private fun SearchKind.label() = when (this) {
    SearchKind.Songs -> "Lagu"
    SearchKind.Videos -> "Video"
    SearchKind.Albums -> "Album"
    SearchKind.Playlists -> "Playlist"
}
