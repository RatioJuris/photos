// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** Reads the device photo library through MediaStore. Never fabricates entries or metadata. */
class RatioJurisMediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    val imagesCollection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val videosCollection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Video access is a separate runtime permission on Android 13+. */
    fun ratioJurisCanReadVideos(): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun ratioJurisCurrentAccess(): RatioJurisMediaAccess {
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> RatioJurisMediaAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> RatioJurisMediaAccess.PARTIAL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> RatioJurisMediaAccess.FULL
            else -> RatioJurisMediaAccess.DENIED
        }
    }

    /** Emits Unit once immediately and again whenever MediaStore reports a change. */
    fun ratioJurisChanges(vararg uris: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        uris.forEach { resolver.registerContentObserver(it, true, observer) }
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    /** Photos and videos, newest first by modification time. */
    suspend fun ratioJurisLoadPhotos(): List<RatioJurisPhoto> = withContext(Dispatchers.IO) {
        if (ratioJurisCurrentAccess() == RatioJurisMediaAccess.DENIED) return@withContext emptyList()
        val images = ratioJurisQuery(imagesCollection, video = false)
        val videos = if (ratioJurisCanReadVideos()) ratioJurisQuery(videosCollection, video = true) else emptyList()
        images + videos
    }

    private fun ratioJurisQuery(collection: Uri, video: Boolean): List<RatioJurisPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            if (video) MediaStore.Video.Media.DURATION else MediaStore.Images.Media.SIZE,
        )
        val result = ArrayList<RatioJurisPhoto>(512)
        try {
            resolver.query(
                collection,
                projection,
                "${MediaStore.Images.Media.SIZE} > 0",
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketId = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val rel = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val mime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val orientation = c.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
                val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val modified = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val size = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val durationIdx = projection.lastIndex
                while (c.moveToNext()) {
                    val itemId = c.getLong(id)
                    result += RatioJurisPhoto(
                        id = itemId,
                        uri = ContentUris.withAppendedId(collection, itemId),
                        displayName = c.getString(name) ?: "",
                        bucketId = c.getString(bucketId) ?: "",
                        bucketName = c.getString(bucket) ?: "",
                        relativePath = c.getString(rel),
                        mimeType = c.getString(mime),
                        width = c.getInt(w),
                        height = c.getInt(h),
                        orientation = c.getInt(orientation),
                        dateTakenMillis = if (c.isNull(taken)) null else c.getLong(taken).takeIf { it > 0 },
                        dateModifiedMillis = c.getLong(modified) * 1000L,
                        sizeBytes = c.getLong(size),
                        isVideo = video,
                        durationMs = if (video) c.getLong(durationIdx) else 0L,
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Media access revoked while querying")
            return emptyList()
        }
        return result
    }

    fun ratioJurisAlbums(photos: List<RatioJurisPhoto>): List<RatioJurisAlbum> =
        photos.groupBy { it.bucketId }
            .map { (bucketId, items) ->
                val newest = items.maxBy { it.dateMillis }
                RatioJurisAlbum(bucketId, newest.bucketName.ifBlank { "Device" }, items.size, newest, newest.relativePath)
            }
            .sortedWith(compareByDescending<RatioJurisAlbum> { ratioJurisAlbumRank(it) }.thenByDescending { it.cover.dateMillis })

    /** Camera first, then screenshots; everything else by recency. */
    private fun ratioJurisAlbumRank(album: RatioJurisAlbum): Int {
        val path = (album.relativePath ?: "").lowercase()
        return when {
            path.startsWith("dcim/camera") -> 2
            path.contains("screenshot") -> 1
            else -> 0
        }
    }

    /** True when the platform Bin (MediaStore trash) exists: Android 11+. */
    val ratioJurisBinSupported: Boolean get() = Build.VERSION.SDK_INT >= 30

    /** Items currently in the system Bin. Empty on Android 10. */
    suspend fun ratioJurisLoadBin(): List<RatioJurisBinItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val out = ArrayList<RatioJurisBinItem>()
        val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        for ((collection, video) in listOf(imagesCollection to false, videosCollection to true, downloads to false)) {
            val args = android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                if (collection == downloads) {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%.phtx"))
                }
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_EXPIRES))
            }
            runCatching {
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_EXPIRES, MediaStore.MediaColumns.DATE_MODIFIED),
                    args, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        out += RatioJurisBinItem(
                            id, ContentUris.withAppendedId(collection, id), c.getString(1) ?: "", video,
                            if (c.isNull(2)) 0L else c.getLong(2) * 1000L, c.getLong(3) * 1000L,
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Could not read the Bin: ${it.javaClass.simpleName}") }
        }
        out.sortedBy { it.expiresMillis }
    }
    companion object {
        private const val TAG = "PhotosMedia"
    }
}
