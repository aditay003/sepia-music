package app.sepia.music.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sepia.music.Graph
import app.sepia.music.data.Song
import app.sepia.music.net.SearchKind

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
@Composable
fun SepiaRoot() {
    val ctx = LocalContext.current
    val ui by Graph.player.ui.collectAsStateWithLifecycle()
    val liked by Graph.repo.likedIds.collectAsStateWithLifecycle()
    val downloads by Graph.repo.downloads.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Dest>() }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    var pickSongs by remember { mutableStateOf<List<Song>?>(null) }

    val searchVm: SearchViewModel = viewModel()
    val homeState = rememberLazyListState()
    val libraryState = rememberLazyListState()
    val searchState = rememberLazyListState()

    val actions = remember {
        object : AppActions {
            override fun menu(song: Song, playlistId: Long?) {
                menuTarget = MenuTarget(song, playlistId)
            }

            override fun addToPlaylist(songs: List<Song>) {
                pickSongs = songs
            }

            override fun toast(msg: String) {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }

            override fun open(d: Dest) {
                stack.add(d)
            }

            override fun searchFor(q: String) {
                stack.clear()
                expanded = false
                tab = 2
                if (q.isNotBlank()) {
                    searchVm.selectKind(SearchKind.Songs)
                    searchVm.submit(q)
                }
            }

            override fun openPlayer() {
                expanded = true
            }
        }
    }

    LaunchedEffect(Unit) {
        Graph.player.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalLiked provides liked,
        LocalDownloads provides downloads
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            // The three main tabs stay alive in the background and only the visible one is shown,
            // so switching tabs is instant and smooth instead of rebuilding a whole screen.
            KeepAlive(tab == 0) { HomeScreen(homeState) }
            KeepAlive(tab == 1) { LibraryScreen(libraryState) }
            KeepAlive(tab == 2) { SearchScreen(searchVm, searchState) }

            // Screens that open on top (playlists, settings, ...)
            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = {
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } +
                        fadeIn(tween(220))) togetherWith fadeOut(tween(160))
                },
                label = "detail"
            ) { d ->
                if (d != null) {
                    DetailHost(d) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                }
            }

            // A soft fade under the floating bars, so scrolling content never shows through them
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (ui.song != null) 230.dp else 140.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            )

            // Floating mini player + navigation
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                AnimatedVisibility(
                    visible = ui.song != null,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 2 }
                ) {
                    Column {
                        MiniPlayer(ui, onOpen = { expanded = true })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                NavBar(selected = tab, onSelect = {
                    tab = it
                    stack.clear()
                })
            }

            // Full-screen player
            AnimatedVisibility(
                visible = expanded && ui.song != null,
                enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                PlayerScreen(onCollapse = { expanded = false })
            }

            menuTarget?.let { t -> SongMenuSheet(t) { menuTarget = null } }
            pickSongs?.let { s -> AddToPlaylistSheet(s) { pickSongs = null } }
        }
    }

    BackHandler(enabled = expanded) { expanded = false }
    BackHandler(enabled = !expanded && stack.isNotEmpty()) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
}

/** Keeps a tab's screen alive; only the visible one is placed, and it fades in and out cheaply. */
@Composable
private fun KeepAlive(visible: Boolean, content: @Composable () -> Unit) {
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(180), label = "tabFade")
    val shown = visible || fade > 0.01f
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = fade }
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) { if (shown) p.place(0, 0) }
            }
    ) { content() }
}

@Composable
private fun DetailHost(d: Dest, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).blockTouches()) {
        when (d) {
            is Dest.Settings -> SettingsScreen(onBack)
            is Dest.Exports -> ExportsScreen(onBack)
            is Dest.Songs -> SongListHost(d, onBack)
        }
    }
}
