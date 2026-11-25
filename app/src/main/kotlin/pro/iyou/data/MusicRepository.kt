package pro.iyou.data

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pro.iyou.data.db.SongDao
import pro.iyou.data.model.Track
import pro.iyou.data.model.toEntity
import pro.iyou.data.model.toTrack

/** Something you can open from Home or Search that isn't a single song. */
data class Collection(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
) {
    enum class Kind { Album, Playlist }
}

data class Shelf(val title: String, val tracks: List<Track>, val collections: List<Collection>)

data class CollectionPage(
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val tracks: List<Track>,
)

enum class SearchKind { Songs, Videos, Albums, Playlists }

data class SearchResults(val tracks: List<Track>, val collections: List<Collection>)

class MusicRepository(private val songs: SongDao) {

    /** The first page of YouTube Music's home is often just one shelf; follow a few continuations. */
    suspend fun home(): Result<List<Shelf>> = YouTube.home().map { first ->
        val sections = first.sections.toMutableList()
        var next = first.continuation
        var pages = 0
        while (next != null && pages < 4 && sections.size < 10) {
            val page = YouTube.home(continuation = next).getOrNull() ?: break
            sections += page.sections
            next = page.continuation
            pages++
        }
        sections.mapNotNull { section ->
            val tracks = section.items.filterIsInstance<SongItem>().map { it.toTrack() }
            val collections = section.items.mapNotNull { it.toCollection() }
            if (tracks.isEmpty() && collections.isEmpty()) null
            else Shelf(section.title, tracks, collections)
        }
    }

    suspend fun suggestions(query: String): List<String> =
        YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty().take(8)

    suspend fun search(query: String, kind: SearchKind): Result<SearchResults> {
        val filter = when (kind) {
            SearchKind.Songs -> YouTube.SearchFilter.FILTER_SONG
            SearchKind.Videos -> YouTube.SearchFilter.FILTER_VIDEO
            SearchKind.Albums -> YouTube.SearchFilter.FILTER_ALBUM
            SearchKind.Playlists -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
        }
        return YouTube.search(query, filter).map { result ->
            SearchResults(
                tracks = result.items.filterIsInstance<SongItem>().map { it.toTrack() },
                collections = result.items.mapNotNull { it.toCollection() },
            )
        }
    }

    suspend fun collection(c: Collection): Result<CollectionPage> = when (c.kind) {
        Collection.Kind.Album -> YouTube.album(c.id).map { page ->
            CollectionPage(
                title = page.album.title,
                subtitle = listOfNotNull(
                    page.album.artists?.joinToString(", ") { it.name },
                    page.album.year?.toString(),
                ).joinToString(" · ").ifBlank { null },
                thumbnail = page.album.thumbnail,
                // album tracks often come without art; borrow the cover
                tracks = page.songs.map { it.toTrack().let { t -> if (t.thumbnail.isBlank()) t.copy(thumbnail = page.album.thumbnail) else t } },
            )
        }
        Collection.Kind.Playlist -> YouTube.playlist(c.id).map { page ->
            CollectionPage(
                title = page.playlist.title,
                subtitle = listOfNotNull(page.playlist.author?.name, page.playlist.songCountText)
                    .joinToString(" · ").ifBlank { null },
                thumbnail = page.playlist.thumbnail,
                tracks = page.songs.map { it.toTrack() },
            )
        }
    }

    /** YouTube Music's radio for a song: the song itself first, then what it would play next. */
    suspend fun radio(videoId: String): List<Track> =
        YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.items.orEmpty().map { it.toTrack() }

    // ---- library (local only, no account) ----

    val liked: Flow<List<Track>> = songs.liked().map { list -> list.map { it.toTrack() } }
    val recent: Flow<List<Track>> = songs.recent().map { list -> list.map { it.toTrack() } }
    fun isLiked(id: String): Flow<Boolean> = songs.isLiked(id).map { it == true }

    suspend fun setLiked(track: Track, liked: Boolean) {
        songs.upsertInfo(track.toEntity())
        songs.setLiked(track.id, if (liked) System.currentTimeMillis() else null)
    }

    suspend fun markPlayed(track: Track) {
        songs.upsertInfo(track.toEntity())
        songs.markPlayed(track.id, System.currentTimeMillis())
    }

    suspend fun clearHistory() = songs.clearHistory()

    private fun YTItem.toCollection(): Collection? = when (this) {
        is AlbumItem -> Collection(
            id = browseId,
            kind = Collection.Kind.Album,
            title = title,
            subtitle = listOfNotNull(artists?.joinToString(", ") { it.name }, year?.toString())
                .joinToString(" · ").ifBlank { "Album" },
            thumbnail = thumbnail,
        )
        is PlaylistItem -> Collection(
            id = id,
            kind = Collection.Kind.Playlist,
            title = title,
            subtitle = listOfNotNull(author?.name, songCountText).joinToString(" · ").ifBlank { "Playlist" },
            thumbnail = thumbnail,
        )
        is ArtistItem, is SongItem -> null
    }
}
