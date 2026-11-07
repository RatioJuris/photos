// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import io.github.ratiojuris.photos.phtx.RatioJurisByteArrayChannel
import io.github.ratiojuris.photos.phtx.RatioJurisDecryptedImage
import io.github.ratiojuris.photos.phtx.RatioJurisMetaKeys
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxCipher
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxContainer
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxEnvelope
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxException
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxFormat
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxHeader
import io.github.ratiojuris.photos.phtx.RatioJurisProtectRequest
import io.github.ratiojuris.photos.phtx.RatioJurisProtectionOutcome
import io.github.ratiojuris.photos.phtx.RatioJurisProtectionPipeline
import io.github.ratiojuris.photos.phtx.RatioJurisStagedOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What an arbitrary incoming file turned out to be, determined from its content, never its name. */
sealed interface RatioJurisDetection {
    data class Protected(val header: RatioJurisPhtxHeader, val displayName: String?, val size: Long?) : RatioJurisDetection
    data class Ordinary(val info: RatioJurisImageInfo, val displayName: String?, val size: Long?) : RatioJurisDetection
    data class Unsupported(val reason: String, val displayName: String?) : RatioJurisDetection
}

/**
 * Storage of protected photos.
 *
 * New protected photos are "carrier" JPEGs in Pictures/Photos: an ordinary JPEG of the public preview
 * with the encrypted PHTX block appended (see RatioJurisPhtxEnvelope). Other apps show only the preview.
 * Bare .phtx files (earlier builds, or received from elsewhere) in Download/Photos Protected stay supported.
 */
class RatioJurisVaultRepository(
    private val context: Context,
    val cipher: RatioJurisPhtxCipher,
) {
    private val resolver get() = context.contentResolver
    private val pipeline = RatioJurisProtectionPipeline(cipher)
    val downloadsCollection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val imagesCollection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val stagingDir: File get() = File(context.noBackupFilesDir, "staging").apply { mkdirs() }

    /** id:dateModified -> is a carrier. Avoids re-reading trailers on every refresh. */
    private val carrierCache = ConcurrentHashMap<String, Boolean>()

    // ---------------------------------------------------------------- listing

    /** All protected photos: verified carrier images in Pictures/Photos plus bare .phtx files in Downloads. */
    suspend fun ratioJurisLoadProtected(): List<RatioJurisProtectedPhoto> = withContext(Dispatchers.IO) {
        ratioJurisLoadCarriers() + ratioJurisLoadBare()
    }

    private fun ratioJurisLoadCarriers(): List<RatioJurisProtectedPhoto> {
        val list = ArrayList<RatioJurisProtectedPhoto>()
        runCatching {
            resolver.query(
                imagesCollection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?", arrayOf(CARRIER_RELATIVE_PATH),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val modified = c.getLong(4) * 1000L
                    val uri = ContentUris.withAppendedId(imagesCollection, id)
                    val isCarrier = carrierCache.getOrPut("$id:$modified") {
                        runCatching { ratioJurisWithChannel(uri) { RatioJurisPhtxEnvelope.ratioJurisLocate(it) != null } }.getOrDefault(false)
                    }
                    if (isCarrier) {
                        list += RatioJurisProtectedPhoto(id, uri, c.getString(1) ?: "", c.getLong(2), c.getLong(3) * 1000L, modified, imageBacked = true)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Could not list carriers: ${it.javaClass.simpleName}") }
        return list
    }

    private fun ratioJurisLoadBare(): List<RatioJurisProtectedPhoto> {
        val list = ArrayList<RatioJurisProtectedPhoto>()
        runCatching {
            resolver.query(
                downloadsCollection,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_ADDED, MediaStore.Downloads.DATE_MODIFIED),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("%.${RatioJurisPhtxFormat.FILE_EXTENSION}"),
                "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    list += RatioJurisProtectedPhoto(
                        id, ContentUris.withAppendedId(downloadsCollection, id), c.getString(1) ?: "",
                        c.getLong(2), c.getLong(3) * 1000L, c.getLong(4) * 1000L, imageBacked = false,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Could not list protected files: ${it.javaClass.simpleName}") }
        return list
    }

    // ---------------------------------------------------------------- inspection

    suspend fun ratioJurisReadHeader(uri: Uri): RatioJurisPhtxHeader = withContext(Dispatchers.IO) {
        ratioJurisWithChannel(uri) { RatioJurisPhtxEnvelope.ratioJurisReadHeader(it, cipher.maxPayloadSize) }
    }

    /** Classifies [uri] by content: PHTX block (bare or carrier) first, then platform image decoding. */
    suspend fun ratioJurisDetect(uri: Uri): RatioJurisDetection = withContext(Dispatchers.IO) {
        val name = ratioJurisDisplayName(uri)
        val size = ratioJurisSizeOf(uri)
        val located = try {
            ratioJurisWithChannel(uri) { ch -> if (ch.size() == 0L) null else RatioJurisPhtxEnvelope.ratioJurisLocate(ch) }
        } catch (e: Exception) {
            return@withContext RatioJurisDetection.Unsupported("unreadable", name)
        }
        if (located != null) {
            return@withContext try {
                RatioJurisDetection.Protected(ratioJurisReadHeader(uri), name, size)
            } catch (e: RatioJurisPhtxException.Unsupported) {
                RatioJurisDetection.Unsupported("version", name)
            } catch (e: RatioJurisPhtxException) {
                RatioJurisDetection.Unsupported("damaged", name)
            }
        }
        val type = runCatching { resolver.getType(uri) }.getOrNull()
        if (type != null && type.startsWith("video/")) return@withContext RatioJurisDetection.Ordinary(RatioJurisImageInfo(0, 0, type), name, size)
        try {
            val (bmp, info) = RatioJurisImaging.ratioJurisDecodeBounded(resolver, uri, 8, software = true)
            bmp.recycle()
            RatioJurisDetection.Ordinary(info, name, size)
        } catch (e: Exception) {
            RatioJurisDetection.Unsupported("format", name)
        }
    }

    // ---------------------------------------------------------------- protect

    /**
     * Protects one MediaStore photo. The original is never modified or deleted here;
     * the caller requests deletion only after [RatioJurisProtectionOutcome.Success].
     */
    suspend fun ratioJurisProtect(
        photo: RatioJurisPhoto,
        password: CharArray,
        policy: RatioJurisThumbnailPolicy,
    ): RatioJurisProtectionOutcome<Uri> = withContext(Dispatchers.Default) {
        try {
            val original = withContext(Dispatchers.IO) { ratioJurisReadAll(photo.uri, cipher.maxImageSize) }
            val (thumb, info) = RatioJurisImaging.ratioJurisMakeThumbnail(original, policy)
            val privateMeta = buildMap {
                put(RatioJurisMetaKeys.ORIGINAL_NAME, photo.displayName.take(255))
                (photo.mimeType ?: info.mimeType)?.let { put(RatioJurisMetaKeys.ORIGINAL_MIME, it) }
                put(RatioJurisMetaKeys.ORIGINAL_WIDTH, info.width.toString())
                put(RatioJurisMetaKeys.ORIGINAL_HEIGHT, info.height.toString())
                photo.dateTakenMillis?.let { put(RatioJurisMetaKeys.ORIGINAL_DATE_TAKEN, it.toString()) }
            }
            val publicMeta = buildMap {
                put(RatioJurisMetaKeys.CREATED_AT, System.currentTimeMillis().toString())
                put(RatioJurisMetaKeys.THUMB_POLICY, policy.wireName)
                if (thumb != null) {
                    put(RatioJurisMetaKeys.THUMB_MIME, "image/jpeg")
                    put(RatioJurisMetaKeys.THUMB_WIDTH, thumb.width.toString())
                    put(RatioJurisMetaKeys.THUMB_HEIGHT, thumb.height.toString())
                }
            }
            val request = RatioJurisProtectRequest(original, privateMeta, publicMeta, thumb?.bytes)
            try {
                pipeline.ratioJurisProtect(request, password, ratioJurisCarrierStaging(), carrier = ratioJurisCarrierFor(thumb?.bytes))
            } finally {
                original.fill(0)
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            RatioJurisProtectionOutcome.Failure(e)
        }
    }

    /** The image other apps will display: the preview, or neutral artwork when no preview is stored. */
    private fun ratioJurisCarrierFor(thumbnail: ByteArray?): ByteArray =
        thumbnail ?: RatioJurisImaging.ratioJurisNeutralTileJpeg()

    // ---------------------------------------------------------------- unlock

    suspend fun ratioJurisReadContainer(uri: Uri): RatioJurisPhtxContainer = withContext(Dispatchers.IO) {
        ratioJurisWithChannel(uri) { RatioJurisPhtxEnvelope.ratioJurisReadContainer(it, cipher.maxPayloadSize) }
    }

    /** Authenticated decryption. Throws [RatioJurisPhtxException.WrongPassword] or other [RatioJurisPhtxException]s. */
    suspend fun ratioJurisUnlock(uri: Uri, password: CharArray): RatioJurisDecryptedImage {
        val container = ratioJurisReadContainer(uri)
        return withContext(Dispatchers.Default) { cipher.ratioJurisOpen(container, password) }
    }

    // ---------------------------------------------------------------- password change

    /**
     * Authenticates [oldPassword], re-encrypts with fresh salt/keys/nonces under [newPassword],
     * verifies the new file, then removes the old one.
     */
    suspend fun ratioJurisChangePassword(item: RatioJurisProtectedPhoto, oldPassword: CharArray, newPassword: CharArray): Uri {
        val container = ratioJurisReadContainer(item.uri)
        val outcome = withContext(Dispatchers.Default) {
            val decrypted = cipher.ratioJurisOpen(container, oldPassword)
            try {
                val request = RatioJurisProtectRequest(
                    decrypted.image, decrypted.privateMetadata, container.header.publicMetadata, container.header.thumbnail,
                )
                pipeline.ratioJurisProtect(request, newPassword, ratioJurisCarrierStaging(), carrier = ratioJurisCarrierFor(container.header.thumbnail))
            } finally {
                decrypted.ratioJurisWipe()
            }
        }
        when (outcome) {
            is RatioJurisProtectionOutcome.Failure -> throw outcome.error
            is RatioJurisProtectionOutcome.Success -> {
                withContext(Dispatchers.IO) { runCatching { resolver.delete(item.uri, null, null) } }
                return outcome.location
            }
        }
    }

    // ---------------------------------------------------------------- import / restore / delete

    /** Copies a validated protected file (e.g. received by e-mail) into Photos, byte for byte. */
    suspend fun ratioJurisImport(source: Uri): Uri = withContext(Dispatchers.IO) {
        val location = ratioJurisWithChannel(source) { RatioJurisPhtxEnvelope.ratioJurisLocate(it) }
            ?: throw RatioJurisPhtxException.NotProtectedImage()
        ratioJurisReadContainer(source) // full structural validation
        val name = ratioJurisDisplayName(source)
        val staging = if (location.carrier) {
            ratioJurisCarrierStaging(name?.takeIf { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) })
        } else {
            ratioJurisBareStaging(name?.takeIf { it.endsWith(".${RatioJurisPhtxFormat.FILE_EXTENSION}", true) })
        }
        try {
            staging.openOutput().use { out -> resolver.openInputStream(source)!!.use { it.copyTo(out) } }
            staging.commit()
        } catch (e: Throwable) {
            staging.abort()
            throw e
        }
    }

    /** Writes an unprotected copy of a decrypted original into Pictures/Photos Restored. Explicit user action only. */
    suspend fun ratioJurisRestore(decrypted: RatioJurisDecryptedImage): Uri = withContext(Dispatchers.IO) {
        val meta = decrypted.privateMetadata
        val mime = meta[RatioJurisMetaKeys.ORIGINAL_MIME]?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val name = meta[RatioJurisMetaKeys.ORIGINAL_NAME]?.ratioJurisSafeFileName() ?: "Restored_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$RESTORED_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            meta[RatioJurisMetaKeys.ORIGINAL_DATE_TAKEN]?.toLongOrNull()?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
        }
        val uri = resolver.insert(imagesCollection, values) ?: error("Could not create file")
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(decrypted.image) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Deletes a protected file this app owns. Returns false when the system must ask the user (e.g. after reinstall). */
    suspend fun ratioJurisDelete(item: RatioJurisProtectedPhoto): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.delete(item.uri, null, null) > 0
        } catch (e: SecurityException) {
            false
        }
    }

    /** Removes our own unfinished (pending) entries left by an interrupted operation. */
    suspend fun ratioJurisCleanupStalePending(olderThanMillis: Long = 10 * 60_000L): Int = withContext(Dispatchers.IO) {
        var removed = 0
        runCatching {
            stagingDir.listFiles()?.forEach { f ->
                if (System.currentTimeMillis() - f.lastModified() > olderThanMillis && f.delete()) removed++
            }
            val cutoff = ((System.currentTimeMillis() - olderThanMillis) / 1000).toString()
            removed += ratioJurisDeletePending(downloadsCollection, "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("%.${RatioJurisPhtxFormat.FILE_EXTENSION}", cutoff))
            removed += ratioJurisDeletePending(imagesCollection, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(CARRIER_RELATIVE_PATH, cutoff))
        }
        removed
    }

    private fun ratioJurisDeletePending(collection: Uri, filter: String, args: Array<String>): Int {
        var removed = 0
        val selection = "${MediaStore.MediaColumns.IS_PENDING} = 1 AND $filter AND ${MediaStore.MediaColumns.DATE_ADDED} < ?"
        val cursor = if (Build.VERSION.SDK_INT >= 30) {
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }, null)
        } else {
            @Suppress("DEPRECATION")
            resolver.query(MediaStore.setIncludePending(collection), arrayOf(MediaStore.MediaColumns._ID), selection, args, null)
        }
        cursor?.use { c ->
            while (c.moveToNext()) {
                if (resolver.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null) > 0) removed++
            }
        }
        return removed
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Opens [uri] as a seekable channel (file descriptor) so the trailer can be read without loading the file.
     * Falls back to a bounded in-memory copy for providers that only offer streams/pipes.
     */
    fun <T> ratioJurisWithChannel(uri: Uri, block: (SeekableByteChannel) -> T): T {
        val pfd = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                val channel = stream.channel
                val seekable = runCatching { channel.size() > 0 && channel.position(0) != null }.getOrDefault(false)
                if (seekable) return block(channel)
            }
        }
        val bytes = ratioJurisReadAll(uri, cipher.maxPayloadSize + RatioJurisPhtxFormat.MAX_HEADER_SIZE + 64L * 1024 * 1024)
        return block(RatioJurisByteArrayChannel(bytes))
    }

    fun ratioJurisDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    fun ratioJurisSizeOf(uri: Uri): Long? = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { len -> len >= 0 } }
    }.getOrNull()

    /** Reads at most [limit] bytes; fails rather than truncating. */
    fun ratioJurisReadAll(uri: Uri, limit: Long): ByteArray {
        val declared = ratioJurisSizeOf(uri)
        if (declared != null && declared > limit) throw RatioJurisPhtxException.TooLarge("image exceeds limit")
        (resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("Unavailable")).use { input ->
            val out = java.io.ByteArrayOutputStream(declared?.toInt()?.coerceAtLeast(1024) ?: (1 shl 20))
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw RatioJurisPhtxException.TooLarge("image exceeds limit")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** Ordinary-looking name: nothing in the file name says the photo is protected. */
    private fun ratioJurisNewName(extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "IMG_${stamp}_${UUID.randomUUID().toString().take(4)}.$extension"
    }

    private fun String.ratioJurisSafeFileName(): String =
        replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").take(120).ifBlank { "Restored.jpg" }

    private fun ratioJurisCarrierStaging(name: String? = null) = RatioJurisMediaStoreStaging(
        imagesCollection,
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name ?: ratioJurisNewName("jpg"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, CARRIER_RELATIVE_PATH.trimEnd('/'))
        },
    )

    private fun ratioJurisBareStaging(name: String? = null) = RatioJurisMediaStoreStaging(
        downloadsCollection,
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name ?: ratioJurisNewName(RatioJurisPhtxFormat.FILE_EXTENSION))
            put(MediaStore.Downloads.MIME_TYPE, BARE_STORE_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BARE_FOLDER")
        },
    )

    /**
     * Android staging: bytes go to an app-private temp file and are verified there by the pipeline, then
     * copied into a MediaStore entry that stays IS_PENDING (invisible to other apps) until the copy
     * hash-matches the verified bytes. Only then is it published.
     */
    inner class RatioJurisMediaStoreStaging(private val collection: Uri, private val values: ContentValues) : RatioJurisStagedOutput<Uri> {
        private val temp = File(stagingDir, "${UUID.randomUUID()}.partial")
        private var entry: Uri? = null

        override fun openOutput(): OutputStream = temp.outputStream()
        override fun openInput(): InputStream = temp.inputStream()
        override fun stagedSize(): Long = temp.length()

        override fun commit(): Uri {
            val uri = resolver.insert(collection, ContentValues(values).apply { put(MediaStore.MediaColumns.IS_PENDING, 1) })
                ?: throw IllegalStateException("Could not create file")
            entry = uri
            val stagedHash = sha256(temp.inputStream())
            resolver.openOutputStream(uri, "w")!!.use { out -> temp.inputStream().use { it.copyTo(out, 64 * 1024) } }
            val writtenHash = sha256(resolver.openInputStream(uri)!!)
            if (!MessageDigest.isEqual(stagedHash, writtenHash)) throw IllegalStateException("Copy verification failed")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            temp.delete()
            return uri
        }

        override fun abort() {
            temp.delete()
            entry?.let { runCatching { resolver.delete(it, null, null) } }
        }

        private fun sha256(input: InputStream): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            DigestInputStream(input, md).use { s -> val b = ByteArray(64 * 1024); while (s.read(b) >= 0) Unit }
            return md.digest()
        }
    }

    companion object {
        private const val TAG = "PhotosVault"
        /** Where carrier images live. An innocuous folder name on purpose. */
        const val CARRIER_FOLDER = "Photos"
        const val CARRIER_RELATIVE_PATH = "Pictures/$CARRIER_FOLDER/"
        const val BARE_FOLDER = "Photos Protected"
        const val RESTORED_FOLDER = "Photos Restored"
        /** Bare .phtx files are stored as generic binary so MediaStore keeps the extension on every Android version. */
        const val BARE_STORE_MIME = "application/octet-stream"
    }
}
