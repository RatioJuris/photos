// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Parsed, validated header. Everything here is public (readable without a password). */
class RatioJurisPhtxHeader internal constructor(
    val version: Int,
    val flags: Int,
    val cipherId: Int,
    val kdfId: Int,
    val kdfParams: RatioJurisKdfParams,
    internal val salt: ByteArray,
    internal val wrapNonce: ByteArray,
    internal val wrappedKey: ByteArray,
    internal val payloadNonce: ByteArray,
    val publicMetadata: Map<String, String>,
    /** Encoded thumbnail (normally JPEG) or null when the container has none. */
    val thumbnail: ByteArray?,
    val payloadLength: Long,
    /** Bytes [0, keyBlockAadEnd) authenticate the wrapped key. */
    internal val keyBlockAad: ByteArray,
    /** All bytes preceding the payload; used as AAD for the payload. */
    internal val headerBytes: ByteArray,
) {
    val hasThumbnail: Boolean get() = thumbnail != null
    val headerSize: Int get() = headerBytes.size
    val totalSize: Long get() = headerBytes.size + payloadLength
    val saltSize: Int get() = salt.size
}

/** Header plus the still-encrypted payload. */
class RatioJurisPhtxContainer internal constructor(
    val header: RatioJurisPhtxHeader,
    internal val payload: ByteArray,
)

/**
 * Strict PHTX reader. Every length is validated against format bounds and caller limits
 * before any allocation; unknown versions, algorithms or flags are rejected.
 */
object RatioJurisPhtxReader {

    /** Returns true if [prefix] starts with the PHTX signature. Does not imply validity. */
    fun looksLikePhtx(prefix: ByteArray): Boolean = RatioJurisPhtxFormat.hasMagic(prefix)

    /**
     * Reads and validates only the header (including thumbnail), leaving the payload unread.
     * @param totalSize the actual stream length if known; enforced exactly when non-null.
     */
    fun readHeader(
        input: InputStream,
        maxPayloadSize: Long = RatioJurisPhtxFormat.MAX_PAYLOAD_SIZE,
        totalSize: Long? = null,
    ): RatioJurisPhtxHeader {
        val r = RecordingReader(input)

        val magic = try {
            r.bytes(RatioJurisPhtxFormat.MAGIC.size)
        } catch (e: RatioJurisPhtxException.Malformed) {
            throw RatioJurisPhtxException.NotProtectedImage()
        }
        if (!RatioJurisPhtxFormat.hasMagic(magic)) throw RatioJurisPhtxException.NotProtectedImage()

        val version = r.u16()
        if (version != RatioJurisPhtxFormat.VERSION_1) throw RatioJurisPhtxException.Unsupported("version $version")
        val flags = r.u16()
        if (flags and RatioJurisPhtxFormat.KNOWN_FLAGS.inv() != 0) throw RatioJurisPhtxException.Unsupported("unknown flags")
        val cipherId = r.u8()
        if (cipherId != RatioJurisPhtxFormat.CIPHER_AES_256_GCM) throw RatioJurisPhtxException.Unsupported("cipher $cipherId")
        val kdfId = r.u8()
        if (kdfId != RatioJurisPhtxFormat.KDF_ARGON2ID) throw RatioJurisPhtxException.Unsupported("kdf $kdfId")
        val saltSize = r.u8()
        if (saltSize !in RatioJurisPhtxFormat.MIN_SALT_SIZE..RatioJurisPhtxFormat.MAX_SALT_SIZE) {
            throw RatioJurisPhtxException.Malformed("invalid salt length")
        }
        val nonceSize = r.u8()
        if (nonceSize != RatioJurisPhtxFormat.NONCE_SIZE) throw RatioJurisPhtxException.Malformed("invalid nonce length")
        val memory = r.u32AsInt("kdf memory")
        val iterations = r.u32AsInt("kdf iterations")
        val parallelism = r.u8()
        val reserved = r.bytes(3)
        if (reserved.any { it != 0.toByte() }) throw RatioJurisPhtxException.Malformed("reserved bytes set")
        val kdf = RatioJurisKdfParams(memory, iterations, parallelism).also { it.validate() }

        val salt = r.bytes(saltSize)
        val keyBlockAad = r.snapshot()
        val wrapNonce = r.bytes(RatioJurisPhtxFormat.NONCE_SIZE)
        val wrappedKey = r.bytes(RatioJurisPhtxFormat.WRAPPED_KEY_SIZE)
        val payloadNonce = r.bytes(RatioJurisPhtxFormat.NONCE_SIZE)

        val metaLen = r.u32AsInt("metadata length")
        if (metaLen > RatioJurisPhtxFormat.MAX_METADATA_SIZE) throw RatioJurisPhtxException.Malformed("metadata too large")
        val publicMetadata = RatioJurisMetadataCodec.decode(r.bytes(metaLen))

        val thumbLen = r.u32AsInt("thumbnail length")
        if (thumbLen > RatioJurisPhtxFormat.MAX_THUMBNAIL_SIZE) throw RatioJurisPhtxException.Malformed("thumbnail too large")
        val hasThumbFlag = flags and RatioJurisPhtxFormat.FLAG_HAS_THUMBNAIL != 0
        if (hasThumbFlag != (thumbLen > 0)) throw RatioJurisPhtxException.Malformed("thumbnail flag mismatch")
        val thumbnail = if (thumbLen > 0) r.bytes(thumbLen) else null

        val payloadLength = r.u64()
        if (payloadLength < RatioJurisPhtxFormat.MIN_PAYLOAD_SIZE || payloadLength > RatioJurisPhtxFormat.MAX_PAYLOAD_SIZE) {
            throw RatioJurisPhtxException.Malformed("invalid payload length")
        }
        if (payloadLength > maxPayloadSize) throw RatioJurisPhtxException.TooLarge("payload exceeds limit")
        val headerBytes = r.snapshot()
        if (totalSize != null && headerBytes.size + payloadLength != totalSize) {
            throw RatioJurisPhtxException.Malformed("payload length does not match file size")
        }

        return RatioJurisPhtxHeader(
            version = version,
            flags = flags,
            cipherId = cipherId,
            kdfId = kdfId,
            kdfParams = kdf,
            salt = salt,
            wrapNonce = wrapNonce,
            wrappedKey = wrappedKey,
            payloadNonce = payloadNonce,
            publicMetadata = publicMetadata,
            thumbnail = thumbnail,
            payloadLength = payloadLength,
            keyBlockAad = keyBlockAad,
            headerBytes = headerBytes,
        )
    }

    /** Reads a complete container and requires the stream to end exactly after the payload. */
    fun readContainer(
        input: InputStream,
        maxPayloadSize: Long,
        totalSize: Long? = null,
    ): RatioJurisPhtxContainer {
        val header = readHeader(input, maxPayloadSize, totalSize)
        // payloadLength <= maxPayloadSize was validated before this allocation.
        if (header.payloadLength > Int.MAX_VALUE - 8) throw RatioJurisPhtxException.TooLarge("payload exceeds limit")
        val payload = ByteArray(header.payloadLength.toInt())
        try {
            readFully(input, payload)
        } catch (e: EOFException) {
            throw RatioJurisPhtxException.Malformed("truncated payload")
        }
        if (input.read() != -1) throw RatioJurisPhtxException.Malformed("trailing data after payload")
        return RatioJurisPhtxContainer(header, payload)
    }

    fun parse(bytes: ByteArray, maxPayloadSize: Long = Int.MAX_VALUE.toLong() - 8): RatioJurisPhtxContainer =
        readContainer(ByteArrayInputStream(bytes), maxPayloadSize, bytes.size.toLong())

    internal fun readFully(input: InputStream, target: ByteArray) {
        var off = 0
        while (off < target.size) {
            val n = input.read(target, off, target.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    /** Sequential reader that records consumed bytes (they form the AAD). */
    private class RecordingReader(private val input: InputStream) {
        private val record = ByteArrayOutputStream(512)

        fun bytes(n: Int): ByteArray {
            val b = ByteArray(n)
            try {
                readFully(input, b)
            } catch (e: EOFException) {
                throw RatioJurisPhtxException.Malformed("truncated header")
            } catch (e: IOException) {
                throw e
            }
            record.write(b)
            return b
        }

        fun u8(): Int = bytes(1)[0].toInt() and 0xFF
        fun u16(): Int = bytes(2).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
        fun u32(): Long = bytes(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        fun u64(): Long {
            val b = bytes(8)
            if (b[0].toInt() and 0x80 != 0) throw RatioJurisPhtxException.Malformed("length overflow")
            return b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xFF) }
        }

        fun u32AsInt(what: String): Int {
            val v = u32()
            if (v > Int.MAX_VALUE) throw RatioJurisPhtxException.Malformed("$what overflow")
            return v.toInt()
        }

        fun snapshot(): ByteArray = record.toByteArray()
    }
}
