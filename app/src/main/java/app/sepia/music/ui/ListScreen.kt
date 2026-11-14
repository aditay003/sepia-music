package app.sepia.music.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sepia.music.Graph
import app.sepia.music.data.Song
import app.sepia.music.work.Downloads
import app.sepia.music.work.LocalMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun flowFor(d: Dest.Songs): Flow<List<Song>>? = when (d.kind) {
    "liked" -> Graph.repo.liked
    "songs" -> Graph.repo.library
    "downloaded" -> Graph.repo.downloaded
    "playlist" -> Graph.repo.playlistSongs(d.arg.toLong())
    "album" -> Graph.repo.library.map { l -> l.filter { it.album == d.arg } }
    "artist" -> Graph.repo.library.map { l -> l.filter { it.artist == d.arg } }
    else -> null
}

private fun emptyText(kind: String): String = when (kind) {
    "liked" -> "Tap the heart on a song and it will be saved here."
    "downloaded" -> "Songs you download will be here, ready to play offline."
    "cached" -> "Songs you stream are kept here for a while so they replay instantly."
    "local" -> "No music files found on this device."
    "playlist" -> "This playlist is empty. Add songs from the \u22EE menu."
    else -> "Nothing here yet."
}

@Composable
fun SongListHost(d: Dest.Songs, onBack: () -> Unit) {
    val act = LocalActions.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val ps by Graph.player.ui.collectAsStateWithLifecycle()

    var songs by remember(d) { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember(d) { mutableStateOf(true) }
    var error by remember(d) { mutableStateOf<String?>(null) }
    var subtitle by remember(d) { mutableStateOf<String?>(null) }
    var reload by remember(d) { mutableStateOf(0) }
    var needPermission by remember(d) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            needPermission = false
            reload += 1
        }
    }

    LaunchedEffect(d, reload) {
        loading = true
        error = null
        try {
            when (d.kind) {
                "mix" -> songs = Graph.source.searchSongs(d.arg)
                "collection", "albumweb" -> {
                    val c = Graph.source.collection(d.arg)
                    subtitle = c.subtitle
                    songs = if (d.kind == "albumweb") c.songs.map { it.copy(album = d.title) } else c.songs
                }
                "cached" -> songs = Graph.repo.cachedSongs()
                "local" -> {
                    if (LocalMusic.granted(ctx)) songs = LocalMusic.scan(ctx) else needPermission = true
                }
                else -> flowFor(d)?.collect {
                    songs = it
                    loading = false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        }
        loading = false
    }

    val isPlaylist = d.kind == "playlist"
    val liveName = if (isPlaylist) remember(d) { Graph.repo.playlistName(d.arg.toLong()) } else null
    val dbName = liveName?.collectAsStateWithLifecycle(initialValue = d.title)?.value
    val title = dbName ?: d.title

    val canDownload = d.kind in setOf("liked", "songs", "mix", "collection", "albumweb", "playlist", "album", "artist")
    val canSave = d.kind in setOf("mix", "collection", "albumweb")
    val hasMenu = canDownload || canSave || isPlaylist

    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack) {
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (canDownload && songs.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Download all") }, onClick = {
                                menuOpen = false
                                Downloads.enqueue(songs)
                                act.toast("Downloading ${songs.size} songs")
                            })
                        }
                        if (canSave && songs.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Save as playlist") }, onClick = {
                                menuOpen = false
                                val items = songs
                                Graph.scope.launch(Dispatchers.IO) {
                                    val pid = Graph.repo.createPlaylist(title)
                                    Graph.repo.addToPlaylist(pid, items)
                                }
                                act.toast("Saved to your playlists")
                            })
                        }
                        if (isPlaylist) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                menuOpen = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = {
                                menuOpen = false
                                deleting = true
                            })
                        }
                    }
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 190.dp)) {
            if (songs.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        val sub = subtitle
                        Text(
                            if (!sub.isNullOrBlank()) "$sub \u2022 ${songs.size} songs" else "${songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton("Play", Icons.Rounded.PlayArrow, true) {
                                Graph.player.playQueue(songs, 0)
                            }
                            ActionButton("Shuffle", Icons.Rounded.Shuffle, false) {
                                Graph.player.playQueue(songs.shuffled(), 0)
                            }
                        }
                    }
                }
            }
            when {
                loading && songs.isEmpty() -> items(7) { SkeletonRow() }
                error != null && songs.isEmpty() -> item { ErrorBox(error ?: "") { reload += 1 } }
                needPermission -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Allow Sepia to see the music on your phone.",
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(14.dp))
                        ActionButton("Allow access", Icons.Rounded.PlayArrow, true) {
                            permissionLauncher.launch(LocalMusic.permission())
                        }
                    }
                }
                songs.isEmpty() -> item { EmptyHint(emptyText(d.kind)) }
                else -> itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        playing = s.id == ps.song?.id,
                        onClick = { Graph.player.playQueue(songs, i) },
                        onMore = { act.menu(s, if (isPlaylist) d.arg.toLongOrNull() else null) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Rename playlist",
            initial = title,
            confirm = "Save",
            onDismiss = { renaming = false }
        ) { name ->
            renaming = false
            val id = d.arg.toLongOrNull()
            if (id != null) Graph.scope.launch(Dispatchers.IO) { Graph.repo.renamePlaylist(id, name) }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = cs.surfaceContainerHigh,
            title = { Text("Delete playlist?") },
            text = { Text("\u201C$title\u201D will be removed. Your songs stay in the library.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    val id = d.arg.toLongOrNull()
                    if (id != null) Graph.scope.launch(Dispatchers.IO) { Graph.repo.deletePlaylist(id) }
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Exported playlists (backup / restore as a .json file)
// ---------------------------------------------------------------------------------------------

@Composable
fun ExportsScreen(onBack: () -> Unit) {
    val act = LocalActions.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val lists by Graph.repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    var pending by remember { mutableStateOf<List<Long>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ids = pending
            Graph.scope.launch(Dispatchers.IO) {
                val ok = try {
                    val json = Graph.repo.exportJson(ids)
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    true
                } catch (e: Exception) {
                    false
                }
                withContext(Dispatchers.Main) { act.toast(if (ok) "Playlists exported" else "Export failed") }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Graph.scope.launch(Dispatchers.IO) {
                val msg = try {
                    val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val n = Graph.repo.importJson(text)
                    if (n == 1) "Imported 1 playlist" else "Imported $n playlists"
                } catch (e: Exception) {
                    "That file couldn't be imported"
                }
                withContext(Dispatchers.Main) { act.toast(msg) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Exported", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 190.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        "Save your playlists to a file to back them up or move them to another phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("Export all", Icons.Rounded.PlayArrow, true) {
                            pending = null
                            exportLauncher.launch("sepia-playlists.json")
                        }
                        ActionButton("Import", Icons.Rounded.Shuffle, false) {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    }
                }
            }
            item { SectionHeader("Your playlists") }
            if (lists.isEmpty()) item { EmptyHint("No playlists yet.") }
            items(lists, key = { it.playlist.id }) { meta ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bounceClick {
                            pending = listOf(meta.playlist.id)
                            exportLauncher.launch("${meta.playlist.name}.json")
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(meta.cover, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meta.playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            if (meta.playlist.exportedAt > 0) "${meta.songCount} songs \u2022 exported" else "${meta.songCount} songs \u2022 tap to export",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
