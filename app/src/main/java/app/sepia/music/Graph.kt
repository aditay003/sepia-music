@file:OptIn(UnstableApi::class)

package app.sepia.music

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import app.sepia.music.data.AppDb
import app.sepia.music.data.Prefs
import app.sepia.music.data.Repo
import app.sepia.music.net.MusicSource
import app.sepia.music.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Tiny service locator: one instance of everything, created in Application.onCreate. */
object Graph {
    lateinit var app: Application
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var db: AppDb
        private set
    lateinit var repo: Repo
        private set
    lateinit var source: MusicSource
        private set
    lateinit var player: PlayerController
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Disk cache for streamed audio (this is what powers the "Cached" row in the library). */
    val cache: SimpleCache by lazy {
        SimpleCache(
            File(app.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(prefs.cacheMb * 1024L * 1024L),
            StandaloneDatabaseProvider(app)
        )
    }

    fun init(application: Application) {
        app = application
        prefs = Prefs(application)
        db = Room.databaseBuilder(application, AppDb::class.java, "sepia.db")
            .fallbackToDestructiveMigration()
            .build()
        repo = Repo(db, scope)
        source = MusicSource(prefs, http).also { it.init() }
        player = PlayerController(application, scope)
        // Warm the cache on a background thread so the first play isn't delayed.
        scope.launch(Dispatchers.IO) { cache }
    }

    /** Song ids whose audio is fully stored in the stream cache. */
    fun cachedIds(): Set<String> {
        val out = HashSet<String>()
        for (key in cache.keys.toList()) {
            val len = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            if (len > 0 && cache.isCached(key, 0, len)) out.add(key.substringBefore('|'))
        }
        return out
    }
}
