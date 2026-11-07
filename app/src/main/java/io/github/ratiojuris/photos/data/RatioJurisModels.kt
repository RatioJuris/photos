// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.net.Uri
import androidx.compose.runtime.Immutable

/** An item in the gallery: either an ordinary photo or a protected (.phtx) photo. */
@Immutable
sealed interface RatioJurisGalleryItem {
    val key: String
    val uri: Uri
    val displayName: String
    /** Best known timestamp for ordering and grouping (epoch millis). */
    val dateMillis: Long
    val sizeBytes: Long
    val albumName: String?
}

@Immutable
data class RatioJurisPhoto(
    val id: Long,
    override val uri: Uri,
    override val displayName: String,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String?,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val dateTakenMillis: Long?,
    val dateModifiedMillis: Long,
    override val sizeBytes: Long,
    /** Videos are shown and played, but never protected (encryption is for photos only). */
    val isVideo: Boolean = false,
    val durationMs: Long = 0,
) : RatioJurisGalleryItem {
    override val key: String get() = "m:$id"

    /** Only still photos can be protected. */
    val canProtect: Boolean get() = !isVideo
    override val dateMillis: Long get() = dateTakenMillis ?: dateModifiedMillis
    override val albumName: String get() = bucketName

    /** Display dimensions with EXIF/MediaStore rotation applied. */
    val orientedWidth: Int get() = if (orientation % 180 != 0) height else width
    val orientedHeight: Int get() = if (orientation % 180 != 0) width else height
}

@Immutable
data class RatioJurisProtectedPhoto(
    val id: Long,
    override val uri: Uri,
    override val displayName: String,
    override val sizeBytes: Long,
    override val dateMillis: Long,
    val dateModifiedMillis: Long,
    /** True for carrier images (an ordinary JPEG preview with the encrypted block appended); false for bare .phtx. */
    val imageBacked: Boolean = false,
) : RatioJurisGalleryItem {
    override val key: String get() = "p:$id"
    override val albumName: String? get() = null
}

@Immutable
data class RatioJurisAlbum(
    val bucketId: String,
    val name: String,
    val count: Int,
    val cover: RatioJurisPhoto,
    val relativePath: String?,
)

/** Media read access granted by the user. */
enum class RatioJurisMediaAccess { FULL, PARTIAL, DENIED }

/** Coil request model for MediaStore-generated thumbnails (fast, oriented, cached by the system). */
data class RatioJurisMediaThumb(val uri: Uri, val dateModified: Long)

/** Coil request model for the public thumbnail embedded in a protected file. */
data class RatioJurisProtectedThumb(val uri: Uri, val dateModified: Long)

/** An item in the system Bin (MediaStore trash, Android 11+). */
@Immutable
data class RatioJurisBinItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    /** When the system will erase it for good (epoch millis), or 0 if unknown. */
    val expiresMillis: Long,
    val dateModifiedMillis: Long,
) {
    val key: String get() = "b:$id"
}