package com.ezsmb.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.ezsmb.app.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayInputStream
import java.io.IOException

/** Visible rows only; bounded IO, two decoders, memory cache only (no private files on disk). */
class Thumbnails(private val engine: SmbEngine) {
    private val permits = Semaphore(2)
    private val folderPermits = Semaphore(1)
    private val folderChecked = LruCache<String, Long>(256)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    suspend fun load(login: Login, share: String, path: String, entry: Entry): Bitmap? {
        if (entry.directory) return loadFolder(login, share, path, entry)
        val kind = previewKind(entry.name) ?: return null
        if (entry.directory || entry.size <= 0) return null
        val key = listOf(login.network, login.guid, login.target.host, login.target.port, login.credentials.username,
            login.credentials.domain, share, path, entry.name, entry.size, entry.modified).joinToString("\u0000")
        cache.get(key)?.let { return it }
        return permits.withPermit {
            cache.get(key)?.let { return@withPermit it }
            withContext(Dispatchers.IO) {
                val job = currentCoroutineContext().job
                try {
                    engine.open(login.target, timeout = 5).use { link ->
                        link.authenticate(login.credentials, login.guid).use { session ->
                            engine.disk(session, share).use { disk ->
                                engine.read(disk, childPath(path, entry.name)).use { file ->
                                    val length = file.fileInformation.standardInformation.endOfFile
                                    require(length == entry.size)
                                    val deadline = System.nanoTime() + 8_000_000_000L
                                    var consumed = 0L
                                    fun checkBudget() {
                                        job.ensureActive()
                                        if (consumed > 24 * 1024 * 1024 || System.nanoTime() > deadline) throw IOException("Preview budget")
                                    }
                                    val bitmap = if (kind == PreviewKind.IMAGE) {
                                        // Never allocate or fetch a huge original for a thumbnail.
                                        if (length > 12 * 1024 * 1024) return@withContext null
                                        val data = ByteArray(length.toInt()); var offset = 0
                                        while (offset < data.size) {
                                            checkBudget()
                                            val n = file.read(data, offset.toLong(), offset, minOf(256 * 1024, data.size - offset))
                                            if (n <= 0) throw IOException("Incomplete image")
                                            offset += n; consumed += n
                                        }
                                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                                        val options = BitmapFactory.Options().apply { inSampleSize = previewSample(bounds.outWidth, bounds.outHeight) }
                                        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return@withContext null
                                        val orientation = runCatching { ExifInterface(ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                                        val matrix = Matrix().apply {
                                            when (orientation) {
                                                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                                                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                                                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
                                            }
                                        }
                                        if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                                    } else {
                                        val source = object : MediaDataSource() {
                                            override fun getSize() = length
                                            override fun close() {}
                                            @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                                                checkBudget()
                                                if (position < 0) throw IOException("Invalid position")
                                                if (position >= length) return -1
                                                if (size == 0) return 0
                                                val n = file.read(buffer, position, offset, minOf(size.toLong(), 256 * 1024L, length - position).toInt())
                                                if (n > 0) consumed += n
                                                return if (n == 0) -1 else n
                                            }
                                        }
                                        val retriever = MediaMetadataRetriever()
                                        try {
                                            retriever.setDataSource(source)
                                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 384)
                                        } finally { retriever.release(); source.close() }
                                    }
                                    job.ensureActive()
                                    bitmap?.also { cache.put(key, it) }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { null } // Unsupported media retains a truthful type icon.
            }
        }
    }
    private suspend fun loadFolder(login: Login, share: String, path: String, entry: Entry): Bitmap? {
        val key = "folder:" + listOf(login.network, login.guid, login.target.host, login.target.port,
            login.credentials.username, login.credentials.domain, login.credentials.anonymous, share, path, entry.name, entry.modified).joinToString("\u0000")
        fun recent() = folderChecked.get(key)?.let { System.nanoTime() - it < 30_000_000_000L } == true
        if (recent()) return cache.get(key)
        return folderPermits.withPermit {
            if (recent()) return@withPermit cache.get(key)
            val directoryPath = childPath(path, entry.name)
            try {
                val candidates = permits.withPermit {
                    withContext(Dispatchers.IO) {
                        val job = currentCoroutineContext().job
                        val deadline = System.nanoTime() + 3_000_000_000L
                        engine.open(login.target, timeout = 2).use { link ->
                            link.authenticate(login.credentials, login.guid).use { session ->
                                engine.disk(session, share).use { disk ->
                                    engine.previewEntries(disk, directoryPath) {
                                        job.ensureActive()
                                        if (System.nanoTime() > deadline) throw IOException("Directory preview budget")
                                    }
                                }
                            }
                        }
                    }
                }
                var cover: Bitmap? = null
                for (candidate in candidates) {
                    currentCoroutineContext().ensureActive()
                    cover = load(login, share, directoryPath, candidate)
                    if (cover != null) break
                }
                if (cover != null) cache.put(key, cover) else cache.remove(key)
                folderChecked.put(key, System.nanoTime())
                cover
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { folderChecked.put(key, System.nanoTime()); cache.remove(key); null }
        }
    }
    fun clear() { cache.evictAll(); folderChecked.evictAll() }
}
