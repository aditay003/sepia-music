package app.sepia.music.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sepia.music.Graph
import app.sepia.music.data.PlaylistMeta
import app.sepia.music.work.LocalMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(listState: LazyListState) {
    val act = LocalActions.current
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val liked by Graph.repo.likedCount.collectAsStateWithLifecycle(initialValue = 0)
    val downloaded by Graph.repo.downloadedCount.collectAsStateWithLifecycle(initialValue = 0)
    val exported by Graph.repo.exportedCount.collectAsStateWithLifecycle(initialValue = 0)
    val lists by Graph.repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by Graph.repo.library.collectAsStateWithLifecycle(initialValue = emptyList())
    val ps by Graph.player.ui.collectAsStateWithLifecycle()
    val playingId = ps.song?.id

    var cached by remember { mutableStateOf(0) }
    var local by remember { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistMeta?>(null) }
    var deleting by remember { mutableStateOf<PlaylistMeta?>(null) }

    LaunchedEffect(Unit) {
        cached = withContext(Dispatchers.IO) { Graph.cachedIds().size }
        if (LocalMusic.granted(ctx)) local = LocalMusic.scan(ctx).size
    }

    val albums = remember(songs) {
        songs.filter { !it.album.isNullOrBlank() }
            .groupBy { it.album ?: "" }
            .toList()
            .sortedBy { it.first.lowercase() }
    }
    val artists = remember(songs) {
        songs.filter { it.artist.isNotBlank() }
            .groupBy { it.artist }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 190.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 12.dp, top = 22.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { act.searchFor("") }) { Icon(Icons.Rounded.Search, "Search") }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(listOf("Playlists", "Songs", "Albums", "Artists").withIndex().toList()) { entry ->
                    Pill(entry.value, entry.index == tab, { tab = entry.index })
                }
            }
        }

        when (tab) {
            0 -> {
                item { Spacer(Modifier.height(14.dp)) }
                item {
                    LibraryTile(Icons.Rounded.Favorite, "Liked", "$liked songs") {
                        act.open(Dest.Songs("liked", "", "Liked"))
                    }
                }
                item {
                    LibraryTile(Icons.Rounded.Download, "Downloaded", "$downloaded songs") {
                        act.open(Dest.Songs("downloaded", "", "Downloaded"))
                    }
                }
                item {
                    LibraryTile(Icons.Rounded.TrendingUp, "Exported", "$exported playlists") {
                        act.open(Dest.Exports)
                    }
                }
                item {
                    LibraryTile(Icons.Rounded.Cached, "Cached", "$cached songs") {
                        act.open(Dest.Songs("cached", "", "Cached"))
                    }
                }
                item {
                    LibraryTile(
                        Icons.Rounded.Folder, "Local", local?.let { "$it songs" } ?: "On this device"
                    ) { act.open(Dest.Songs("local", "", "Local")) }
                }
                item {
                    SectionHeader("Playlists") {
                        IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "New playlist") }
                    }
                }
                if (lists.isEmpty()) {
                    item { EmptyHint("Create a playlist with the + button.") }
                }
                items(lists, key = { it.playlist.id }) { meta ->
                    PlaylistRow(
                        meta = meta,
                        onOpen = {
                            act.open(Dest.Songs("playlist", meta.playlist.id.toString(), meta.playlist.name))
                        },
                        onRename = { renaming = meta },
                        onDelete = { deleting = meta },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            1 -> {
                if (songs.isEmpty()) {
                    item { EmptyHint("Songs you like, download or add to playlists will appear here.") }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        playing = s.id == playingId,
                        onClick = { Graph.player.playQueue(songs, i) },
                        onMore = { act.menu(s) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            2 -> {
                if (albums.isEmpty()) {
                    item { EmptyHint("Albums show up when you save songs from an album page.") }
                }
                items(albums, key = { it.first }) { group ->
                    ResultRow(group.first, "${group.second.size} songs", group.second.first().thumb, false) {
                        act.open(Dest.Songs("album", group.first, group.first))
                    }
                }
            }

            else -> {
                if (artists.isEmpty()) {
                    item { EmptyHint("Artists show up when you save songs.") }
                }
                items(artists, key = { it.first }) { group ->
                    ResultRow(group.first, "${group.second.size} songs", group.second.first().thumb, true) {
                        act.open(Dest.Songs("artist", group.first, group.first))
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
            Graph.scope.launch(Dispatchers.IO) { Graph.repo.createPlaylist(name) }
        }
    }
    renaming?.let { meta ->
        NameDialog(
            title = "Rename playlist",
            initial = meta.playlist.name,
            confirm = "Save",
            onDismiss = { renaming = null }
        ) { name ->
            renaming = null
            Graph.scope.launch(Dispatchers.IO) { Graph.repo.renamePlaylist(meta.playlist.id, name) }
        }
    }
    deleting?.let { meta ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete playlist?") },
            text = { Text("\u201C${meta.playlist.name}\u201D will be removed. Your songs stay in the library.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    Graph.scope.launch(Dispatchers.IO) { Graph.repo.deletePlaylist(meta.playlist.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LibraryTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = cs.onSurface, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistRow(
    meta: PlaylistMeta,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .bounceClick(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(meta.cover, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                meta.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${meta.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "More", tint = cs.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                    menu = false
                    onRename()
                })
                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}
