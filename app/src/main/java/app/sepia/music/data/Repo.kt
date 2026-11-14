package app.sepia.music.data

import app.sepia.music.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Repo(db: AppDb, scope: CoroutineScope) {
    private val songs = db.songs()
    private val dls = db.downloads()
    private val lists = db.playlists()
    private val searches = db.search()

    val recent: Flow<List<Song>> = songs.recent(14)
    val liked: Flow<List<Song>> = songs.liked()
    val library: Flow<List<Song>> = songs.library()
    val downloaded: Flow<List<Song>> = songs.downloaded()
    val playlists: Flow<List<PlaylistMeta>> = lists.metas()
    val likedCount: Flow<Int> = songs.likedCount()
    val downloadedCount: Flow<Int> = dls.doneCount()
    val exportedCount: Flow<Int> = lists.exportedCount()
    val searchHistory: Flow<List<String>> = searches.recent()

    val likedIds: StateFlow<Set<String>> =
        songs.likedIds().map { it.toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val downloads: StateFlow<Map<String, DlEntry>> =
        dls.all().map { l -> l.associateBy { it.songId } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    // ---- songs -----------------------------------------------------------------------------

    /** Insert the song if new, otherwise refresh its metadata (keeps likes, play counts...). */
    suspend fun upsertMeta(s: Song) {
        songs.insertIgnore(s.copy(liked = false, likedAt = 0, lastPlayed = 0, playCount = 0, inLibrary = false))
        songs.updateMeta(s.id, s.title, s.artist, s.album, s.durationSec, s.thumb, s.localUri)
    }

    suspend fun song(id: String): Song? = songs.byId(id)

    suspend fun songsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val map = songs.byIds(ids).associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }

    suspend fun toggleLike(s: Song) {
        upsertMeta(s)
        val wasLiked = s.id in likedIds.value
        songs.setLiked(s.id, !wasLiked, if (wasLiked) 0L else System.currentTimeMillis())
    }

    suspend fun markPlayed(s: Song) {
        upsertMeta(s)
        songs.markPlayed(s.id, System.currentTimeMillis())
    }

    suspend fun cachedSongs(): List<Song> = withContext(Dispatchers.IO) {
        songsByIds(Graph.cachedIds().toList())
    }

    // ---- playlists -------------------------------------------------------------------------

    suspend fun createPlaylist(name: String): Long =
        lists.insert(Playlist(name = name.trim().ifEmpty { "New playlist" }, createdAt = System.currentTimeMillis()))

    suspend fun addToPlaylist(pid: Long, items: List<Song>) {
        var pos = lists.nextPosition(pid)
        val now = System.currentTimeMillis()
        for (s in items) {
            upsertMeta(s)
            songs.addToLibrary(s.id)
            lists.addSong(PlaylistSong(pid, s.id, pos++, now))
        }
    }

    suspend fun removeFromPlaylist(pid: Long, sid: String) = lists.removeSong(pid, sid)

    suspend fun renamePlaylist(id: Long, name: String) = lists.rename(id, name.trim().ifEmpty { "Untitled" })

    suspend fun deletePlaylist(id: Long) {
        lists.clear(id)
        lists.delete(id)
    }

    fun playlistSongs(id: Long): Flow<List<Song>> = lists.songs(id)

    fun playlistName(id: Long): Flow<String?> = lists.nameFlow(id)

    // ---- search history --------------------------------------------------------------------

    suspend fun saveSearch(term: String) = searches.put(SearchEntry(term, System.currentTimeMillis()))

    suspend fun clearSearches() = searches.clear()

    // ---- downloads -------------------------------------------------------------------------

    suspend fun queueDownload(s: Song) {
        upsertMeta(s)
        songs.addToLibrary(s.id)
        dls.upsert(DlEntry(s.id, "", DlState.QUEUED, 0, 0, System.currentTimeMillis()))
    }

    suspend fun setDownload(id: String, state: Int, progress: Int) = dls.setState(id, state, progress)

    suspend fun finishDownload(id: String, path: String, size: Long) = dls.finish(id, path, size)

    suspend fun deleteDownloadRow(id: String) = dls.delete(id)

    // ---- export / import -------------------------------------------------------------------

    suspend fun exportJson(ids: List<Long>?): String {
        val arr = JSONArray()
        for (p in lists.allOnce()) {
            if (ids != null && p.id !in ids) continue
            val sa = JSONArray()
            for (s in lists.songsOnce(p.id)) {
                if (s.id.startsWith("local:")) continue
                sa.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("title", s.title)
                        .put("artist", s.artist)
                        .put("album", s.album ?: JSONObject.NULL)
                        .put("duration", s.durationSec)
                        .put("thumb", s.thumb ?: JSONObject.NULL)
                )
            }
            arr.put(JSONObject().put("name", p.name).put("songs", sa))
            lists.markExported(p.id, System.currentTimeMillis())
        }
        return JSONObject().put("app", "Sepia").put("version", 1).put("playlists", arr).toString(2)
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).ifEmpty { null } else null

    /** Returns how many playlists were imported. */
    suspend fun importJson(text: String): Int {
        val arr = JSONObject(text).optJSONArray("playlists") ?: return 0
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pid = createPlaylist(o.str("name") ?: "Imported")
            val sa = o.optJSONArray("songs") ?: continue
            val items = ArrayList<Song>()
            for (j in 0 until sa.length()) {
                val so = sa.getJSONObject(j)
                val id = so.str("id") ?: continue
                items.add(
                    Song(
                        id = id,
                        title = so.str("title") ?: "Unknown",
                        artist = so.str("artist") ?: "",
                        album = so.str("album"),
                        durationSec = so.optLong("duration"),
                        thumb = so.str("thumb")
                    )
                )
            }
            addToPlaylist(pid, items)
            count++
        }
        return count
    }
}
