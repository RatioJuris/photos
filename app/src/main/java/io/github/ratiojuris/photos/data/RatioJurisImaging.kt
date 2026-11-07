// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Size
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

class RatioJurisThumbnail(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

/** Result of decoding the header of an original. */
class RatioJurisImageInfo(val width: Int, val height: Int, val mimeType: String?)

/**
 * Bounded image decoding through ImageDecoder (applies EXIF orientation, supports
 * JPEG/PNG/WebP/HEIF/AVIF/GIF/DNG as provided by the platform) and thumbnail encoding.
 * EXIF is never copied into thumbnails: they are re-encoded from pixels.
 */
object RatioJurisImaging {
    const val STANDARD_THUMB_MAX = 480
    private const val REDUCED_SAMPLE_MAX = 14
    private const val REDUCED_OUTPUT_MAX = 192
    private const val NEUTRAL_SIZE = 256

    /** Decodes [bytes] so neither dimension exceeds [maxDim]. */
    fun ratioJurisDecodeBounded(bytes: ByteArray, maxDim: Int, software: Boolean = false): Pair<Bitmap, RatioJurisImageInfo> =
        decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxDim, software)

    fun ratioJurisDecodeBounded(resolver: ContentResolver, uri: Uri, maxDim: Int, software: Boolean = false): Pair<Bitmap, RatioJurisImageInfo> =
        decode(ImageDecoder.createSource(resolver, uri), maxDim, software)

    private fun decode(source: ImageDecoder.Source, maxDim: Int, software: Boolean): Pair<Bitmap, RatioJurisImageInfo> {
        var info: RatioJurisImageInfo? = null
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
            val w = imageInfo.size.width
            val h = imageInfo.size.height
            info = RatioJurisImageInfo(w, h, imageInfo.mimeType)
            val target = ratioJurisFit(Size(w, h), maxDim)
            if (target.width < w || target.height < h) decoder.setTargetSize(target.width, target.height)
            if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
        return bitmap to (info ?: RatioJurisImageInfo(bitmap.width, bitmap.height, null))
    }

    fun ratioJurisFit(size: Size, maxDim: Int): Size {
        val longest = max(size.width, size.height)
        if (longest <= maxDim || longest <= 0) return size
        val scale = maxDim.toFloat() / longest
        return Size(max(1, (size.width * scale).roundToInt()), max(1, (size.height * scale).roundToInt()))
    }

    /** Builds the public thumbnail for [policy]; returns null for [RatioJurisThumbnailPolicy.NONE]. */
    fun ratioJurisMakeThumbnail(original: ByteArray, policy: RatioJurisThumbnailPolicy): Pair<RatioJurisThumbnail?, RatioJurisImageInfo> {
        val (bitmap, info) = ratioJurisDecodeBounded(original, STANDARD_THUMB_MAX, software = true)
        try {
            val thumb = when (policy) {
                RatioJurisThumbnailPolicy.STANDARD -> encode(bitmap, 82)
                RatioJurisThumbnailPolicy.REDUCED -> {
                    val small = ratioJurisFit(Size(bitmap.width, bitmap.height), REDUCED_SAMPLE_MAX)
                    val tiny = Bitmap.createScaledBitmap(bitmap, small.width, small.height, true)
                    val out = ratioJurisFit(Size(bitmap.width, bitmap.height), REDUCED_OUTPUT_MAX)
                    val blurred = Bitmap.createScaledBitmap(tiny, out.width, out.height, true)
                    tiny.recycle()
                    encode(blurred, 70).also { blurred.recycle() }
                }
                RatioJurisThumbnailPolicy.NEUTRAL -> ratioJurisNeutralTile().let { tile -> encode(tile, 85).also { tile.recycle() } }
                RatioJurisThumbnailPolicy.NONE -> null
            }
            return thumb to info
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap, quality: Int): RatioJurisThumbnail {
        val out = ByteArrayOutputStream(32 * 1024)
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Thumbnail encoding failed" }
        return RatioJurisThumbnail(out.toByteArray(), bitmap.width, bitmap.height)
    }

    /** A neutral artwork tile that reveals nothing about the photograph (and nothing about protection). */
    fun ratioJurisNeutralTile(): Bitmap {
        val s = NEUTRAL_SIZE.toFloat()
        val bmp = Bitmap.createBitmap(NEUTRAL_SIZE, NEUTRAL_SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, s, s, 0xFFE9E4DC.toInt(), 0xFFC9C2B8.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, s, s, bg)
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        c.drawCircle(s * 0.30f, s * 0.32f, s * 0.22f, soft)
        c.drawCircle(s * 0.70f, s * 0.68f, s * 0.30f, soft)
        return bmp
    }

    fun ratioJurisNeutralTileJpeg(): ByteArray {
        val tile = ratioJurisNeutralTile()
        return try { encode(tile, 85).bytes } finally { tile.recycle() }
    }
}