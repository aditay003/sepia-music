package app.sepia.music.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import app.sepia.music.data.DlEntry
import app.sepia.music.data.Song

/** Screens that open on top of the three main tabs. */
sealed interface Dest {
    data object Settings : Dest
    data object Exports : Dest

    /**
     * kind: liked | songs | downloaded | cached | local | playlist | album | artist | mix | collection
     * arg: playlist id, album/artist name, search query or collection url (depends on kind)
     */
    data class Songs(val kind: String, val arg: String, val title: String) : Dest
}

class MenuTarget(val song: Song, val playlistId: Long?)

/** Things any screen can ask the root to do. */
interface AppActions {
    fun menu(song: Song, playlistId: Long? = null)
    fun addToPlaylist(songs: List<Song>)
    fun toast(msg: String)
    fun open(d: Dest)
    fun searchFor(q: String)
    fun openPlayer()
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }
val LocalLiked = compositionLocalOf<Set<String>> { emptySet() }
val LocalDownloads = compositionLocalOf<Map<String, DlEntry>> { emptyMap() }
