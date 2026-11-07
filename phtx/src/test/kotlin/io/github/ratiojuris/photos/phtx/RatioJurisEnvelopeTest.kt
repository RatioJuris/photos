// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.PASSWORD
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisCipher
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisPng
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import javax.imageio.ImageIO

class RatioJurisEnvelopeTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cipher = ratioJurisCipher()
    private val pipeline = RatioJurisProtectionPipeline(cipher)

    private fun protectWithCarrier(name: String = "IMG_0001.jpg"): Triple<File, ByteArray, RatioJurisProtectRequest> {
        val image = ratioJurisPng()
        val request = ratioJurisRequest(image)
        val target = File(tmp.root, name)
        val outcome = pipeline.ratioJurisProtect(request, PASSWORD, RatioJurisFileStaging(target), carrier = request.thumbnail)
        assertTrue(outcome is RatioJurisProtectionOutcome.Success)
        return Triple(target, image, request)
    }

    @Test fun carrierFileIsAnOrdinaryImageShowingOnlyThePreview() {
        val (file, image, request) = protectWithCarrier()
        // What any other app's image decoder sees: the preview, nothing else.
        val decoded = ImageIO.read(file)
        assertNotNull("carrier must decode as a normal image", decoded)
        val preview = ImageIO.read(request.thumbnail!!.inputStream())
        assertEquals(preview.width, decoded.width)
        assertEquals(preview.height, decoded.height)
        assertTrue(decoded.width < 100 && image.size > file.length() / 50) // preview is small; original is not exposed
        val bytes = file.readBytes()
        assertTrue(bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) // JPEG SOI
    }

    @Test fun locatesAndDecryptsBlockInsideCarrier() {
        val (file, image, request) = protectWithCarrier()
        RandomAccessFile(file, "r").channel.use { ch ->
            val loc = RatioJurisPhtxEnvelope.ratioJurisLocate(ch)!!
            assertTrue(loc.carrier)
            assertEquals(request.thumbnail!!.size.toLong(), loc.offset)
            val header = RatioJurisPhtxEnvelope.ratioJurisReadHeader(ch)
            assertArrayEquals(request.thumbnail, header.thumbnail)
            val container = RatioJurisPhtxEnvelope.ratioJurisReadContainer(ch, cipher.maxPayloadSize)
            assertArrayEquals(image, cipher.ratioJurisOpen(container, PASSWORD).image)
            val wrong = RatioJurisPhtxEnvelope.ratioJurisReadContainer(ch, cipher.maxPayloadSize)
            try {
                cipher.ratioJurisOpen(wrong, "nope-nope".toCharArray()); throw AssertionError("accepted wrong password")
            } catch (e: RatioJurisPhtxException.WrongPassword) { /* expected */ }
        }
    }

    @Test fun barePhtxIsStillRecognised() {
        val bytes = RatioJurisFixtures.ratioJurisProtectToBytes()
        val loc = RatioJurisPhtxEnvelope.ratioJurisLocate(RatioJurisByteArrayChannel(bytes))!!
        assertFalse(loc.carrier)
        assertEquals(0L, loc.offset)
    }

    @Test fun ordinaryImagesAreNotMistakenForProtected() {
        assertNull(RatioJurisPhtxEnvelope.ratioJurisLocate(RatioJurisByteArrayChannel(ratioJurisPng())))
        val jpeg = RatioJurisFixtures.ratioJurisThumbnail(ratioJurisPng())
        assertNull(RatioJurisPhtxEnvelope.ratioJurisLocate(RatioJurisByteArrayChannel(jpeg)))
        // A JPEG that merely ends with the trailer magic but has inconsistent offsets is rejected.
        val fake = jpeg + RatioJurisPhtxEnvelope.ratioJurisTrailer(2, 999_999)
        assertNull(RatioJurisPhtxEnvelope.ratioJurisLocate(RatioJurisByteArrayChannel(fake)))
        val fake2 = jpeg + RatioJurisPhtxEnvelope.ratioJurisTrailer(jpeg.size.toLong() - 40, 40)
        assertNull(RatioJurisPhtxEnvelope.ratioJurisLocate(RatioJurisByteArrayChannel(fake2)))
    }

    @Test fun truncatedOrTamperedCarrierFilesAreRejected() {
        val (file, _, _) = protectWithCarrier()
        val bytes = file.readBytes()
        // Lost trailer (e.g. an app re-saved only the JPEG part): not protected any more, no crash.
        assertNull(RatioJurisPhtxEnvelope.ratioJurisLocate(RatioJurisByteArrayChannel(bytes.copyOf(bytes.size - 1))))
        // Corrupted ciphertext inside the carrier: authentication fails.
        val t = bytes.copyOf().also { it[it.size - RatioJurisPhtxEnvelope.TRAILER_SIZE - 5] = (it[it.size - RatioJurisPhtxEnvelope.TRAILER_SIZE - 5].toInt() xor 1).toByte() }
        val c = RatioJurisPhtxEnvelope.ratioJurisReadContainer(RatioJurisByteArrayChannel(t), cipher.maxPayloadSize)
        try {
            cipher.ratioJurisOpen(c, PASSWORD); throw AssertionError("accepted tampered payload")
        } catch (e: RatioJurisPhtxException.Corrupted) { /* expected */ }
        // Every truncation is either "not protected" or a structured error, never a valid container.
        var pos = bytes.size - 1
        while (pos > 0) {
            val cut = bytes.copyOf(pos)
            val ch = RatioJurisByteArrayChannel(cut)
            val loc = runCatching { RatioJurisPhtxEnvelope.ratioJurisLocate(ch) }.getOrNull()
            if (loc != null) {
                try {
                    RatioJurisPhtxEnvelope.ratioJurisReadContainer(RatioJurisByteArrayChannel(cut), cipher.maxPayloadSize)
                    throw AssertionError("truncation at $pos accepted")
                } catch (e: RatioJurisPhtxException) { /* expected */ }
            }
            pos -= 997
        }
    }

    @Test fun failedCarrierWriteLeavesNothingBehind() {
        val target = File(tmp.root, "x.jpg")
        val outcome = pipeline.ratioJurisProtect(ratioJurisRequest(ByteArray(0), withThumb = false), PASSWORD, RatioJurisFileStaging(target), carrier = byteArrayOf(1, 2, 3))
        assertTrue(outcome is RatioJurisProtectionOutcome.Failure)
        assertFalse(target.exists())
        assertFalse(File(tmp.root, "x.jpg.partial").exists())
    }
}
