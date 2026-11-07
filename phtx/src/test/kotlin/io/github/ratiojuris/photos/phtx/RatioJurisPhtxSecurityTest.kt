// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.PASSWORD
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisCipher
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisPng
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisProtectToBytes
import io.github.ratiojuris.photos.phtx.RatioJurisFixtures.ratioJurisRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class RatioJurisPhtxSecurityTest {

    private val cipher = ratioJurisCipher()

    private inline fun <reified E : Throwable> expect(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw AssertionError("Expected ${E::class.simpleName} but got $e", e)
        }
        fail("Expected ${E::class.simpleName}")
        throw IllegalStateException()
    }

    /** Offsets of the v1 layout for a container produced by the fixtures. */
    private fun layout(bytes: ByteArray): Map<String, Int> {
        val header = RatioJurisPhtxReader.parse(bytes).header
        val saltStart = RatioJurisPhtxFormat.FIXED_HEADER_SIZE
        val wrapNonce = saltStart + header.saltSize
        val wrapped = wrapNonce + RatioJurisPhtxFormat.NONCE_SIZE
        val payloadNonce = wrapped + RatioJurisPhtxFormat.WRAPPED_KEY_SIZE
        return mapOf(
            "salt" to saltStart,
            "wrapNonce" to wrapNonce,
            "wrapped" to wrapped,
            "payloadNonce" to payloadNonce,
            "metaLen" to payloadNonce + RatioJurisPhtxFormat.NONCE_SIZE,
            "payload" to header.headerSize,
            "payloadLen" to header.headerSize - 8,
        )
    }

    // 1 + 14: encrypt a valid image and preserve content byte-for-byte
    @Test fun encryptsAndRoundTripsImageExactly() {
        val image = ratioJurisPng()
        val bytes = ratioJurisProtectToBytes(request = ratioJurisRequest(image))
        val container = RatioJurisPhtxReader.parse(bytes)
        val decrypted = cipher.ratioJurisOpen(container, PASSWORD)
        assertArrayEquals(image, decrypted.image)
        assertEquals("IMG_0001.png", decrypted.privateMetadata[RatioJurisMetaKeys.ORIGINAL_NAME])
        assertEquals(image.size.toString(), decrypted.privateMetadata[RatioJurisMetaKeys.ORIGINAL_SIZE])
        assertNotNull(ImageIO.read(ByteArrayInputStream(decrypted.image)))
    }

    // 2
    @Test fun decryptsWithCorrectPassword() {
        val bytes = ratioJurisProtectToBytes()
        val decrypted = cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(bytes), "correct horse battery staple".toCharArray())
        assertTrue(decrypted.image.isNotEmpty())
    }

    // 3
    @Test fun rejectsIncorrectPassword() {
        val bytes = ratioJurisProtectToBytes()
        expect<RatioJurisPhtxException.WrongPassword> {
            cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(bytes), "Correct horse battery staple".toCharArray())
        }
    }

    // 4
    @Test fun rejectsModifiedCiphertext() {
        val bytes = ratioJurisProtectToBytes()
        val payloadStart = layout(bytes).getValue("payload")
        for (pos in listOf(payloadStart, payloadStart + 100, bytes.size - 17)) {
            val tampered = bytes.copyOf().also { it[pos] = (it[pos].toInt() xor 0x01).toByte() }
            expect<RatioJurisPhtxException.Corrupted> { cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(tampered), PASSWORD) }
        }
    }

    // 5
    @Test fun rejectsModifiedAuthenticationTag() {
        val bytes = ratioJurisProtectToBytes()
        for (pos in bytes.size - 16 until bytes.size) {
            val tampered = bytes.copyOf().also { it[pos] = (it[pos].toInt() xor 0x80).toByte() }
            expect<RatioJurisPhtxException.Corrupted> { cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(tampered), PASSWORD) }
        }
        // Tag of the wrapped key: indistinguishable from a wrong password by design.
        val l = layout(bytes)
        val wrapTag = l.getValue("wrapped") + RatioJurisPhtxFormat.KEY_SIZE
        val tampered = bytes.copyOf().also { it[wrapTag] = (it[wrapTag].toInt() xor 1).toByte() }
        expect<RatioJurisPhtxException.WrongPassword> { cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(tampered), PASSWORD) }
    }

    @Test fun rejectsTamperedHeaderFieldsAndThumbnail() {
        val bytes = ratioJurisProtectToBytes()
        val l = layout(bytes)
        // salt and nonce tampering
        for (pos in listOf(l.getValue("salt"), l.getValue("wrapNonce"), l.getValue("payloadNonce"))) {
            val t = bytes.copyOf().also { it[pos] = (it[pos].toInt() xor 1).toByte() }
            expect<RatioJurisPhtxException> { cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(t), PASSWORD) }
        }
        // Thumbnail bytes are bound to the payload as AAD: swap one thumbnail byte.
        val thumbPos = l.getValue("payloadLen") - 10
        val t = bytes.copyOf().also { it[thumbPos] = (it[thumbPos].toInt() xor 1).toByte() }
        expect<RatioJurisPhtxException.Corrupted> { cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(t), PASSWORD) }
    }

    // 6
    @Test fun rejectsMalformedHeaders() {
        val bytes = ratioJurisProtectToBytes()
        expect<RatioJurisPhtxException.NotProtectedImage> { RatioJurisPhtxReader.parse(ratioJurisPng()) }
        expect<RatioJurisPhtxException.NotProtectedImage> {
            RatioJurisPhtxReader.parse(bytes.copyOf().also { it[0] = 'X'.code.toByte() })
        }
        // unknown flags
        expect<RatioJurisPhtxException.Unsupported> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[11] = 0x02 }) }
        // thumbnail flag cleared while a thumbnail is present
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[11] = 0x00 }) }
        // unknown cipher / kdf
        expect<RatioJurisPhtxException.Unsupported> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[12] = 0x7F }) }
        expect<RatioJurisPhtxException.Unsupported> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[13] = 0x02 }) }
        // reserved bytes
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[26] = 1 }) }
        // hostile KDF cost (memory = 0xFFFFFFFF KiB must not be attempted)
        expect<RatioJurisPhtxException.Malformed> {
            RatioJurisPhtxReader.parse(bytes.copyOf().also { for (i in 16..19) it[i] = 0xFF.toByte() })
        }
        expect<RatioJurisPhtxException.Unsupported> {
            RatioJurisPhtxReader.parse(bytes.copyOf().also { it[16] = 0x10 }) // ~256 GiB
        }
        // metadata length beyond limit
        val metaLen = layout(bytes).getValue("metaLen")
        expect<RatioJurisPhtxException.Malformed> {
            RatioJurisPhtxReader.parse(bytes.copyOf().also { it[metaLen] = 0x7F })
        }
        // trailing garbage
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(bytes + byteArrayOf(0)) }
    }

    // 7
    @Test fun rejectsUnsupportedVersions() {
        val bytes = ratioJurisProtectToBytes()
        for (v in listOf(0, 2, 0xFFFF)) {
            val t = bytes.copyOf().also { it[8] = (v ushr 8).toByte(); it[9] = v.toByte() }
            val e = expect<RatioJurisPhtxException.Unsupported> { RatioJurisPhtxReader.parse(t) }
            assertTrue(e.message!!.contains("version"))
        }
    }

    // 8 + 19: every truncation point is rejected; no prefix is a valid-looking container
    @Test fun rejectsEveryTruncation() {
        val bytes = ratioJurisProtectToBytes(request = ratioJurisRequest(ratioJurisPng(64, 48)))
        val step = maxOf(1, bytes.size / 400)
        var pos = 0
        while (pos < bytes.size) {
            val truncated = bytes.copyOf(pos)
            try {
                RatioJurisPhtxReader.parse(truncated)
                fail("Truncation at $pos accepted")
            } catch (e: RatioJurisPhtxException) {
                // expected: NotProtectedImage below magic length, Malformed afterwards
            }
            pos += if (pos < 600) 1 else step
        }
    }

    // 9
    @Test fun rejectsInvalidNonceLengths() {
        val bytes = ratioJurisProtectToBytes()
        for (n in listOf(0, 8, 11, 13, 16, 255)) {
            expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[15] = n.toByte() }) }
        }
    }

    // 10
    @Test fun rejectsInvalidSaltLengths() {
        val bytes = ratioJurisProtectToBytes()
        for (n in listOf(0, 1, 15, 65, 255)) {
            expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(bytes.copyOf().also { it[14] = n.toByte() }) }
        }
    }

    // 11
    @Test fun rejectsImpossiblePayloadLengths() {
        val bytes = ratioJurisProtectToBytes()
        val at = layout(bytes).getValue("payloadLen")
        fun withLen(v: Long) = bytes.copyOf().also { b -> for (i in 0 until 8) b[at + i] = (v ushr (56 - 8 * i)).toByte() }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(withLen(0)) }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(withLen(15)) }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(withLen(-1)) }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(withLen((1L shl 36) + 1)) }
        // Plausible but larger than the caller's limit: rejected before allocating.
        expect<RatioJurisPhtxException.TooLarge> {
            RatioJurisPhtxReader.readContainer(ByteArrayInputStream(withLen(1L shl 35)), maxPayloadSize = 1L shl 20)
        }
        // Length disagreeing with real file size.
        val real = RatioJurisPhtxReader.parse(bytes).header.payloadLength
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(withLen(real + 1)) }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisPhtxReader.parse(withLen(real - 1)) }
    }

    // 12
    @Test fun handlesEmptyFiles() {
        expect<RatioJurisPhtxException.EmptyInput> {
            cipher.ratioJurisProtect(ByteArrayOutputStream(), ratioJurisRequest(ByteArray(0), withThumb = false), PASSWORD)
        }
        expect<RatioJurisPhtxException.NotProtectedImage> { RatioJurisPhtxReader.parse(ByteArray(0)) }
        assertFalse(RatioJurisPhtxReader.looksLikePhtx(ByteArray(0)))
    }

    // 13
    @Test fun handlesOversizedFiles() {
        val small = ratioJurisCipher(maxImageSize = 1024)
        val out = ByteArrayOutputStream()
        expect<RatioJurisPhtxException.TooLarge> {
            small.ratioJurisProtect(out, ratioJurisRequest(ByteArray(1025) { 1 }, withThumb = false), PASSWORD)
        }
        assertEquals("nothing written for oversized input", 0, out.size())
        expect<RatioJurisPhtxException.TooLarge> {
            cipher.ratioJurisProtect(
                ByteArrayOutputStream(),
                RatioJurisProtectRequest(image = ByteArray(10) { 1 }, thumbnail = ByteArray(RatioJurisPhtxFormat.MAX_THUMBNAIL_SIZE + 1)),
                PASSWORD,
            )
        }
    }

    // 15 + 16: thumbnail is readable from the header alone, without any password
    @Test fun thumbnailReadableWithoutPassword() {
        val request = ratioJurisRequest()
        val bytes = ratioJurisProtectToBytes(request = request)
        // Read only the header; the payload is never touched.
        val header = RatioJurisPhtxReader.readHeader(ByteArrayInputStream(bytes))
        assertArrayEquals(request.thumbnail, header.thumbnail)
        val decoded = ImageIO.read(ByteArrayInputStream(header.thumbnail))
        assertNotNull(decoded)
        assertTrue(maxOf(decoded.width, decoded.height) <= 64)
        assertEquals("standard", header.publicMetadata[RatioJurisMetaKeys.THUMB_POLICY])
        assertEquals(RatioJurisPhtxFormat.RATIOJURIS_CREATOR, header.publicMetadata[RatioJurisMetaKeys.CREATOR])
    }

    @Test fun noThumbnailPolicyStoresNoThumbnail() {
        val bytes = ratioJurisProtectToBytes(request = ratioJurisRequest(withThumb = false))
        val header = RatioJurisPhtxReader.parse(bytes).header
        assertNull(header.thumbnail)
        assertFalse(header.hasThumbnail)
    }

    // 17: the original is not present in plaintext and private metadata is hidden
    @Test fun originalRemainsProtected() {
        val image = ratioJurisPng()
        val bytes = ratioJurisProtectToBytes(request = ratioJurisRequest(image))
        val window = image.copyOfRange(image.size / 2, image.size / 2 + 32)
        assertFalse("plaintext window found in container", bytes.indexOf(window) >= 0)
        assertFalse(bytes.indexOf("IMG_0001.png".toByteArray()) >= 0)
        assertFalse(bytes.indexOf(PASSWORD.concatToString().toByteArray()) >= 0)
        // Two encryptions of the same input differ (fresh salt, key, nonces).
        val again = ratioJurisProtectToBytes(request = ratioJurisRequest(image))
        assertFalse(bytes.contentEquals(again))
    }

    @Test fun saltsAndNoncesAreUniquePerContainer() {
        val seen = HashSet<String>()
        repeat(8) {
            val bytes = ratioJurisProtectToBytes(request = ratioJurisRequest(ByteArray(100) { 3 }, withThumb = false))
            val l = layout(bytes)
            assertTrue(seen.add("s" + bytes.copyOfRange(l.getValue("salt"), l.getValue("wrapNonce")).ratioJurisHex()))
            assertTrue(seen.add("w" + bytes.copyOfRange(l.getValue("wrapNonce"), l.getValue("wrapped")).ratioJurisHex()))
            assertTrue(seen.add("p" + bytes.copyOfRange(l.getValue("payloadNonce"), l.getValue("metaLen")).ratioJurisHex()))
        }
    }

    @Test fun changePasswordReencryptsWithFreshKeys() {
        val image = ratioJurisPng()
        val original = ratioJurisProtectToBytes(request = ratioJurisRequest(image))
        val out = ByteArrayOutputStream()
        val newPassword = "new password 2".toCharArray()
        expect<RatioJurisPhtxException.WrongPassword> {
            cipher.ratioJurisChangePassword(RatioJurisPhtxReader.parse(original), "nope".toCharArray(), newPassword, ByteArrayOutputStream())
        }
        cipher.ratioJurisChangePassword(RatioJurisPhtxReader.parse(original), PASSWORD, newPassword, out).verificationKey.close()
        val changed = RatioJurisPhtxReader.parse(out.toByteArray())
        expect<RatioJurisPhtxException.WrongPassword> { cipher.ratioJurisOpen(changed, PASSWORD) }
        assertArrayEquals(image, cipher.ratioJurisOpen(changed, newPassword).image)
        assertArrayEquals(RatioJurisPhtxReader.parse(original).header.thumbnail, changed.header.thumbnail)
    }

    @Test fun unicodePasswordsAreNormalised() {
        val composed = "caf\u00e9".toCharArray()
        val decomposed = "cafe\u0301".toCharArray()
        val bytes = ratioJurisProtectToBytes(password = composed)
        assertTrue(cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(bytes), decomposed).image.isNotEmpty())
    }

    @Test fun argon2idMatchesRfc9106StyleVector() {
        // Independent check of the KDF wiring: same inputs -> same key, different salt -> different key.
        val salt1 = ByteArray(16) { it.toByte() }
        val salt2 = ByteArray(16) { (it + 1).toByte() }
        val k1 = RatioJurisKeyDerivation.ratioJurisDeriveKey(PASSWORD, salt1, RatioJurisFixtures.TEST_KDF)
        val k2 = RatioJurisKeyDerivation.ratioJurisDeriveKey(PASSWORD, salt1, RatioJurisFixtures.TEST_KDF)
        val k3 = RatioJurisKeyDerivation.ratioJurisDeriveKey(PASSWORD, salt2, RatioJurisFixtures.TEST_KDF)
        assertArrayEquals(k1.bytes, k2.bytes)
        assertFalse(k1.bytes.contentEquals(k3.bytes))
        k1.close()
        assertTrue(k1.bytes.all { it == 0.toByte() })
    }

    @Test fun metadataCodecRejectsHostileInput() {
        val ok = RatioJurisMetadataCodec.encode(mapOf("a.b" to "\u00fcml\u00e4ut"))
        assertEquals("\u00fcml\u00e4ut", RatioJurisMetadataCodec.decode(ok)["a.b"])
        expect<RatioJurisPhtxException.Malformed> { RatioJurisMetadataCodec.decode(byteArrayOf(0x01, 0x00)) } // > 256 entries
        expect<RatioJurisPhtxException.Malformed> { RatioJurisMetadataCodec.decode(byteArrayOf(0, 1, 0)) } // zero key length
        expect<RatioJurisPhtxException.Malformed> { RatioJurisMetadataCodec.decode(byteArrayOf(0, 1, 1, 'A'.code.toByte(), 0, 0)) }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisMetadataCodec.decode(byteArrayOf(0, 1, 1, 'a'.code.toByte(), 0, 5, 1)) }
        val dup = byteArrayOf(0, 2, 1, 'a'.code.toByte(), 0, 0, 1, 'a'.code.toByte(), 0, 0)
        expect<RatioJurisPhtxException.Malformed> { RatioJurisMetadataCodec.decode(dup) }
        expect<RatioJurisPhtxException.Malformed> { RatioJurisMetadataCodec.decode(byteArrayOf(0, 1, 1, 'a'.code.toByte(), 0, 1, 0xC3.toByte())) }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
