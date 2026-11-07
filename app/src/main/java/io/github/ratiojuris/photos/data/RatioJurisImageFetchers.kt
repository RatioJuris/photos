// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.content.Context
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxEnvelope
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import java.io.BufferedInputStream
import java.io.FileNotFoundException

/** Grid thumbnails from MediaStore's own thumbnail cache (oriented, fast, no full decode). */
class RatioJurisMediaThumbFetcher(
    private val context: Context,
    private val data: RatioJurisMediaThumb,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val w = options.size.width.pxOrElse { DEFAULT_PX }.coerceIn(64, MAX_PX)
        val h = options.size.height.pxOrElse { DEFAULT_PX }.coerceIn(64, MAX_PX)
        val bitmap = context.contentResolver.loadThumbnail(data.uri, Size(w, h), null)
        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<RatioJurisMediaThumb> {
        override fun create(data: RatioJurisMediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            RatioJurisMediaThumbFetcher(context, data, options)
    }

    class Key : Keyer<RatioJurisMediaThumb> {
        override fun key(data: RatioJurisMediaThumb, options: Options) = "mt:${data.uri}:${data.dateModified}"
    }

    companion object {
        private const val DEFAULT_PX = 384
        private const val MAX_PX = 1024
    }
}

/** Reads only the public header of a protected file and returns its embedded thumbnail. The payload is never read. */
class RatioJurisProtectedThumbFetcher(
    private val context: Context,
    private val data: RatioJurisProtectedThumb,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val header = context.contentResolver.openFileDescriptor(data.uri, "r")?.let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { s -> RatioJurisPhtxEnvelope.ratioJurisReadHeader(s.channel) }
        } ?: context.contentResolver.openInputStream(data.uri)?.use { RatioJurisPhtxReader.readHeader(BufferedInputStream(it)) }
            ?: throw FileNotFoundException("Unavailable")
        val thumb = header.thumbnail ?: throw RatioJurisNoThumbnailException()
        SourceFetchResult(
            source = ImageSource(source = Buffer().write(thumb), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<RatioJurisProtectedThumb> {
        override fun create(data: RatioJurisProtectedThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            RatioJurisProtectedThumbFetcher(context, data)
    }

    class Key : Keyer<RatioJurisProtectedThumb> {
        override fun key(data: RatioJurisProtectedThumb, options: Options) = "pt:${data.uri}:${data.dateModified}"
    }
}

class RatioJurisNoThumbnailException : Exception("Protected photo has no thumbnail")

object RatioJurisImageFetchersRegistry {
    fun ratioJurisRegister(builder: coil3.ComponentRegistry.Builder, context: Context) {
        builder.add(RatioJurisMediaThumbFetcher.Factory(context))
        builder.add(RatioJurisMediaThumbFetcher.Key())
        builder.add(RatioJurisProtectedThumbFetcher.Factory(context))
        builder.add(RatioJurisProtectedThumbFetcher.Key())
    }
}
