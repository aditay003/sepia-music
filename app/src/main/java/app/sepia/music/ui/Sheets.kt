package app.sepia.music.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sepia.music.Graph
import app.sepia.music.data.DlState
import app.sepia.music.data.Song
import app.sepia.music.data.isLocal
import app.sepia.music.player.PlayerUi
import app.sepia.music.work.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The "⋮" menu for a song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(target: MenuTarget, onDismiss: () -> Unit) {
    val song = target.song
    val act = LocalActions.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val liked = song.id in LocalLiked.current
    val dl = LocalDownloads.current[song.id]
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close(after: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            after()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(song.thumb, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }
            MenuRow(Icons.Rounded.SkipNext, "Play next") {
                close {
                    Graph.player.playNext(song)
                    act.toast("Will play next")
                }
            }
            MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to queue") {
                close {
                    Graph.player.addToQueue(song)
                    act.toast("Added to queue")
                }
            }
            MenuRow(Icons.Rounded.Add, "Add to playlist") { close { act.addToPlaylist(listOf(song)) } }
            MenuRow(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (liked) "Remove from liked" else "Like"
            ) {
                close { Graph.scope.launch(Dispatchers.IO) { Graph.repo.toggleLike(song) } }
            }
            if (!song.isLocal) {
                val busy = dl != null && (dl.state == DlState.RUNNING || dl.state == DlState.QUEUED)
                val done = dl != null && dl.state == DlState.DONE
                MenuRow(
                    if (done || busy) Icons.Rounded.Delete else Icons.Rounded.Download,
                    if (done) "Remove download" else if (busy) "Cancel download" else "Download"
                ) {
                    close {
                        if (done || busy) {
                            Downloads.remove(song.id)
                        } else {
                            Downloads.enqueue(listOf(song))
                            act.toast("Downloading\u2026")
                        }
                    }
                }
            }
            if (song.artist.isNotBlank()) {
                MenuRow(Icons.Rounded.Person, "More from ${song.artist}") {
                    close { act.searchFor(song.artist) }
                }
            }
            val playlistId = target.playlistId
            if (playlistId != null) {
                MenuRow(Icons.Rounded.Close, "Remove from this playlist") {
                    close {
                        Graph.scope.launch(Dispatchers.IO) { Graph.repo.removeFromPlaylist(playlistId, song.id) }
                    }
                }
            }
            if (!song.isLocal) {
                MenuRow(Icons.Rounded.Share, "Share") {
                    close {
                        val send = Intent(Intent.ACTION_SEND)
                        send.type = "text/plain"
                        send.putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                        val chooser = Intent.createChooser(send, "Share song")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(chooser)
                    }
                }
            }
        }
    }
}

/** Pick a playlist (or create one) for the given songs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(songs: List<Song>, onDismiss: () -> Unit) {
    val act = LocalActions.current
    val cs = MaterialTheme.colorScheme
    val lists by Graph.repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    fun close(after: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            after()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            MenuRow(Icons.Rounded.Add, "New playlist") { creating = true }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(lists, key = { it.playlist.id }) { meta ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                close {
                                    Graph.scope.launch(Dispatchers.IO) {
                                        Graph.repo.addToPlaylist(meta.playlist.id, songs)
                                    }
                                    act.toast("Added to ${meta.playlist.name}")
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(meta.cover, Modifier.size(44.dp), radius = 12.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                meta.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${meta.songCount} songs",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false }
        ) { name ->
            creating = false
            Graph.scope.launch(Dispatchers.IO) {
                val pid = Graph.repo.createPlaylist(name)
                Graph.repo.addToPlaylist(pid, songs)
            }
            act.toast("Added to $name")
            close {}
        }
    }
}

/** What is playing now and what is next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(ui: PlayerUi, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surfaceContainerLow) {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (ui.queue.isEmpty()) {
            EmptyHint("The queue is empty.")
        }
        LazyColumn(
            Modifier.fillMaxWidth(),
            state = rememberLazyListState(initialFirstVisibleItemIndex = (ui.index - 1).coerceAtLeast(0)),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            itemsIndexed(ui.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Graph.player.jumpTo(i) }
                        .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(s.thumb, Modifier.size(44.dp), radius = 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (i == ui.index) cs.primary else cs.onSurface
                        )
                        Text(
                            s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { Graph.player.removeAt(i) }) {
                        Icon(Icons.Rounded.Close, "Remove from queue", tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
