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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sepia.music.Graph
import app.sepia.music.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime

class Mix(val title: String, val query: String, val thumb: String?, val songs: List<Song>)

private val MIXES: Map<String, List<Pair<String, String>>> = mapOf(
    "Romance" to listOf(
        "Love songs" to "love songs",
        "Soft hearts" to "romantic songs",
        "Slow & sweet" to "slow romantic songs"
    ),
    "Chill" to listOf(
        "Chill mix" to "chill songs",
        "Lofi beats" to "lofi hip hop",
        "Evening unwind" to "relaxing acoustic songs"
    ),
    "Focus" to listOf(
        "Deep focus" to "focus music",
        "Study beats" to "study lofi",
        "Instrumental" to "instrumental music"
    ),
    "Workout" to listOf(
        "Workout mix" to "workout songs",
        "Gym energy" to "gym motivation songs",
        "Run faster" to "running songs"
    )
)

class HomeViewModel : ViewModel() {
    val moods = listOf("Romance", "Chill", "Focus", "Workout")

    var mood by mutableStateOf("Romance")
        private set
    var mixes by mutableStateOf<List<Mix>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val cache = HashMap<String, List<Mix>>()
    private var job: Job? = null

    init {
        load()
    }

    fun pick(m: String) {
        if (m == mood) return
        mood = m
        load()
    }

    fun load() {
        job?.cancel()
        val m = mood
        val cached = cache[m]
        if (cached != null) {
            mixes = cached
            error = null
            loading = false
            return
        }
        loading = true
        error = null
        job = viewModelScope.launch {
            val defs = MIXES[m].orEmpty()
            val results = coroutineScope {
                defs.map { d ->
                    async { runCatching { Graph.source.searchSongs(d.second) }.getOrNull() }
                }.awaitAll()
            }
            val list = ArrayList<Mix>()
            for (i in defs.indices) {
                val songs = results[i]
                if (!songs.isNullOrEmpty()) {
                    list.add(Mix(defs[i].first, defs[i].second, songs.first().thumb, songs))
                }
            }
            if (m == mood) {
                if (list.isEmpty()) {
                    mixes = emptyList()
                    error = "Couldn't load mixes. Check your connection and try again."
                } else {
                    cache[m] = list
                    mixes = list
                }
                loading = false
            }
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Good night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        h < 22 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun HomeScreen(listState: LazyListState) {
    val vm: HomeViewModel = viewModel()
    val act = LocalActions.current
    val recent by Graph.repo.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val ps by Graph.player.ui.collectAsStateWithLifecycle()
    val playingId = ps.song?.id

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 190.dp)
    ) {
        item { HomeHeader(ps.playing) { act.open(Dest.Settings) } }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.moods) { m -> Pill(m, m == vm.mood, { vm.pick(m) }) }
            }
        }
        item { SectionHeader("Recently played") }
        item {
            if (recent.isEmpty()) {
                EmptyHint("Songs you play will show up here.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(recent, key = { _, s -> s.id }) { i, s ->
                        CoverCard(s.thumb, s.title, s.artist, playing = s.id == playingId) {
                            Graph.player.playQueue(recent, i)
                        }
                    }
                }
            }
        }
        item { SectionHeader("Made for you") }
        item { MixesRow(vm) }
    }
}

@Composable
private fun HomeHeader(playing: Boolean, onAvatar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                greeting(),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(38.dp)
                    .bounceClick(onClick = onAvatar)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFD9B99B), Color(0xFF8C634C)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Person, "Settings",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Let the music\nfeel you", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.width(12.dp))
            Equalizer(
                cs.primary.copy(alpha = 0.55f),
                Modifier.padding(bottom = 8.dp).size(24.dp),
                active = playing
            )
        }
    }
}

@Composable
private fun MixesRow(vm: HomeViewModel) {
    val act = LocalActions.current
    val err = vm.error
    when {
        vm.loading && vm.mixes.isEmpty() -> LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) { items(3) { ShimmerCard() } }

        err != null && vm.mixes.isEmpty() -> ErrorBox(err) { vm.load() }

        else -> LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(vm.mixes, key = { it.query }) { mix ->
                CoverCard(mix.thumb, mix.title, "Playlist") {
                    act.open(Dest.Songs("mix", mix.query, mix.title))
                }
            }
        }
    }
}
