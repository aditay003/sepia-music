package app.sepia.music.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.sepia.music.Graph
import app.sepia.music.data.Song
import app.sepia.music.player.PlayerUi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
/** Colours and shapes that make each player style look different. */
private class Look(
    val dark: Boolean,
    val fg: Color,
    val sub: Color,
    val accent: Color,
    val onAccent: Color,
    val radius: Dp,
    val centered: Boolean
)

private fun plLook(style: String, appDark: Boolean, cs: ColorScheme): Look = when (style) {
    "apple" -> Look(true, Color.White, Color.White.copy(alpha = 0.62f), Color.White, Color(0xFF1C1C1E), 14.dp, false)
    "wavy" -> Look(true, Color(0xFFF3E6DA), Color(0xFFD2BBA9), Color(0xFFE9D8C8), Color(0xFF2B1A12), 26.dp, true)
    "ytm" -> Look(true, Color.White, Color(0xFFAAAAAA), Color.White, Color.Black, 10.dp, true)
    "spotify" -> Look(true, Color.White, Color(0xFFB3B3B3), Color.White, Color.Black, 8.dp, false)
    else -> {
        val fg = if (appDark) Color(0xFFF3E6DA) else Color(0xFF2B1A12)
        Look(appDark, fg, fg.copy(alpha = 0.65f), cs.primary, cs.onPrimary, 28.dp, false)
    }
}

@Composable
fun PlayerScreen(onCollapse: () -> Unit) {
    val ui by Graph.player.ui.collectAsStateWithLifecycle()
    val song = ui.song
    val cs = MaterialTheme.colorScheme
    val appDark = AppTheme.isDark()
    val style = AppTheme.playerStyle
    val look = plLook(style, appDark, cs)
    val act = LocalActions.current
    val likedIds = LocalLiked.current
    val scope = rememberCoroutineScope()
    val collapse by rememberUpdatedState(onCollapse)
    var showQueue by remember { mutableStateOf(false) }
    val offsetY = remember { Animatable(0f) }
    val art by rememberArtColor(song?.thumb)
    val seed = art ?: cs.primary
    val liked = song != null && song.id in likedIds
    val toggleLike: () -> Unit = {
        if (song != null) Graph.scope.launch(Dispatchers.IO) { Graph.repo.toggleLike(song) }
    }
    val openMenu: () -> Unit = { if (song != null) act.menu(song) }

    // Light status-bar icons on the dark player looks, put back when the player closes.
    val view = LocalView.current
    DisposableEffect(look.dark) {
        val window = (view.context as? Activity)?.window
        val ctrl = if (window != null) WindowCompat.getInsetsController(window, view) else null
        val before = ctrl?.isAppearanceLightStatusBars
        ctrl?.isAppearanceLightStatusBars = !look.dark
        onDispose {
            if (ctrl != null && before != null) ctrl.isAppearanceLightStatusBars = before
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > size.height * 0.16f) {
                            collapse()
                        } else {
                            scope.launch { offsetY.animateTo(0f, spring(stiffness = 400f)) }
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offsetY.snapTo((offsetY.value + amount).coerceAtLeast(0f)) }
                    }
                )
            }
    ) {
        PlBackdrop(style, look, song?.thumb, seed)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlTopBar(style, look, onCollapse, openMenu)

            // The artwork always keeps the same gap from the sides, whatever the song or play state.
            PlArtwork(
                song?.thumb, look, style,
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)
            ) { dir -> if (dir > 0) Graph.player.next() else Graph.player.skipToPrevious() }

            PlTitle(style, look, song, liked, toggleLike, openMenu)
            Spacer(Modifier.height(6.dp))
            PlSeek(look, style, ui.playing)

            if (style == "spotify" || style == "ytm") {
                PlControlsFive(ui, look, style)
            } else {
                PlControlsThree(ui, look, style)
                PlVolume(look, style == "apple")
            }

            PlBottomRow(style, look, ui, liked, toggleLike, openMenu) { showQueue = true }
        }
    }

    if (showQueue) QueueSheet(ui) { showQueue = false }
}

// ---------------------------------------------------------------------------------------------
// Background: blurry artwork + gradient (Spotify / YT Music use plain gradients)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlBackdrop(style: String, look: Look, thumb: String?, seed: Color) {
    val tint by animateColorAsState(seed, tween(700), label = "seed")
    Box(Modifier.fillMaxSize()) {
        when (style) {
            "spotify" -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(lerp(tint, Color.Black, 0.35f), Color(0xFF121212)))
                )
            )
            "ytm" -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(lerp(tint, Color.Black, 0.6f), Color(0xFF0B0B0B), Color(0xFF030303))
                    )
                )
            )
            else -> {
                // A plain colour first, so nothing flashes while the picture loads.
                val base = if (look.dark) lerp(tint, Color.Black, 0.5f) else lerp(tint, Color.White, 0.45f)
                Box(Modifier.fillMaxSize().background(base))
                if (!thumb.isNullOrEmpty()) {
                    val ctx = LocalContext.current
                    val request = remember(thumb) {
                        ImageRequest.Builder(ctx).data(thumb).size(200).crossfade(500).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(64.dp)
                    )
                }
                val scrimTop = if (look.dark) Color(0x59000000) else Color(0x59FFFFFF)
                val scrimBottom = if (look.dark) Color(0xB3000000) else Color(0xCCF5EADF)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scrimTop, scrimBottom))))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlTopBar(style: String, look: Look, onCollapse: () -> Unit, onMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        if (style != "apple") {
            IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = look.fg, modifier = Modifier.size(30.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .bounceClick(onClick = onCollapse)
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(look.fg.copy(alpha = 0.3f))
            )
        }
        if (style != "apple") {
            IconButton(onClick = onMore, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.MoreVert, "More", tint = look.fg)
            }
        }
    }
}

@Composable
private fun PlArtwork(thumb: String?, look: Look, style: String, modifier: Modifier, onSwipe: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val swipe by rememberUpdatedState(onSwipe)
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            // Swipe the artwork left for the next song, right for the previous one.
            detectHorizontalDragGestures(
                onDragEnd = {
                    val w = size.width.toFloat()
                    val x = dragX.value
                    if (abs(x) > w * 0.2f) swipe(if (x < 0) 1 else -1)
                    scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
                },
                onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, maxHeight)
        AnimatedContent(
            targetState = thumb,
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    translationX = dragX.value
                    rotationZ = dragX.value / 40f
                    alpha = (1f - abs(dragX.value) / 700f).coerceIn(0.4f, 1f)
                },
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
            contentAlignment = Alignment.Center,
            label = "artSwap"
        ) { t ->
            val box = Modifier.size(side)
            Cover(
                t,
                if (style == "spotify") {
                    box
                } else {
                    box.shadow(
                        22.dp, RoundedCornerShape(look.radius),
                        ambientColor = Color(0x44000000), spotColor = Color(0x44000000)
                    )
                },
                radius = look.radius
            )
        }
    }
}

@Composable
private fun PlRoundButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PlTitle(
    style: String,
    look: Look,
    song: Song?,
    liked: Boolean,
    onLike: () -> Unit,
    onMore: () -> Unit
) {
    val title = song?.title ?: "Nothing playing"
    val artist = song?.artist ?: ""
    if (look.centered) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "title"
            ) { t ->
                Text(
                    t, color = look.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
            Text(
                artist, color = look.sub, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "title"
                ) { t ->
                    Text(
                        t, color = look.fg,
                        fontSize = if (style == "default") 26.sp else 22.sp,
                        fontWeight = if (style == "default") FontWeight.SemiBold else FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    artist, color = look.sub, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (style == "apple") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlRoundButton(
                        if (liked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (liked) "Unlike" else "Like", look.fg, onLike
                    )
                    PlRoundButton(Icons.Rounded.MoreHoriz, "More", look.fg, onMore)
                }
            } else {
                IconButton(onClick = onLike) {
                    Crossfade(targetState = liked, animationSpec = tween(200), label = "like") { l ->
                        Icon(
                            if (l) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (l) "Unlike" else "Like",
                            tint = if (l) {
                                if (style == "spotify") Color(0xFF1DB954) else Color(0xFFC2523A)
                            } else {
                                look.fg
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Progress bar + times. Only this small piece redraws every 200 ms while music plays. */
@Composable
private fun PlSeek(look: Look, style: String, playing: Boolean) {
    val pb by rememberPlayback()
    val pos = pb.pos
    val dur = pb.dur
    val progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    var scrub by remember { mutableStateOf<Float?>(null) }
    val fg = look.fg
    val barHeight = when (style) {
        "apple" -> 6.dp
        "ytm" -> 3.dp
        else -> 4.dp
    }
    Column(Modifier.fillMaxWidth()) {
        SeekBar(
            progress = progress,
            onSeek = { scrub = it },
            onSeekEnd = { f ->
                scrub = null
                if (dur > 0) Graph.player.seekTo((f * dur).toLong())
            },
            trackColor = fg.copy(alpha = if (style == "default") 0.18f else 0.28f),
            fillColor = if (style == "apple") fg.copy(alpha = 0.9f) else fg,
            trackHeight = barHeight,
            thumbAlways = style != "apple",
            wavy = style == "wavy",
            wavePlaying = playing
        )
        val elapsed = ((scrub ?: progress) * dur).toLong()
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(fmt(elapsed), style = MaterialTheme.typography.labelMedium, color = look.sub)
            Spacer(Modifier.weight(1f))
            Text(
                if (style == "apple") "-" + fmt((dur - elapsed).coerceAtLeast(0L)) else fmt(dur),
                style = MaterialTheme.typography.labelMedium, color = look.sub
            )
        }
    }
}

@Composable
private fun PlPlayButton(ui: PlayerUi, size: Dp, filled: Boolean, fill: Color, glyph: Color) {
    Box(
        Modifier
            .size(size)
            .bounceClick { Graph.player.toggle() }
            .clip(CircleShape)
            .background(if (filled) fill else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (ui.buffering) {
            CircularProgressIndicator(modifier = Modifier.size(size * 0.45f), strokeWidth = 3.dp, color = glyph)
        } else {
            Crossfade(targetState = ui.playing, animationSpec = tween(180), label = "bigPlay") { p ->
                Icon(
                    if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (p) "Pause" else "Play",
                    tint = glyph,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

@Composable
private fun PlControlsThree(ui: PlayerUi, look: Look, style: String) {
    val apple = style == "apple"
    val glyph = if (apple) 44.dp else 40.dp
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { Graph.player.previous() }, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Rounded.FastRewind, "Previous", tint = look.fg, modifier = Modifier.size(glyph))
        }
        PlPlayButton(
            ui, if (apple) 84.dp else 76.dp, !apple,
            look.accent, if (apple) look.fg else look.onAccent
        )
        IconButton(
            onClick = { Graph.player.next() },
            enabled = ui.hasNext,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                Icons.Rounded.FastForward, "Next",
                tint = look.fg.copy(alpha = if (ui.hasNext) 1f else 0.35f),
                modifier = Modifier.size(glyph)
            )
        }
    }
}

@Composable
private fun PlControlsFive(ui: PlayerUi, look: Look, style: String) {
    val on = if (style == "spotify") Color(0xFF1DB954) else Color.White
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlToggle(Icons.Rounded.Shuffle, "Shuffle", ui.shuffle, look.fg, on) { Graph.player.toggleShuffle() }
        IconButton(onClick = { Graph.player.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = look.fg, modifier = Modifier.size(38.dp))
        }
        PlPlayButton(ui, 70.dp, true, Color.White, Color.Black)
        IconButton(
            onClick = { Graph.player.next() },
            enabled = ui.hasNext,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                Icons.Rounded.SkipNext, "Next",
                tint = look.fg.copy(alpha = if (ui.hasNext) 1f else 0.35f),
                modifier = Modifier.size(38.dp)
            )
        }
        PlToggle(
            if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Repeat", ui.repeat != Player.REPEAT_MODE_OFF, look.fg, on
        ) { Graph.player.cycleRepeat() }
    }
}

@Composable
private fun PlToggle(
    icon: ImageVector,
    desc: String,
    active: Boolean,
    fg: Color,
    activeTint: Color,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (active) activeTint.copy(alpha = 0.14f) else Color.Transparent, tween(200), label = "toggleBg"
    )
    Box(
        Modifier.size(48.dp).bounceClick(onClick = onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, desc,
            tint = if (active) activeTint else fg.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlBottomRow(
    style: String,
    look: Look,
    ui: PlayerUi,
    liked: Boolean,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit
) {
    val fg = look.fg
    val repeatIcon = if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
    val likeIcon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
    when (style) {
        "spotify" -> Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", false, fg, fg, onQueue)
        }
        "ytm" -> Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlToggle(likeIcon, if (liked) "Unlike" else "Like", liked, fg, Color(0xFFFF4E45), onLike)
            Row(
                Modifier
                    .bounceClick(onClick = onQueue)
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = 0.14f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("UP NEXT", color = fg, style = MaterialTheme.typography.labelLarge)
            }
            PlToggle(Icons.Rounded.MoreVert, "More", false, fg, fg, onMore)
        }
        "wavy" -> Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlToggle(Icons.Rounded.Shuffle, "Shuffle", ui.shuffle, fg, fg) { Graph.player.toggleShuffle() }
            PlToggle(likeIcon, if (liked) "Unlike" else "Like", liked, fg, fg, onLike)
            PlToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", false, fg, fg, onQueue)
            PlToggle(repeatIcon, "Repeat", ui.repeat != Player.REPEAT_MODE_OFF, fg, fg) { Graph.player.cycleRepeat() }
        }
        else -> Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlToggle(Icons.Rounded.Shuffle, "Shuffle", ui.shuffle, fg, fg) { Graph.player.toggleShuffle() }
            PlToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", false, fg, fg, onQueue)
            PlToggle(repeatIcon, "Repeat", ui.repeat != Player.REPEAT_MODE_OFF, fg, fg) { Graph.player.cycleRepeat() }
        }
    }
}

@Composable
private fun PlVolume(look: Look, apple: Boolean) {
    val fg = look.fg
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }

    // Follow the hardware volume buttons.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            }
        }
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeDown, "Volume down", tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        SeekBar(
            progress = vol,
            onSeek = {
                vol = it
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0)
            },
            onSeekEnd = {
                vol = it
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0)
            },
            trackColor = fg.copy(alpha = if (apple) 0.28f else 0.18f),
            fillColor = fg.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            trackHeight = if (apple) 6.dp else 4.dp,
            thumbAlways = !apple
        )
        Icon(Icons.Rounded.VolumeUp, "Volume up", tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}
