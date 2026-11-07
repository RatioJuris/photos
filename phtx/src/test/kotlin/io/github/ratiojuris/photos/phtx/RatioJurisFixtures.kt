// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.imageio.ImageIO
import kotlin.random.Random

/** Deterministic fixtures. Test-only KDF cost keeps the suite fast; production uses DEFAULT. */
object RatioJurisFixtures {
    val TEST_KDF = RatioJurisKdfParams(memoryKiB = RatioJurisPhtxFormat.MIN_KDF_MEMORY_KIB, iterations = 1, parallelism = 1)
    val PASSWORD = "correct horse battery staple".toCharArray()

    fun ratioJurisCipher(maxImageSize: Long = RatioJurisPhtxCipher.DEFAULT_MAX_IMAGE_SIZE) =
        RatioJurisPhtxCipher(TEST_KDF, SecureRandom(), maxImageSize = maxImageSize)

    /** A real PNG photograph-like image (gradient + noise), deterministic for a given seed. */
    fun ratioJurisPng(width: Int = 640, height: Int = 480, seed: Int = 7): ByteArray {
        val rnd = Random(seed)
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / width + rnd.nextInt(16)).coerceIn(0, 255)
            val g = (y * 255 / height + rnd.nextInt(16)).coerceIn(0, 255)
            val b = ((x + y) % 256)
            img.setRGB(x, y, Color(r, g, b).rgb)
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    fun ratioJurisThumbnail(source: ByteArray, maxDim: Int = 64): ByteArray {
        val src = ImageIO.read(source.inputStream())
        val scale = maxDim.toDouble() / maxOf(src.width, src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        dst.createGraphics().apply { drawImage(src, 0, 0, w, h, null); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(dst, "jpg", it) }.toByteArray()
    }

    fun ratioJurisRequest(image: ByteArray = ratioJurisPng(), withThumb: Boolean = true) = RatioJurisProtectRequest(
        image = image,
        privateMetadata = mapOf(RatioJurisMetaKeys.ORIGINAL_NAME to "IMG_0001.png", RatioJurisMetaKeys.ORIGINAL_MIME to "image/png"),
        publicMetadata = mapOf(RatioJurisMetaKeys.THUMB_MIME to "image/jpeg", RatioJurisMetaKeys.THUMB_POLICY to "standard"),
        thumbnail = if (withThumb) ratioJurisThumbnail(image) else null,
    )

    fun ratioJurisProtectToBytes(
        cipher: RatioJurisPhtxCipher = ratioJurisCipher(),
        request: RatioJurisProtectRequest = ratioJurisRequest(),
        password: CharArray = PASSWORD,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        cipher.ratioJurisProtect(out, request, password).verificationKey.close()
        return out.toByteArray()
    }
}
