package app.sepia.music.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Long = 0,
    val thumb: String? = null,
    val localUri: String? = null,
    val liked: Boolean = false,
    val likedAt: Long = 0,
    val lastPlayed: Long = 0,
    val playCount: Int = 0,
    val inLibrary: Boolean = false
)

val Song.isLocal: Boolean get() = id.startsWith("local:")

@Entity(tableName = "downloads")
data class DlEntry(
    @PrimaryKey val songId: String,
    val path: String,
    val state: Int,
    val progress: Int,
    val size: Long,
    val createdAt: Long
)

object DlState {
    const val QUEUED = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
}

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val exportedAt: Long = 0
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")]
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long
)

@Entity(tableName = "search_history")
data class SearchEntry(
    @PrimaryKey val term: String,
    val ts: Long
)

data class PlaylistMeta(
    @Embedded val playlist: Playlist,
    val songCount: Int,
    val cover: String?
)

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(song: Song)

    @Query(
        "UPDATE songs SET title = :title, artist = :artist, album = COALESCE(:album, album), " +
            "durationSec = CASE WHEN :dur > 0 THEN :dur ELSE durationSec END, " +
            "thumb = COALESCE(:thumb, thumb), localUri = COALESCE(:localUri, localUri) WHERE id = :id"
    )
    suspend fun updateMeta(
        id: String,
        title: String,
        artist: String,
        album: String?,
        dur: Long,
        thumb: String?,
        localUri: String?
    )

    @Query("UPDATE songs SET lastPlayed = :ts, playCount = playCount + 1 WHERE id = :id")
    suspend fun markPlayed(id: String, ts: Long)

    @Query(
        "UPDATE songs SET liked = :liked, likedAt = :ts, " +
            "inLibrary = CASE WHEN :liked = 1 THEN 1 ELSE inLibrary END WHERE id = :id"
    )
    suspend fun setLiked(id: String, liked: Boolean, ts: Long)

    @Query("UPDATE songs SET inLibrary = 1 WHERE id = :id")
    suspend fun addToLibrary(id: String)

    @Query("SELECT * FROM songs WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun liked(): Flow<List<Song>>

    @Query("SELECT id FROM songs WHERE liked = 1")
    fun likedIds(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE inLibrary = 1 ORDER BY title COLLATE NOCASE")
    fun library(): Flow<List<Song>>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN downloads d ON d.songId = s.id " +
            "WHERE d.state = 2 ORDER BY d.createdAt DESC"
    )
    fun downloaded(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun byId(id: String): Song?

    @Query("SELECT COUNT(*) FROM songs WHERE liked = 1")
    fun likedCount(): Flow<Int>
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(d: DlEntry)

    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE songId = :id")
    suspend fun setState(id: String, state: Int, progress: Int)

    @Query("UPDATE downloads SET state = 2, progress = 100, path = :path, size = :size WHERE songId = :id")
    suspend fun finish(id: String, path: String, size: Long)

    @Query("DELETE FROM downloads WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloads")
    fun all(): Flow<List<DlEntry>>

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 2")
    fun doneCount(): Flow<Int>
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(p: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clear(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(ps: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid")
    suspend fun removeSong(pid: Long, sid: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :pid")
    suspend fun nextPosition(pid: Long): Int

    @Query(
        "SELECT p.*, " +
            "(SELECT COUNT(*) FROM playlist_songs WHERE playlistId = p.id) AS songCount, " +
            "(SELECT s.thumb FROM playlist_songs ps INNER JOIN songs s ON s.id = ps.songId " +
            "WHERE ps.playlistId = p.id ORDER BY ps.position LIMIT 1) AS cover " +
            "FROM playlists p ORDER BY p.createdAt DESC"
    )
    fun metas(): Flow<List<PlaylistMeta>>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId " +
            "WHERE ps.playlistId = :pid ORDER BY ps.position"
    )
    fun songs(pid: Long): Flow<List<Song>>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId " +
            "WHERE ps.playlistId = :pid ORDER BY ps.position"
    )
    suspend fun songsOnce(pid: Long): List<Song>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun allOnce(): List<Playlist>

    @Query("UPDATE playlists SET exportedAt = :ts WHERE id = :id")
    suspend fun markExported(id: Long, ts: Long)

    @Query("SELECT COUNT(*) FROM playlists WHERE exportedAt > 0")
    fun exportedCount(): Flow<Int>

    @Query("SELECT name FROM playlists WHERE id = :id")
    fun nameFlow(id: Long): Flow<String?>
}

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: SearchEntry)

    @Query("SELECT term FROM search_history ORDER BY ts DESC LIMIT 12")
    fun recent(): Flow<List<String>>

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Database(
    entities = [Song::class, DlEntry::class, Playlist::class, PlaylistSong::class, SearchEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun downloads(): DownloadDao
    abstract fun playlists(): PlaylistDao
    abstract fun search(): SearchDao
}
