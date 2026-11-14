package app.sepia.music.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.sepia.music.Graph
import app.sepia.music.data.DlState
import app.sepia.music.data.Song
import app.sepia.music.data.isLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object Downloads {

    /** Where downloaded songs are stored (private to the app, no permission needed). */
    fun dir(): File {
        val base = Graph.app.getExternalFilesDir(null) ?: Graph.app.filesDir
        return File(base, "music").apply { mkdirs() }
    }

    fun enqueue(songs: List<Song>) {
        val wm = WorkManager.getInstance(Graph.app)
        val network = if (Graph.prefs.wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED
        for (s in songs) {
            if (s.isLocal) continue
            val state = Graph.repo.downloads.value[s.id]?.state
            if (state == DlState.DONE || state == DlState.RUNNING || state == DlState.QUEUED) continue
            Graph.scope.launch(Dispatchers.IO) {
                Graph.repo.queueDownload(s)
                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf("id" to s.id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                    .build()
                wm.enqueueUniqueWork("dl-${s.id}", ExistingWorkPolicy.REPLACE, request)
            }
        }
    }

    fun remove(songId: String) {
        WorkManager.getInstance(Graph.app).cancelUniqueWork("dl-$songId")
        Graph.scope.launch(Dispatchers.IO) {
            val entry = Graph.repo.downloads.value[songId]
            if (entry != null && entry.path.isNotEmpty()) File(entry.path).delete()
            File(dir(), "$songId.part").delete()
            Graph.repo.deleteDownloadRow(songId)
        }
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val repo = Graph.repo
        val song = repo.song(id) ?: return@withContext Result.failure()
        val part = File(Downloads.dir(), "$id.part")
        try {
            repo.setDownload(id, DlState.RUNNING, 0)
            val r = Graph.source.resolve(id, fresh = true)
            val ext = if (r.mime.contains("webm")) "webm" else "m4a"
            val safe = "${song.artist} - ${song.title}"
                .replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")
                .trim()
                .take(80)
            val file = File(Downloads.dir(), "$safe [$id].$ext")

            val rb = okhttp3.Request.Builder().url(r.url).header("Range", "bytes=0-")
            for ((k, v) in r.headers) rb.header(k, v)

            Graph.http.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                var done = 0L
                var last = -5
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            ensureActive()
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct >= last + 5) {
                                    last = pct
                                    repo.setDownload(id, DlState.RUNNING, pct)
                                }
                            }
                        }
                    }
                }
            }
            if (file.exists()) file.delete()
            if (!part.renameTo(file)) throw IOException("Couldn't save the file")
            repo.finishDownload(id, file.absolutePath, file.length())
            Result.success()
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            repo.setDownload(id, DlState.FAILED, 0)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
