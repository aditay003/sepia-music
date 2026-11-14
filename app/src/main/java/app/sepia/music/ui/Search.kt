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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sepia.music.Graph
import app.sepia.music.net.Item
import app.sepia.music.net.SearchKind
import app.sepia.music.net.SearchSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import app.sepia.music.data.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
sealed interface SearchUi {
    data object Idle : SearchUi
    data object Loading : SearchUi
    data class Results(
        val items: List<Item>,
        val hasMore: Boolean,
        val loadingMore: Boolean,
        val top: Song? = null,
        val extra: List<Song> = emptyList()
    ) : SearchUi
    data class Failed(val message: String) : SearchUi
}

private fun itemKey(i: Item): String = when (i) {
    is Item.Track -> "t:" + i.song.id
    is Item.Group -> "g:" + i.url
    is Item.Artist -> "a:" + i.name
}

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var kind by mutableStateOf(SearchKind.Songs)
        private set
    var state by mutableStateOf<SearchUi>(SearchUi.Idle)
        private set

    val history = Graph.repo.searchHistory

    private var session: SearchSession? = null
    private var job: Job? = null

    /** True while the person is still typing (then we show live suggestions instead of results). */
    var typing by mutableStateOf(false)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var quickSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    private var suggestJob: Job? = null

    fun onType(text: String) {
        query = text
        suggestJob?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            typing = false
            suggestions = emptyList()
            quickSongs = emptyList()
            return
        }
        typing = true
        suggestJob = viewModelScope.launch {
            delay(280)
            val sug = try {
                Graph.source.suggestions(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (query.trim() == q) suggestions = sug
            val songs = try {
                Graph.source.searchSongs(q).take(5)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (query.trim() == q) quickSongs = songs
        }
    }

    fun submit(term: String = query) {
        val t = term.trim()
        if (t.isEmpty()) return
        query = t
        execute(t)
    }

    fun selectKind(k: SearchKind) {
        if (k == kind) return
        kind = k
        if (query.isNotBlank() && state !is SearchUi.Idle) execute(query.trim())
    }

    private fun execute(t: String) {
        job?.cancel()
        suggestJob?.cancel()
        typing = false
        state = SearchUi.Loading
        job = viewModelScope.launch {
            try {
                Graph.repo.saveSearch(t)
                if (kind == SearchKind.Songs) {
                    // Songs and videos at the same time, so a song that only exists as a video still shows up.
                    val both = coroutineScope {
                        val a = async { Graph.source.search(t, SearchKind.Songs) }
                        val b = async {
                            try {
                                Graph.source.search(t, SearchKind.Videos).second
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                emptyList<Item>()
                            }
                        }
                        Pair(a.await(), b.await())
                    }
                    val (s, items) = both.first
                    session = s
                    val songs = items.mapNotNull { (it as? Item.Track)?.song }
                    val vids = both.second.mapNotNull { (it as? Item.Track)?.song }
                    val top = pickTop(t, songs, vids)
                    val rest = items.filter { !(it is Item.Track && it.song.id == top?.id) }
                    val extra = vids.filter { v -> v.id != top?.id && songs.none { it.id == v.id } }.take(3)
                    state = SearchUi.Results(rest.distinctBy { itemKey(it) }, s.next != null, false, top, extra)
                } else {
                    val (s, items) = Graph.source.search(t, kind)
                    session = s
                    state = SearchUi.Results(items.distinctBy { itemKey(it) }, s.next != null, false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = SearchUi.Failed(friendly(e))
            }
        }
    }

    fun loadMore() {
        val s = session ?: return
        val cur = state as? SearchUi.Results ?: return
        if (cur.loadingMore || !cur.hasMore) return
        state = cur.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val more = Graph.source.more(s)
                val merged = (cur.items + more).distinctBy { itemKey(it) }
                state = cur.copy(items = merged, hasMore = s.next != null, loadingMore = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = cur.copy(loadingMore = false, hasMore = false)
            }
        }
    }

    fun clear() {
        job?.cancel()
        suggestJob?.cancel()
        typing = false
        suggestions = emptyList()
        quickSongs = emptyList()
        query = ""
        state = SearchUi.Idle
        session = null
    }

    fun clearHistory() {
        viewModelScope.launch { Graph.repo.clearSearches() }
    }
}

private val SUGGESTED = listOf(
    "Top hits" to "top hits songs",
    "Lofi beats" to "lofi beats",
    "Love songs" to "love songs",
    "Chill vibes" to "chill vibes songs",
    "Workout mix" to "workout songs",
    "Acoustic covers" to "acoustic songs"
)

@Composable
fun SearchScreen(vm: SearchViewModel, listState: LazyListState) {
    val act = LocalActions.current
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val history by vm.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val ps by Graph.player.ui.collectAsStateWithLifecycle()
    val playingId = ps.song?.id
    val state = vm.state

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 190.dp)
    ) {
        item {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding().padding(start = 24.dp, top = 22.dp, bottom = 14.dp)
            )
        }
        item {
            SearchField(
                value = vm.query,
                onChange = { vm.onType(it) },
                onSearch = {
                    vm.submit()
                    focus.clearFocus()
                },
                onClear = { vm.clear() }
            )
        }
        if (state !is SearchUi.Idle && !vm.typing) {
            item {
                LazyRow(
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SearchKind.entries) { k -> Pill(k.label, k == vm.kind, { vm.selectKind(k) }) }
                }
            }
        }

        if (vm.typing) {
            // Live suggestions while typing, like YT Music: a couple of searches, the best songs, then more searches.
            val sug = vm.suggestions
            val quick = vm.quickSongs
            items(sug.take(2)) { text ->
                LiveSuggestion(
                    text,
                    onPick = {
                        vm.submit(text)
                        focus.clearFocus()
                    },
                    onInsert = { vm.onType(text) }
                )
            }
            items(quick, key = { "q:" + it.id }) { song ->
                SongRow(
                    song = song,
                    playing = song.id == playingId,
                    onClick = {
                        Graph.player.playRadio(song)
                        focus.clearFocus()
                    },
                    onMore = { act.menu(song) },
                    modifier = Modifier
                )
            }
            items(sug.drop(2)) { text ->
                LiveSuggestion(
                    text,
                    onPick = {
                        vm.submit(text)
                        focus.clearFocus()
                    },
                    onInsert = { vm.onType(text) }
                )
            }
            if (sug.isEmpty() && quick.isEmpty()) {
                item {
                    LiveSuggestion(
                        vm.query.trim(),
                        onPick = {
                            vm.submit()
                            focus.clearFocus()
                        },
                        onInsert = {}
                    )
                }
            }
        } else when (state) {
            is SearchUi.Idle -> {
                if (history.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Recent searches",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { vm.clearHistory() }) {
                                    Icon(Icons.Rounded.Delete, "Clear recent searches", tint = cs.onSurfaceVariant)
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(history) { h -> Pill(h, false, { vm.submit(h) }) }
                            }
                        }
                    }
                }
                item { SectionHeader("Suggested") }
                items(SUGGESTED) { s ->
                    SuggestionRow(s.first, s.second) { vm.submit(s.second) }
                }
            }

            is SearchUi.Loading -> items(7) { SkeletonRow() }

            is SearchUi.Failed -> item { ErrorBox(state.message) { vm.submit() } }

            is SearchUi.Results -> {
                if (state.items.isEmpty() && state.top == null) {
                    item { EmptyHint("No results for \u201C${vm.query}\u201D.") }
                }
                state.top?.let { top ->
                    item(key = "top-result") {
                        TopResultCard(
                            song = top,
                            playing = top.id == playingId,
                            onPlay = { Graph.player.playRadio(top) },
                            onSave = { act.addToPlaylist(listOf(top)) }
                        )
                    }
                }
                if (state.extra.isNotEmpty()) {
                    item(key = "more-from-youtube") { SectionHeader("More from YouTube") }
                    items(state.extra, key = { "x:" + it.id }) { v ->
                        SongRow(
                            song = v,
                            playing = v.id == playingId,
                            onClick = { Graph.player.playRadio(v) },
                            onMore = { act.menu(v) },
                            modifier = Modifier
                        )
                    }
                }
                itemsIndexed(state.items, key = { _, it -> itemKey(it) }) { _, entry ->
                    when (entry) {
                        is Item.Track -> SongRow(
                            song = entry.song,
                            playing = entry.song.id == playingId,
                            onClick = {
                                // Play this song, then keep going with similar songs (not the same song again).
                                Graph.player.playRadio(entry.song)
                            },
                            onMore = { act.menu(entry.song) },
                            modifier = Modifier
                        )
                        is Item.Group -> ResultRow(
                            title = entry.title,
                            subtitle = (if (entry.album) "Album" else "Playlist") +
                                (if (entry.subtitle.isNotBlank()) " \u2022 ${entry.subtitle}" else ""),
                            thumb = entry.thumb,
                            round = false
                        ) { act.open(Dest.Songs("collection", entry.url, entry.title)) }
                        is Item.Artist -> ResultRow(
                            title = entry.name,
                            subtitle = "Artist",
                            thumb = entry.thumb,
                            round = true
                        ) {
                            vm.selectKind(SearchKind.Songs)
                            vm.submit(entry.name)
                        }
                    }
                }
                if (state.hasMore) {
                    item {
                        LaunchedEffect(state.items.size) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = cs.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "Search songs, artists, albums\u2026",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, "Clear", tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, query: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFCDA98C), Color(0xFF7A5540)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.9f))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Playlist", style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
fun ResultRow(title: String, subtitle: String, thumb: String?, round: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(thumb, Modifier.size(52.dp), radius = if (round) 26.dp else 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

/** One line of "what people search for", with an arrow that copies it into the search box. */
@Composable
private fun LiveSuggestion(text: String, onPick: () -> Unit, onInsert: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onPick)
            .padding(start = 24.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onInsert) {
            Icon(Icons.Rounded.NorthWest, "Use this text", tint = cs.onSurfaceVariant)
        }
    }
}

/** The best match for what was typed, shown first with Play / Save buttons (like YT Music's "top result"). */
@Composable
private fun TopResultCard(song: Song, playing: Boolean, onPlay: () -> Unit, onSave: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainerHigh)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(song.thumb, Modifier.size(72.dp), radius = 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Top result", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                Text(
                    song.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Song \u2022 " + song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(if (playing) "Playing" else "Play", Icons.Rounded.PlayArrow, true, onPlay)
            ActionButton("Save", Icons.Rounded.Add, false, onSave)
        }
    }
}

private fun normText(s: String): String =
    s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun matchScore(q: String, s: Song): Int {
    val nq = normText(q)
    if (nq.isEmpty()) return 0
    val nt = normText(s.title)
    val hay = normText(s.title + " " + s.artist)
    return when {
        nt == nq -> 100
        nt.startsWith(nq) || hay.startsWith(nq) -> 70
        nq.split(' ').all { it in hay } -> 50
        else -> 0
    }
}

/** The best match from songs first, then videos (so a song that only exists as a video still shows up). */
private fun pickTop(q: String, songs: List<Song>, videos: List<Song>): Song? {
    val all = songs.take(8) + videos.take(8)
    val best = all.maxByOrNull { matchScore(q, it) } ?: return null
    return if (matchScore(q, best) >= 50) best else null
}
