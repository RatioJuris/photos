// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

/**
 * Constants of the PHTX v1 protected image container.
 * The normative PHTX v1 specification is maintained alongside this code; keep both in sync.
 */
object RatioJurisPhtxFormat {
    /** "PHTX\r\n\u001A\n" - detects text-mode corruption, like the PNG signature. */
    val MAGIC: ByteArray = byteArrayOf(0x50, 0x48, 0x54, 0x58, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Authorship signature recorded in the public metadata of every container written by this code. */
    const val RATIOJURIS_CREATOR: String = "RatioJuris Photos"
    const val RATIOJURIS_PROJECT_URL: String = "https://github.com/ratiojuris/photos"

    const val VERSION_1: Int = 1
    const val FILE_EXTENSION: String = "phtx"
    const val MIME_TYPE: String = "application/vnd.photos.phtx"

    const val FLAG_HAS_THUMBNAIL: Int = 0x0001
    const val KNOWN_FLAGS: Int = FLAG_HAS_THUMBNAIL

    const val CIPHER_AES_256_GCM: Int = 0x01
    const val KDF_ARGON2ID: Int = 0x01

    const val FIXED_HEADER_SIZE: Int = 28
    const val NONCE_SIZE: Int = 12
    const val TAG_SIZE: Int = 16
    const val KEY_SIZE: Int = 32
    const val WRAPPED_KEY_SIZE: Int = KEY_SIZE + TAG_SIZE

    const val MIN_SALT_SIZE: Int = 16
    const val MAX_SALT_SIZE: Int = 64
    const val DEFAULT_SALT_SIZE: Int = 32

    /** Argon2id bounds accepted by readers; prevents hostile files from forcing huge allocations. */
    const val MIN_KDF_MEMORY_KIB: Int = 8 * 1024
    const val MAX_KDF_MEMORY_KIB: Int = 256 * 1024
    const val MIN_KDF_ITERATIONS: Int = 1
    const val MAX_KDF_ITERATIONS: Int = 16
    const val MIN_KDF_PARALLELISM: Int = 1
    const val MAX_KDF_PARALLELISM: Int = 16

    const val MAX_METADATA_SIZE: Int = 64 * 1024
    const val MAX_THUMBNAIL_SIZE: Int = 2 * 1024 * 1024

    /** Smallest valid payload: inner-metadata length field (4) + GCM tag. */
    const val MIN_PAYLOAD_SIZE: Long = (4 + TAG_SIZE).toLong()

    /** Format ceiling (AES-GCM single-message limit is ~64 GiB). Implementations may impose lower limits. */
    const val MAX_PAYLOAD_SIZE: Long = 1L shl 36

    /** Upper bound of the header region preceding the payload. */
    val MAX_HEADER_SIZE: Int = FIXED_HEADER_SIZE + MAX_SALT_SIZE + NONCE_SIZE + WRAPPED_KEY_SIZE +
        NONCE_SIZE + 4 + MAX_METADATA_SIZE + 4 + MAX_THUMBNAIL_SIZE + 8

    fun hasMagic(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC.size && MAGIC.indices.all { prefix[it] == MAGIC[it] }
}

/** Argon2id cost parameters, stored verbatim in every container. */
data class RatioJurisKdfParams(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    fun validate() {
        if (memoryKiB !in RatioJurisPhtxFormat.MIN_KDF_MEMORY_KIB..RatioJurisPhtxFormat.MAX_KDF_MEMORY_KIB) {
            throw RatioJurisPhtxException.Unsupported("KDF memory out of range")
        }
        if (iterations !in RatioJurisPhtxFormat.MIN_KDF_ITERATIONS..RatioJurisPhtxFormat.MAX_KDF_ITERATIONS) {
            throw RatioJurisPhtxException.Unsupported("KDF iterations out of range")
        }
        if (parallelism !in RatioJurisPhtxFormat.MIN_KDF_PARALLELISM..RatioJurisPhtxFormat.MAX_KDF_PARALLELISM) {
            throw RatioJurisPhtxException.Unsupported("KDF parallelism out of range")
        }
    }

    companion object {
        /** Production default: 64 MiB, 3 passes, 1 lane (exceeds OWASP Argon2id minimums). */
        val DEFAULT = RatioJurisKdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)
    }
}
