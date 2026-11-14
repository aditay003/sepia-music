@file:OptIn(UnstableApi::class)

package app.sepia.music.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import app.sepia.music.Graph
import app.sepia.music.data.DlState
import app.sepia.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

/** Everything the UI needs to draw the mini player / player screen. */
data class PlayerUi(
    val song: Song? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val index: Int = 0
)

fun Song.toMediaItem(path: String? = null): MediaItem {
    val uri: Uri = when {
        path != null -> Uri.fromFile(File(path))
        localUri != null -> Uri.parse(localUri)
        else -> Uri.parse("ytm://song/$id")
    }
    val extras = Bundle()
    extras.putLong("dur", durationSec)
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(thumb?.let { Uri.parse(it) })
        .setExtras(extras)
        .build()
    return MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(meta).build()
}

fun MediaItem.toSong(): Song = Song(
    id = mediaId,
    title = mediaMetadata.title?.toString() ?: "",
    artist = mediaMetadata.artist?.toString() ?: "",
    album = mediaMetadata.albumTitle?.toString(),
    durationSec = mediaMetadata.extras?.getLong("dur") ?: 0L,
    thumb = mediaMetadata.artworkUri?.toString(),
    localUri = if (mediaId.startsWith("local:")) localConfiguration?.uri?.toString() else null
)

/** Turns ytm://song/<id> into a real stream URL right before ExoPlayer needs it. */
private class YtResolver : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != "ytm") return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("Bad song id")
        val r = try {
            runBlocking { Graph.source.resolve(id) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "Couldn't load this song", e)
        }
        return dataSpec.buildUpon()
            .setUri(Uri.parse(r.url))
            .setKey(r.key)
            .setHttpRequestHeaders(r.headers)
            .build()
    }
}

class PlayerController(private val app: Application, private val scope: CoroutineScope) {

    /** The one and only player. Must be touched on the main thread. */
    val exo: ExoPlayer by lazy { build() }

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    /** Short user-facing messages ("Couldn't play ..."). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val retries = HashMap<String, Int>()
    private var restored = false

    /** The song we last asked for "similar songs" for, so we don't ask twice. */
    private var radioFor: String? = null

    private fun build(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val cached = CacheDataSource.Factory()
            .setCache(Graph.cache)
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(app, cached), YtResolver())
        val player = ExoPlayer.Builder(app)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(resolving, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 60_000, 1_500, 3_000)
                    .build()
            )
            .build()
        player.addListener(Listener())
        return player
    }

    // ---- state -----------------------------------------------------------------------------

    private fun refresh(rebuildQueue: Boolean) {
        val p = exo
        val queue = if (rebuildQueue) {
            (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toSong() }
        } else {
            _ui.value.queue
        }
        val state = p.playbackState
        _ui.value = PlayerUi(
            song = p.currentMediaItem?.toSong(),
            playing = p.playWhenReady && state != Player.STATE_ENDED && state != Player.STATE_IDLE,
            buffering = p.playWhenReady && state == Player.STATE_BUFFERING,
            hasNext = p.hasNextMediaItem(),
            hasPrev = p.hasPreviousMediaItem(),
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
            queue = queue,
            index = p.currentMediaItemIndex
        )
    }

    private inner class Listener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(events.contains(Player.EVENT_TIMELINE_CHANGED))
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED
                )
            ) saveSession()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            val song = mediaItem.toSong()
            scope.launch(Dispatchers.IO) { Graph.repo.markPlayed(song) }
            prefetchNext()
            maybeAutoplay()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                radioFor = null
                maybeAutoplay()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                exo.currentMediaItem?.mediaId?.let {
                    retries.remove(it)
                    Graph.source.attempts.remove(it)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError()
        }
    }

    /** Resolve the next song's stream URL in the background so the switch is instant. */
    private fun prefetchNext() {
        val p = exo
        val n = p.nextMediaItemIndex
        if (n == C.INDEX_UNSET) return
        val item = p.getMediaItemAt(n)
        if (item.localConfiguration?.uri?.scheme != "ytm") return
        val id = item.mediaId
        scope.launch(Dispatchers.IO) { runCatching { Graph.source.resolve(id) } }
    }

    /** When the queue is about to run out, quietly add songs like the last one (like YT Music's radio). */
    private fun maybeAutoplay(force: Boolean = false) {
        val p = exo
        if (!force && !Graph.prefs.autoplay) return
        if (p.mediaItemCount == 0 || p.hasNextMediaItem()) return
        if (p.repeatMode != Player.REPEAT_MODE_OFF) return
        val item = p.currentMediaItem ?: return
        val id = item.mediaId
        if (id.startsWith("local:") || radioFor == id) return
        radioFor = id
        val artist = item.mediaMetadata.artist?.toString() ?: ""
        val title = item.mediaMetadata.title?.toString() ?: ""
        val have = HashSet<String>()
        for (i in 0 until p.mediaItemCount) have.add(p.getMediaItemAt(i).mediaId)
        scope.launch {
            val more = try {
                Graph.source.radio(id, artist, title)
            } catch (e: Exception) {
                emptyList()
            }
            val fresh = more.filter { it.id !in have }.take(25)
            var added = false
            if (fresh.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val q = exo
                    if (q.currentMediaItem?.mediaId == id && !q.hasNextMediaItem()) {
                        q.addMediaItems(fresh.map { itemFor(it) })
                        added = true
                        if (q.playbackState == Player.STATE_ENDED) {
                            q.seekToNextMediaItem()
                            q.play()
                        }
                    }
                }
            }
            if (!added) radioFor = null
        }
    }

    private fun handleError() {
        val p = exo
        val item = p.currentMediaItem ?: return
        val id = item.mediaId
        val n = (retries[id] ?: 0) + 1
        retries[id] = n
        val streamed = item.localConfiguration?.uri?.scheme == "ytm"
        if (streamed && n <= 3) {
            // Expired or rejected URL: forget it, pick another strategy and try again from the same spot.
            Graph.source.invalidate(id)
            Graph.source.attempts[id] = n
            val pos = p.currentPosition
            p.seekTo(p.currentMediaItemIndex, pos)
            p.prepare()
            return
        }
        retries.remove(id)
        val name = item.mediaMetadata.title?.toString() ?: "this song"
        messages.tryEmit("Couldn't play \"$name\"")
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.prepare()
        } else {
            p.pause()
        }
    }

    // ---- commands --------------------------------------------------------------------------

    private fun itemFor(song: Song): MediaItem {
        val d = Graph.repo.downloads.value[song.id]
        val path = if (d != null && d.state == DlState.DONE && File(d.path).exists()) d.path else null
        return song.toMediaItem(path)
    }

    /** Play one song and keep going with similar songs, like YT Music's radio. */
    fun playRadio(song: Song) {
        radioFor = null
        playQueue(listOf(song), 0)
        maybeAutoplay(force = true)
    }

    /** Stop the music and empty the queue (the mini player disappears). */
    fun stopAndClear() {
        val p = exo
        p.stop()
        p.clearMediaItems()
        radioFor = null
        refresh(true)
        try {
            app.stopService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) {
        }
    }

    /** Go to the previous song (never just restarts this one) - used by swipe gestures. */
    fun skipToPrevious() {
        val p = exo
        if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem() else p.seekTo(0)
    }

    fun playQueue(songs: List<Song>, start: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map { itemFor(it) }
        val p = exo
        p.setMediaItems(items, start.coerceIn(0, items.lastIndex), 0L)
        p.prepare()
        p.play()
        startService()
    }

    fun playNext(song: Song) {
        val p = exo
        if (p.mediaItemCount == 0) {
            playQueue(listOf(song))
            return
        }
        p.addMediaItem(p.currentMediaItemIndex + 1, itemFor(song))
    }

    fun addToQueue(song: Song) {
        val p = exo
        if (p.mediaItemCount == 0) {
            playQueue(listOf(song))
            return
        }
        p.addMediaItem(itemFor(song))
    }

    fun toggle() {
        val p = exo
        if (p.mediaItemCount == 0) return
        val state = p.playbackState
        if (p.playWhenReady && state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
            p.pause()
        } else {
            if (state == Player.STATE_IDLE) p.prepare()
            if (state == Player.STATE_ENDED) p.seekToDefaultPosition(0)
            p.play()
            startService()
        }
    }

    fun next() {
        exo.seekToNextMediaItem()
    }

    fun previous() {
        val p = exo
        if (p.currentPosition > 3_000 || !p.hasPreviousMediaItem()) p.seekTo(0) else p.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        exo.seekTo(ms)
    }

    fun toggleShuffle() {
        exo.shuffleModeEnabled = !exo.shuffleModeEnabled
    }

    fun cycleRepeat() {
        exo.repeatMode = when (exo.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun jumpTo(index: Int) {
        exo.seekTo(index, 0L)
        exo.play()
    }

    fun removeAt(index: Int) {
        exo.removeMediaItem(index)
    }

    private fun startService() {
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) {
            // Not allowed right now (e.g. app in background); playback still works.
        }
    }

    // ---- remember the last queue -----------------------------------------------------------

    private fun saveSession() {
        val p = exo
        if (p.mediaItemCount == 0) return
        val ids = JSONArray()
        for (i in 0 until p.mediaItemCount) ids.put(p.getMediaItemAt(i).mediaId)
        Graph.prefs.lastSession = JSONObject()
            .put("ids", ids)
            .put("index", p.currentMediaItemIndex)
            .put("pos", p.currentPosition)
            .toString()
    }

    /** Puts the last queue back (paused) so the mini player is there when the app opens. */
    fun restoreSession() {
        if (restored) return
        restored = true
        val p = exo
        if (p.mediaItemCount > 0) return
        val raw = Graph.prefs.lastSession
        if (raw.isEmpty()) return
        scope.launch {
            try {
                val o = JSONObject(raw)
                val arr = o.getJSONArray("ids")
                val ids = List(arr.length()) { arr.getString(it) }
                val songs = withContext(Dispatchers.IO) { Graph.repo.songsByIds(ids) }
                if (songs.isEmpty()) return@launch
                val index = o.optInt("index").coerceIn(0, songs.lastIndex)
                val pos = o.optLong("pos")
                withContext(Dispatchers.Main) {
                    if (exo.mediaItemCount == 0) {
                        exo.setMediaItems(songs.map { itemFor(it) }, index, pos)
                    }
                }
            } catch (e: Exception) {
                // Ignore a corrupt saved queue.
            }
        }
    }
}
