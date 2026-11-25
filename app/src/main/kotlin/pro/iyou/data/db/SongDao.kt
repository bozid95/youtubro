package pro.iyou.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM song WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    fun liked(): Flow<List<SongEntity>>

    @Query("SELECT * FROM song WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recent(limit: Int = 100): Flow<List<SongEntity>>

    @Query("SELECT likedAt IS NOT NULL FROM song WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query("SELECT * FROM song WHERE id = :id")
    suspend fun get(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(song: SongEntity)

    @Query("UPDATE song SET title = :title, artist = :artist, thumbnail = :thumbnail, durationSec = COALESCE(:durationSec, durationSec) WHERE id = :id")
    suspend fun updateInfo(id: String, title: String, artist: String, thumbnail: String, durationSec: Int?)

    @Query("UPDATE song SET likedAt = :likedAt WHERE id = :id")
    suspend fun setLiked(id: String, likedAt: Long?)

    @Query("UPDATE song SET lastPlayedAt = :at, playCount = playCount + 1 WHERE id = :id")
    suspend fun markPlayed(id: String, at: Long)

    @Query("UPDATE song SET lastPlayedAt = NULL")
    suspend fun clearHistory()

    @Transaction
    suspend fun upsertInfo(song: SongEntity) {
        insertIgnore(song)
        updateInfo(song.id, song.title, song.artist, song.thumbnail, song.durationSec)
    }
}
