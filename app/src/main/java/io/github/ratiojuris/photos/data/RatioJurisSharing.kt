// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.phtx.RatioJurisMetaKeys
import io.github.ratiojuris.photos.phtx.RatioJurisDecryptedImage
import io.github.ratiojuris.photos.phtx.RatioJurisTempJanitor
import io.github.ratiojuris.photos.ui.RatioJurisOpenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Re-shares an image this app was given (e.g. opened from another app). */
fun Context.ratioJurisShareUri(uri: Uri, mime: String = "image/*") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, getString(R.string.share_title)).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this@ratioJurisShareUri, RatioJurisOpenActivity::class.java)))
        if (this@ratioJurisShareUri !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

/**
 * Sharing. Protected photos are shared as the encrypted .phtx file by default; thumbnails and
 * decrypted originals are only exported on explicit request, as short-lived FileProvider files.
 */
class RatioJurisSharing(
    private val context: Context,
    val exportJanitor: RatioJurisTempJanitor,
    private val sharedJanitor: RatioJurisTempJanitor,
) {
    private val authority = "${context.packageName}.files"

    fun ratioJurisSharePhotos(activity: Context, items: List<RatioJurisPhoto>) {
        if (items.isEmpty()) return
        val mime = items.map { it.mimeType ?: "image/*" }.distinct().singleOrNull() ?: "image/*"
        start(activity, items.map { it.uri }, mime)
    }

    /** Shares the encrypted container itself. Nothing is decrypted. */
    fun ratioJurisShareProtected(activity: Context, items: List<RatioJurisProtectedPhoto>) {
        if (items.isEmpty()) return
        start(activity, items.map { it.uri }, "application/octet-stream")
    }

    fun ratioJurisShareProtectedUri(activity: Context, uri: Uri) = start(activity, listOf(uri), "application/octet-stream")

    /** Selection sharing: photos as images, protected photos as their encrypted files. */
    fun ratioJurisShareItems(activity: Context, items: List<RatioJurisGalleryItem>) {
        val photos = items.filterIsInstance<RatioJurisPhoto>()
        val locked = items.filterIsInstance<RatioJurisProtectedPhoto>()
        when {
            locked.isEmpty() -> ratioJurisSharePhotos(activity, photos)
            photos.isEmpty() -> ratioJurisShareProtected(activity, locked)
            else -> start(activity, items.map { it.uri }, "*/*")
        }
    }

    /** Shares only the public thumbnail as a JPEG. Does not need or use the password. */
    suspend fun ratioJurisShareThumbnail(activity: Context, thumbnail: ByteArray, baseName: String) {
        val file = withContext(Dispatchers.IO) {
            sharedJanitor.ratioJurisSweepExpired()
            sharedJanitor.ratioJurisNewFile("${baseName.substringBeforeLast('.')}_thumbnail.jpg").apply { writeBytes(thumbnail) }
        }
        start(activity, listOf(fileUri(file)), "image/jpeg")
    }

    /** Explicit, authenticated export of the original as a temporary plaintext file. */
    suspend fun ratioJurisShareOriginal(activity: Context, decrypted: RatioJurisDecryptedImage) {
        val meta = decrypted.privateMetadata
        val name = meta[RatioJurisMetaKeys.ORIGINAL_NAME] ?: "photo.jpg"
        val mime = meta[RatioJurisMetaKeys.ORIGINAL_MIME]?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val file = withContext(Dispatchers.IO) {
            exportJanitor.ratioJurisSweepExpired()
            exportJanitor.ratioJurisNewFile(name).apply { writeBytes(decrypted.image) }
        }
        start(activity, listOf(fileUri(file)), mime)
    }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    private fun start(activity: Context, uris: List<Uri>, mime: String) {
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = mime
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, activity.getString(R.string.share_title)).apply {
            // Avoid offering Photos itself as a share target for its own content.
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, RatioJurisOpenActivity::class.java)))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (activity !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(chooser)
    }
}
