// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer

/**
 * Language-neutral key/value metadata encoding used for both the public (plaintext)
 * and private (encrypted) metadata blocks.
 *
 *   u16 count
 *   repeated count times:
 *     u8  keyLength (1..64)   key bytes: ASCII [a-z0-9._-]
 *     u16 valueLength (0..4096) value bytes: UTF-8
 *
 * Keys must be unique. Unknown keys must be ignored by readers.
 */
object RatioJurisMetadataCodec {
    const val MAX_ENTRIES = 256
    const val MAX_KEY_LENGTH = 64
    const val MAX_VALUE_LENGTH = 4096
    private val KEY_PATTERN = Regex("^[a-z0-9._-]+$")

    fun encode(entries: Map<String, String>): ByteArray {
        require(entries.size <= MAX_ENTRIES) { "Too many metadata entries" }
        val out = ByteArrayOutputStream()
        out.writeU16(entries.size)
        for ((key, value) in entries) {
            val k = key.toByteArray(Charsets.US_ASCII)
            require(k.size in 1..MAX_KEY_LENGTH && KEY_PATTERN.matches(key)) { "Invalid metadata key" }
            val v = value.toByteArray(Charsets.UTF_8)
            require(v.size <= MAX_VALUE_LENGTH) { "Metadata value too long" }
            out.write(k.size)
            out.write(k)
            out.writeU16(v.size)
            out.write(v)
        }
        val bytes = out.toByteArray()
        require(bytes.size <= RatioJurisPhtxFormat.MAX_METADATA_SIZE) { "Metadata too large" }
        return bytes
    }

    fun decode(bytes: ByteArray): Map<String, String> {
        if (bytes.isEmpty()) return emptyMap()
        val buf = ByteBuffer.wrap(bytes)
        fun need(n: Int) {
            if (buf.remaining() < n) throw RatioJurisPhtxException.Malformed("metadata truncated")
        }
        need(2)
        val count = buf.short.toInt() and 0xFFFF
        if (count > MAX_ENTRIES) throw RatioJurisPhtxException.Malformed("too many metadata entries")
        val result = LinkedHashMap<String, String>(count)
        repeat(count) {
            need(1)
            val keyLen = buf.get().toInt() and 0xFF
            if (keyLen !in 1..MAX_KEY_LENGTH) throw RatioJurisPhtxException.Malformed("bad metadata key length")
            need(keyLen)
            val keyBytes = ByteArray(keyLen).also { buf.get(it) }
            val key = String(keyBytes, Charsets.US_ASCII)
            if (!KEY_PATTERN.matches(key)) throw RatioJurisPhtxException.Malformed("bad metadata key")
            need(2)
            val valueLen = buf.short.toInt() and 0xFFFF
            if (valueLen > MAX_VALUE_LENGTH) throw RatioJurisPhtxException.Malformed("bad metadata value length")
            need(valueLen)
            val valueBytes = ByteArray(valueLen).also { buf.get(it) }
            val value = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(valueBytes)).toString()
            } catch (e: CharacterCodingException) {
                throw RatioJurisPhtxException.Malformed("metadata value is not UTF-8")
            }
            if (result.put(key, value) != null) throw RatioJurisPhtxException.Malformed("duplicate metadata key")
        }
        if (buf.hasRemaining()) throw RatioJurisPhtxException.Malformed("trailing metadata bytes")
        return result
    }

    private fun ByteArrayOutputStream.writeU16(v: Int) {
        write((v ushr 8) and 0xFF)
        write(v and 0xFF)
    }
}

/** Well-known metadata keys. */
object RatioJurisMetaKeys {
    // Public block (readable without the password)
    const val CREATED_AT = "created.ms"
    const val CREATOR = "creator"
    const val THUMB_MIME = "thumb.mime"
    const val THUMB_WIDTH = "thumb.width"
    const val THUMB_HEIGHT = "thumb.height"
    const val THUMB_POLICY = "thumb.policy"

    // Private block (encrypted together with the original)
    const val ORIGINAL_NAME = "orig.name"
    const val ORIGINAL_MIME = "orig.mime"
    const val ORIGINAL_WIDTH = "orig.width"
    const val ORIGINAL_HEIGHT = "orig.height"
    const val ORIGINAL_DATE_TAKEN = "orig.date_taken.ms"
    const val ORIGINAL_SIZE = "orig.size"
    const val ORIGINAL_SHA256 = "orig.sha256"
}
