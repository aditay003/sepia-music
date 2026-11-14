package app.sepia.music.work

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.sepia.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Songs stored on the phone itself (the "Local" row in the library). */
object LocalMusic {

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission()) == PackageManager.PERMISSION_GRANTED

    suspend fun scan(ctx: Context): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        ctx.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                val artist = c.getString(2)
                out.add(
                    Song(
                        id = "local:$id",
                        title = c.getString(1) ?: "Unknown",
                        artist = if (artist == null || artist == "<unknown>") "Unknown artist" else artist,
                        album = c.getString(3),
                        durationSec = c.getLong(4) / 1000,
                        thumb = "content://media/external/audio/albumart/${c.getLong(5)}",
                        localUri = uri.toString()
                    )
                )
            }
        }
        out
    }
}
