package app.sepia.music.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import app.sepia.music.Graph
import app.sepia.music.data.DlState
import app.sepia.music.data.Song
import app.sepia.music.player.PlayerUi
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs
// ---------------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------------

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun friendly(e: Throwable): String = when {
    e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ||
        e is java.net.ConnectException -> "No connection. Check your internet and try again."
    e is org.schabi.newpipe.extractor.exceptions.ReCaptchaException ->
        "YouTube asked for a captcha. Try again in a little while."
    else -> e.message?.takeIf { it.isNotBlank() }?.take(140) ?: "Something went wrong. Try again."
}

/** Soft "squish" when pressed. Put it first in a modifier chain so the whole thing scales. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Stops taps from reaching screens underneath an overlay. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    this.drawBehind {
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(base, hi, base),
                startX = size.width * x,
                endX = size.width * (x + 1f)
            )
        )
    }
}

/** Playback position in ms, refreshed ~5x per second while the app is on screen. */
@Composable
fun rememberPosition(): State<Long> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = Graph.player.exo.currentPosition, key1 = owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Graph.player.exo.currentPosition
                delay(200)
            }
        }
    }
}

/** Where we are in the current song, and how long it is. */
data class PlaybackPos(val pos: Long, val dur: Long)

private fun readPlayback(): PlaybackPos {
    val p = Graph.player.exo
    val raw = p.duration
    // Streams often don't report their length straight away, so fall back to the length we saved with the song.
    val dur = if (raw > 0) raw else (p.currentMediaItem?.mediaMetadata?.extras?.getLong("dur") ?: 0L) * 1000L
    return PlaybackPos(p.currentPosition.coerceAtLeast(0L), dur)
}

/** Position and length of the current song, refreshed ~5x per second while the app is on screen. */
@Composable
fun rememberPlayback(): State<PlaybackPos> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = readPlayback(), key1 = owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = readPlayback()
                delay(200)
            }
        }
    }
}

/** A dominant colour from the artwork, used to tint the player background. */
@Composable
fun rememberArtColor(url: String?): State<Color?> {
    val ctx = LocalContext.current
    return produceState<Color?>(initialValue = null, key1 = url) {
        if (url.isNullOrEmpty()) return@produceState
        val request = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build()
        val result = ctx.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null) {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).maximumColorCount(16).generate()
            }
            val rgb = palette.getDarkMutedColor(palette.getMutedColor(palette.getDominantColor(0)))
            value = if (rgb != 0) Color(rgb) else null
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceVariant, tween(220), label = "pillBg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, tween(220), label = "pillFg")
    Box(
        modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (filled) cs.primary else cs.surfaceVariant)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) cs.onPrimary else cs.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (filled) cs.onPrimary else cs.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, radius: Dp = 14.dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(cs.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = cs.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize(0.4f)
        )
        if (!url.isNullOrEmpty()) {
            val ctx = LocalContext.current
            val request = remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(200).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun Equalizer(color: Color, modifier: Modifier = Modifier.size(18.dp), active: Boolean = true) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(
        0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(430, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    val c by t.animateFloat(
        0.2f, 0.9f, infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse), label = "c"
    )
    Canvas(modifier) {
        val w = size.width / 5f
        val heights = if (active) listOf(a, b, c) else listOf(0.3f, 0.3f, 0.3f)
        heights.forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2 * w, size.height - bh),
                size = Size(w, bh),
                cornerRadius = CornerRadius(w / 2f)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Pill("Try again", true, onRetry)
    }
}

@Composable
fun CoverCard(thumb: String?, title: String, subtitle: String, playing: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(124.dp).bounceClick(onClick = onClick)) {
        Box(Modifier.size(124.dp)) {
            Cover(thumb, Modifier.fillMaxSize(), radius = 18.dp)
            if (playing) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) { Equalizer(Color.White, Modifier.size(24.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
        )
        Text(
            subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}

@Composable
fun ShimmerCard() {
    Column(Modifier.width(124.dp)) {
        Box(Modifier.size(124.dp).clip(RoundedCornerShape(18.dp)).shimmer())
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(90.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
    }
}

@Composable
fun SkeletonRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).shimmer())
        Spacer(Modifier.width(14.dp))
        Column {
            Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(110.dp).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val dl = LocalDownloads.current[song.id]
    Row(
        modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp)) {
            Cover(song.thumb, Modifier.fillMaxSize())
            if (playing) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) { Equalizer(Color.White, Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) cs.primary else cs.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (dl?.state) {
                    DlState.DONE -> {
                        Icon(
                            Icons.Rounded.DownloadDone, "Downloaded", tint = cs.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    DlState.RUNNING, DlState.QUEUED -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = cs.primary
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    else -> {}
                }
                Text(
                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "More", tint = cs.onSurfaceVariant) }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(
            title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Floating navigation + mini player
// ---------------------------------------------------------------------------------------------

/** A frosted, liquid-glass look: soft tint, a bright light reflection on top and a glowing rim. */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val dark = AppTheme.isDark()
    val top = if (dark) Color(0xD93A2B22) else Color(0xD9FFFFFF)
    val bottom = if (dark) Color(0xCC2A1E18) else Color(0xCCF6EADB)
    val sheen = if (dark) Color(0x24FFFFFF) else Color(0x99FFFFFF)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.55f else 1f),
        0.4f to Color.White.copy(alpha = if (dark) 0.07f else 0.3f),
        0.6f to Color.White.copy(alpha = if (dark) 0.07f else 0.3f),
        1f to Color.White.copy(alpha = if (dark) 0.4f else 0.85f)
    )
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = size.height * 0.5f))
        }
        .border(1.2.dp, rim, shape)
}

@Composable
fun NavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = AppTheme.isDark()
    val density = LocalDensity.current
    val tabs = listOf(
        "Home" to Icons.Rounded.Home,
        "Library" to Icons.Rounded.LibraryMusic,
        "Search" to Icons.Rounded.Search
    )
    val shape = RoundedCornerShape(32.dp)
    // Which way did we just move? The front edge of the pill rushes ahead and the back edge trails behind: that is the liquid stretch.
    val last = remember { intArrayOf(selected) }
    val heading = remember { booleanArrayOf(true) }
    if (selected != last[0]) {
        heading[0] = selected > last[0]
        last[0] = selected
    }
    val right = heading[0]

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(68.dp)
            .glass(shape)
    ) {
        val pad = 6.dp
        val padPx = with(density) { pad.toPx() }
        val slot = (constraints.maxWidth - 2 * padPx) / tabs.size
        val front = spring<Float>(dampingRatio = 0.7f, stiffness = 800f)
        val back = spring<Float>(dampingRatio = 0.7f, stiffness = 230f)
        val start by animateFloatAsState(selected * slot, if (right) back else front, label = "pillStart")
        val end by animateFloatAsState((selected + 1) * slot, if (right) front else back, label = "pillEnd")
        val stretch = ((end - start) / slot - 1f).coerceIn(0f, 1f)

        Box(
            Modifier
                .offset { IntOffset((padPx + start).roundToInt(), padPx.roundToInt()) }
                .width(with(density) { (end - start).toDp() })
                .height(maxHeight - pad * 2)
                .graphicsLayer { scaleY = 1f - 0.1f * stretch }
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (dark) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.95f),
                            if (dark) Color.White.copy(alpha = 0.08f) else cs.primaryContainer.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.5f else 1f), Color.White.copy(alpha = 0.1f))
                    ),
                    RoundedCornerShape(26.dp)
                )
        )
        Row(Modifier.fillMaxSize().padding(pad)) {
            tabs.forEachIndexed { i, tab ->
                val sel = i == selected
                val tint by animateColorAsState(
                    if (sel) cs.primary else cs.onSurfaceVariant, tween(200), label = "navTint"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bounceClick { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(tab.second, tab.first, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        tab.first, color = tint, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(ui: PlayerUi, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val song = ui.song ?: return
    val cs = MaterialTheme.colorScheme
    val pb by rememberPlayback()
    val shape = RoundedCornerShape(30.dp)
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .onSizeChanged { width = it.width.toFloat() }
            .graphicsLayer {
                translationY = dy.value
                alpha = (1f - dy.value / 280f).coerceIn(0f, 1f)
            }
            .bounceClick(onClick = onOpen)
            .glass(shape)
            .drawBehind {
                val d = pb.dur
                if (d > 0) {
                    val h = 3.dp.toPx()
                    val frac = (pb.pos.toFloat() / d).coerceIn(0f, 1f)
                    drawRect(cs.primary, topLeft = Offset(0f, size.height - h), size = Size(size.width * frac, h))
                }
            }
            .pointerInput(Unit) {
                // Swipe left / right: next / previous song. Swipe down: stop the music and hide the player.
                var axis = 0
                var tx = 0f
                var ty = 0f
                detectDragGestures(
                    onDragStart = {
                        axis = 0
                        tx = 0f
                        ty = 0f
                    },
                    onDragEnd = {
                        val w = width
                        if (axis == 1 && abs(dx.value) > w * 0.2f) {
                            val dir = if (dx.value < 0) 1 else -1
                            scope.launch {
                                dx.animateTo(-dir * w * 0.5f, tween(110))
                                if (dir > 0) Graph.player.next() else Graph.player.skipToPrevious()
                                dx.snapTo(dir * w * 0.5f)
                                dx.animateTo(0f, tween(230, easing = FastOutSlowInEasing))
                            }
                        } else if (axis == 2 && dy.value > 70f) {
                            scope.launch {
                                dy.animateTo(300f, tween(150))
                                Graph.player.stopAndClear()
                            }
                        } else {
                            scope.launch { dx.animateTo(0f, spring()) }
                            scope.launch { dy.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dx.animateTo(0f) }
                        scope.launch { dy.animateTo(0f) }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (axis == 0) {
                            tx += amount.x
                            ty += amount.y
                            if (abs(tx) > 10f || abs(ty) > 10f) axis = if (abs(tx) >= abs(ty)) 1 else 2
                        }
                        if (axis == 1) scope.launch { dx.snapTo(dx.value + amount.x) }
                        if (axis == 2) scope.launch { dy.snapTo((dy.value + amount.y).coerceAtLeast(0f)) }
                    }
                )
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The cover and the title slide with your finger, like in YT Music; the buttons stay put.
        Row(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    translationX = dx.value
                    alpha = (1f - abs(dx.value) / (width * 0.9f)).coerceIn(0f, 1f)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(song.thumb, Modifier.size(46.dp), radius = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                Text(
                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { Graph.player.toggle() }) {
            if (ui.buffering) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.primary)
            } else {
                Crossfade(targetState = ui.playing, animationSpec = tween(180), label = "miniPlay") { p ->
                    Icon(
                        if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (p) "Pause" else "Play",
                        tint = cs.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        IconButton(onClick = { Graph.player.next() }, enabled = ui.hasNext) {
            Icon(
                Icons.Rounded.SkipNext, "Next",
                tint = cs.onSurface.copy(alpha = if (ui.hasNext) 1f else 0.35f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Seek / volume bar
// ---------------------------------------------------------------------------------------------

@Composable
fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
    thumbAlways: Boolean = true,
    wavy: Boolean = false,
    wavePlaying: Boolean = false
) {
    val seek by rememberUpdatedState(onSeek)
    val seekEnd by rememberUpdatedState(onSeekEnd)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f), tween(220, easing = LinearEasing), label = "seek"
    )
    val shown = if (dragging) dragValue else animated
    val thumb by animateDpAsState(if (dragging) 18.dp else 12.dp, label = "thumb")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> seekEnd((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragValue = (o.x / size.width).coerceIn(0f, 1f)
                        seek(dragValue)
                    },
                    onDragEnd = {
                        dragging = false
                        seekEnd(dragValue)
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                        seek(dragValue)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val w = constraints.maxWidth
        if (wavy) {
            WaveTrack(shown, trackColor, fillColor, wavePlaying)
        } else {
            Box(Modifier.fillMaxWidth().height(trackHeight).clip(CircleShape).background(trackColor))
            Box(Modifier.fillMaxWidth(shown).height(trackHeight).clip(CircleShape).background(fillColor))
            if (thumbAlways || dragging) {
                Box(
                    Modifier
                        .offset { IntOffset((w * shown - thumb.roundToPx() / 2f).roundToInt(), 0) }
                        .size(thumb)
                        .background(fillColor, CircleShape)
                )
            }
        }
    }
}

/** A gently moving wave for the part of the song already played. */
@Composable
private fun WaveTrack(progress: Float, track: Color, fill: Color, playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        val w = size.width
        val mid = size.height / 2f
        val amp = if (playing) 5.dp.toPx() else 1.5.dp.toPx()
        val len = 28.dp.toPx()
        val twoPi = (2.0 * PI).toFloat()
        val end = (w * progress).coerceIn(0f, w)
        drawLine(track, Offset(end, mid), Offset(w, mid), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        val path = Path()
        path.moveTo(0f, mid + amp * sin(0f - phase))
        var x = 4f
        while (x < end) {
            path.lineTo(x, mid + amp * sin(x / len * twoPi - phase))
            x += 4f
        }
        val endY = mid + amp * sin(end / len * twoPi - phase)
        path.lineTo(end, endY)
        drawPath(path, fill, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(fill, radius = 7.dp.toPx(), center = Offset(end, endY))
    }
}
