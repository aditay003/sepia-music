package app.sepia.music.net

import android.net.Uri
import app.sepia.music.data.Prefs
import app.sepia.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

sealed interface Item {
    data class Track(val song: Song) : Item
    data class Group(
        val title: String,
        val subtitle: String,
        val url: String,
        val thumb: String?,
        val album: Boolean
    ) : Item
    data class Artist(val name: String, val thumb: String?) : Item
}

enum class SearchKind(val label: String, val filter: String) {
    Songs("Songs", "music_songs"),
    Videos("Videos", "music_videos"),
    Albums("Albums", "music_albums"),
    Artists("Artists", "music_artists"),
    Playlists("Playlists", "music_playlists")
}

class SearchSession(val extractor: SearchExtractor, var next: Page?, val kind: SearchKind)

class Resolved(
    val url: String,
    val mime: String,
    val kbps: Int,
    val key: String,
    val headers: Map<String, String>,
    val expiresAt: Long
)

class CollectionData(val title: String, val subtitle: String, val songs: List<Song>)

// ---------------------------------------------------------------------------------------------
// HTTP for the extractor
// ---------------------------------------------------------------------------------------------

class NpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val data: ByteArray? = request.dataToSend()
        val body = data?.toRequestBody(null)
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
        builder.header("User-Agent", UA)
        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (v in values) builder.addHeader(name, v)
        }
        return client.newCall(builder.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

// ---------------------------------------------------------------------------------------------
// Search + stream resolution
// ---------------------------------------------------------------------------------------------

class MusicSource(private val prefs: Prefs, private val http: OkHttpClient) {

    private val urlCache = ConcurrentHashMap<String, Resolved>()

    /** How many times playback of a song failed in a row; picks a different strategy each time. */
    val attempts = ConcurrentHashMap<String, Int>()

    fun init() {
        NewPipe.init(
            NpDownloader(http),
            Localization.DEFAULT,
            ContentCountry(Locale.getDefault().country.ifEmpty { "US" })
        )
    }

    fun invalidate(id: String) {
        urlCache.remove(id)
    }

    // ---- search ----------------------------------------------------------------------------

    suspend fun search(query: String, kind: SearchKind): Pair<SearchSession, List<Item>> =
        withContext(Dispatchers.IO) {
            val service = ServiceList.YouTube
            val handler = service.getSearchQHFactory().fromQuery(query, listOf(kind.filter), "")
            val extractor = service.getSearchExtractor(handler)
            extractor.fetchPage()
            val page = extractor.getInitialPage()
            val items = page.getItems().mapNotNull { toItem(it, kind) }
            Pair(SearchSession(extractor, page.getNextPage(), kind), items)
        }

    suspend fun more(session: SearchSession): List<Item> = withContext(Dispatchers.IO) {
        val next = session.next ?: return@withContext emptyList<Item>()
        val page = session.extractor.getPage(next)
        session.next = page.getNextPage()
        page.getItems().mapNotNull { toItem(it, session.kind) }
    }

    suspend fun searchSongs(query: String): List<Song> {
        val (_, items) = search(query, SearchKind.Songs)
        return items.mapNotNull { (it as? Item.Track)?.song }.distinctBy { it.id }
    }

    /** What people type as they search (YouTube's own suggestions). */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        ServiceList.YouTube.getSuggestionExtractor().suggestionList(query).take(8)
    }

    /**
     * Songs like the given one - YouTube Music's "radio" for that track - without the same song again
     * from other channels. Falls back to YouTube's mix, then to other songs by the same artist.
     */
    suspend fun radio(id: String, artist: String, title: String = ""): List<Song> {
        val seed = plainTitle(title)
        var mix: List<Song> = emptyList()
        for (url in listOf(
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://www.youtube.com/watch?v=$id&list=RD$id"
        )) {
            val got: List<Song> = try {
                withContext(Dispatchers.IO) {
                    val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
                    val out = ArrayList<Song>()
                    for (item in info.getRelatedItems()) {
                        val s = toSong(item) ?: continue
                        if (s.durationSec != 0L && (s.durationSec < 60L || s.durationSec > 900L)) continue
                        out.add(s)
                    }
                    out
                }
            } catch (e: Exception) {
                emptyList()
            }
            if (got.size > mix.size) mix = got
            if (mix.size >= 8) break
        }
        var all: List<Song> = mix
        if (all.size < 5) {
            val byArtist: List<Song> = try {
                searchSongs(if (artist.isBlank()) "top songs" else artist)
            } catch (e: Exception) {
                emptyList()
            }
            all = all + byArtist
        }
        val seen = HashSet<String>()
        if (seed.isNotEmpty()) seen.add(seed)
        val result = ArrayList<Song>()
        for (song in all) {
            if (song.id == id) continue
            val t = plainTitle(song.title)
            if (t.isNotEmpty() && !seen.add(t)) continue
            result.add(song)
        }
        return result
    }

    suspend fun collection(url: String): CollectionData = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val info = PlaylistInfo.getInfo(service, url)
        val songs = ArrayList<Song>()
        for (item in info.getRelatedItems()) {
            val s = toSong(item)
            if (s != null) songs.add(s)
        }
        var next: Page? = info.getNextPage()
        var guard = 0
        while (next != null && guard < 8) {
            val page = PlaylistInfo.getMoreItems(service, url, next)
            for (item in page.getItems()) {
                val s = toSong(item)
                if (s != null) songs.add(s)
            }
            next = page.getNextPage()
            guard++
        }
        CollectionData(info.getName(), info.getUploaderName() ?: "", songs.distinctBy { it.id })
    }

    // ---- streams ---------------------------------------------------------------------------

    suspend fun resolve(id: String, fresh: Boolean = false): Resolved = withContext(Dispatchers.IO) {
        if (!fresh) {
            val cached = urlCache[id]
            if (cached != null && cached.expiresAt - System.currentTimeMillis() > 120_000L) {
                return@withContext cached
            }
        }
        val attempt = attempts[id] ?: 0
        val hasCustom = prefs.pipedUrl.isNotBlank()
        val resolved: Resolved = if (attempt >= 3 && hasCustom) {
            try {
                viaPiped(id)
            } catch (e: Exception) {
                viaNewPipe(id, attempt)
            }
        } else {
            try {
                viaNewPipe(id, attempt)
            } catch (e: Exception) {
                if (hasCustom) viaPiped(id) else throw e
            }
        }
        urlCache[id] = resolved
        resolved
    }

    private fun viaNewPipe(id: String, attempt: Int): Resolved {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id")
        extractor.fetchPage()
        val streams = extractor.getAudioStreams().filter { s ->
            val c = s.getContent()
            !c.isNullOrEmpty() && c.startsWith("http") && !c.contains("/manifest/")
        }
        if (streams.isEmpty()) throw IOException("No playable audio found for this song")
        val sorted = if (prefs.quality == "low") {
            streams.sortedBy { it.getAverageBitrate() }
        } else {
            streams.sortedByDescending { it.getAverageBitrate() }
        }
        val pick = sorted[minOf(maxOf(attempt - 1, 0), sorted.size - 1)]
        val url = pick.getContent()
        val uri = Uri.parse(url)
        val mime = uri.getQueryParameter("mime") ?: "audio/mp4"
        val kbps = pick.getAverageBitrate()
        val expiry = uri.getQueryParameter("expire")?.toLongOrNull()?.times(1000L)
            ?: (System.currentTimeMillis() + 20 * 60_000L)
        return Resolved(url, mime, kbps, "$id|${mime.substringAfter('/')}|$kbps", headersFor(uri), expiry)
    }

    private fun viaPiped(id: String): Resolved {
        val base = prefs.pipedUrl.trim().trimEnd('/')
        val req = okhttp3.Request.Builder()
            .url("$base/streams/$id")
            .header("User-Agent", NpDownloader.UA)
            .build()
        val text = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Your custom server answered ${r.code}")
            r.body?.string().orEmpty()
        }
        val arr = JSONObject(text).optJSONArray("audioStreams")
            ?: throw IOException("Your custom server returned no audio")
        var best: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.optString("url").startsWith("http")) continue
            val b = best
            if (b == null || o.optInt("bitrate") > b.optInt("bitrate")) best = o
        }
        val pick = best ?: throw IOException("Your custom server returned no audio")
        val mime = pick.optString("mimeType", "audio/mp4")
        val kbps = pick.optInt("bitrate") / 1000
        return Resolved(
            pick.getString("url"),
            mime,
            kbps,
            "$id|piped|${mime.substringAfter('/')}|$kbps",
            emptyMap(),
            System.currentTimeMillis() + 15 * 60_000L
        )
    }

    /** googlevideo checks that the User-Agent matches the client the URL was issued for. */
    private fun headersFor(uri: Uri): Map<String, String> {
        val ua = when (uri.getQueryParameter("c")) {
            "IOS" -> extractorUa("getIosUserAgent")
                ?: "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
            "ANDROID", "ANDROID_VR", "ANDROID_MUSIC" -> extractorUa("getAndroidUserAgent")
                ?: "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
            else -> NpDownloader.UA
        }
        return mapOf("User-Agent" to ua)
    }

    private fun extractorUa(method: String): String? = runCatching {
        val c = Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper")
        val m = c.getMethod(method, Localization::class.java)
        m.invoke(null, *arrayOf<Any?>(null)) as? String
    }.getOrNull()

    // ---- mapping ---------------------------------------------------------------------------

    private fun toItem(i: InfoItem, kind: SearchKind): Item? {
        val thumb = art(bestThumb(i))
        return when (i) {
            is StreamInfoItem -> {
                val s = toSong(i) ?: return null
                Item.Track(s)
            }
            is PlaylistInfoItem -> Item.Group(
                i.getName(),
                i.getUploaderName() ?: "",
                i.getUrl(),
                thumb,
                kind == SearchKind.Albums
            )
            is ChannelInfoItem -> Item.Artist(i.getName(), thumb)
            else -> null
        }
    }

    private fun toSong(i: StreamInfoItem): Song? {
        val id = videoId(i.getUrl()) ?: return null
        val artist = (i.getUploaderName() ?: "").removeSuffix(" - Topic")
        return Song(
            id = id,
            title = i.getName(),
            artist = artist,
            durationSec = maxOf(i.getDuration(), 0L),
            thumb = art(bestThumb(i)) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        )
    }

    private fun bestThumb(i: InfoItem): String? {
        val list = i.getThumbnails() ?: return null
        return list.maxByOrNull { it.getHeight() }?.getUrl() ?: list.firstOrNull()?.getUrl()
    }

    companion object {
        private val ID_REGEX = Regex("(?:[?&]v=|youtu\\.be/)([A-Za-z0-9_-]{11})")
        private val SIZE_REGEX = Regex("=[wsh]\\d+.*$")

        fun videoId(url: String?): String? = url?.let { ID_REGEX.find(it)?.groupValues?.get(1) }

        /** Upgrade tiny Google-hosted album art to a crisp square. */
        fun art(url: String?): String? {
            if (url == null) return null
            return if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
                url.replace(SIZE_REGEX, "=w544-h544-l90-rj")
            } else {
                url
            }
        }
    }
}

/** A song's title without the extras that don't make it a different song (brackets, "official video", "remix"...). */
private fun plainTitle(s: String): String =
    s.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
        .replace(Regex("\\b(official|video|audio|lyrics|lyric|hd|4k|full song|remix|slowed|reverb)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
