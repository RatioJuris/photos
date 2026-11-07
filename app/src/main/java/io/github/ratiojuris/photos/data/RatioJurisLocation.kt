// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Immutable
data class RatioJurisLatLon(val lat: Double, val lon: Double) {
    val isValid: Boolean get() = lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
    fun format(): String = "%.5f, %.5f".format(lat, lon)
}

/**
 * Reads photo/video locations on the device. Android 10+ redacts GPS unless the app holds
 * ACCESS_MEDIA_LOCATION and opens the original file; nothing here uses the network.
 */
class RatioJurisLocationRepository(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val indexFile = File(context.filesDir, "places.tsv")
    private val mutex = Mutex()
    /** "id:dateModified" -> location (null = no location). */
    private val index = HashMap<String, RatioJurisLatLon?>()
    private var loaded = false

    fun ratioJurisHasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun ratioJurisLocationOf(photo: RatioJurisPhoto): RatioJurisLatLon? = withContext(Dispatchers.IO) {
        if (!ratioJurisHasPermission()) return@withContext null
        val original = runCatching { MediaStore.setRequireOriginal(photo.uri) }.getOrDefault(photo.uri)
        if (photo.isVideo) ratioJurisVideoLocation(original) else ratioJurisImageLocation(original)
    }

    private fun ratioJurisImageLocation(uri: Uri): RatioJurisLatLon? = runCatching {
        resolver.openInputStream(uri)?.use { s ->
            ExifInterface(s).latLong?.let { RatioJurisLatLon(it[0], it[1]) }?.takeIf { it.isValid }
        }
    }.getOrNull()

    /** Video location is ISO 6709, e.g. "+37.4219-122.0840/". */
    private fun ratioJurisVideoLocation(uri: Uri): RatioJurisLatLon? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val iso = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) ?: return@runCatching null
            val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(iso) ?: return@runCatching null
            RatioJurisLatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()).takeIf { it.isValid }
        } finally {
            r.release()
        }
    }.getOrNull()

    /**
     * Incrementally indexes locations for [photos] (cached on device by id + modification time)
     * and returns the geotagged ones. Safe to call repeatedly.
     */
    suspend fun ratioJurisIndex(photos: List<RatioJurisPhoto>, onProgress: (Map<Long, RatioJurisLatLon>) -> Unit = {}): Map<Long, RatioJurisLatLon> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!loaded) {
                    runCatching {
                        indexFile.forEachLine { line ->
                            val p = line.split('\t')
                            if (p.size == 3) index[p[0]] = if (p[1] == "-") null else RatioJurisLatLon(p[1].toDouble(), p[2].toDouble())
                        }
                    }
                    loaded = true
                }
                if (!ratioJurisHasPermission()) return@withContext emptyMap()
                var changed = false
                photos.forEachIndexed { i, photo ->
                    val key = "${photo.id}:${photo.dateModifiedMillis}"
                    if (!index.containsKey(key)) {
                        index[key] = ratioJurisLocationOf(photo)
                        changed = true
                        if (i % 200 == 199) onProgress(ratioJurisResolve(photos))
                    }
                }
                if (changed) {
                    val live = photos.mapTo(HashSet()) { "${it.id}:${it.dateModifiedMillis}" }
                    index.keys.retainAll(live)
                    runCatching {
                        indexFile.bufferedWriter().use { w ->
                            index.forEach { (k, v) -> w.write(if (v == null) "$k\t-\t-\n" else "$k\t${v.lat}\t${v.lon}\n") }
                        }
                    }
                }
                ratioJurisResolve(photos)
            }
        }

    private fun ratioJurisResolve(photos: List<RatioJurisPhoto>): Map<Long, RatioJurisLatLon> =
        photos.mapNotNull { p -> index["${p.id}:${p.dateModifiedMillis}"]?.let { p.id to it } }.toMap()

    /** Removes the cached index (used when location is turned off). */
    suspend fun ratioJurisClear() = mutex.withLock {
        withContext(Dispatchers.IO) { index.clear(); loaded = true; indexFile.delete() }
    }
}

/**
 * OpenStreetMap raster tiles, used only when the user has turned Maps on.
 * Follows the OSMF tile usage policy: identifying User-Agent, at most 2 connections, local caching,
 * and visible attribution in the UI.
 */
class RatioJurisTileCache(private val context: Context) {
    private val memory = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val dir = File(context.cacheDir, "tiles")
    private val connections = Semaphore(2)

    fun ratioJurisCached(z: Int, x: Int, y: Int): ImageBitmap? = memory.get("$z/$x/$y")

    suspend fun ratioJurisTile(z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$z/$x/$y"
        memory.get(key)?.let { return@withContext it }
        val file = File(dir, "$z/$x/$y.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
        val bytes = if (fresh) file.readBytes() else connections.withPermit { ratioJurisDownload(z, x, y) }?.also {
            file.parentFile?.mkdirs(); file.writeBytes(it)
        } ?: if (file.exists()) file.readBytes() else null
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }?.also { memory.put(key, it) }
    }

    private fun ratioJurisDownload(z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val c = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Keeps the disk cache bounded. */
    fun ratioJurisTrim(maxBytes: Long = 60L * 1024 * 1024) {
        val files = dir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        while (total > maxBytes && files.isNotEmpty()) {
            val f = files.removeAt(0); total -= f.length(); f.delete()
        }
    }

    fun ratioJurisClear() { memory.evictAll(); dir.deleteRecursively() }

    companion object {
        const val USER_AGENT = "RatioJurisPhotos/1.0 (+https://github.com/ratiojuris/photos)"
        private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    }
}
