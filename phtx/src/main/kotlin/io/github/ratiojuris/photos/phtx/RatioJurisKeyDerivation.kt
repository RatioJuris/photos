// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.text.Normalizer

/**
 * A 256-bit key held in memory. [close] overwrites the key bytes.
 * Never logged, never persisted.
 */
class RatioJurisSecretKey internal constructor(internal val bytes: ByteArray) : AutoCloseable {
    override fun close() {
        bytes.fill(0)
    }

    override fun toString(): String = "RatioJurisSecretKey(<redacted>)"
}

/**
 * Argon2id (RFC 9106, version 0x13) password-based key derivation using BouncyCastle.
 *
 * Password encoding (normative for other implementations): the password is normalised to
 * Unicode NFC and encoded as UTF-8 without terminator. No secret/associated data is used.
 */
object RatioJurisKeyDerivation {

    fun ratioJurisDeriveKey(password: CharArray, salt: ByteArray, params: RatioJurisKdfParams): RatioJurisSecretKey {
        params.validate()
        require(salt.size in RatioJurisPhtxFormat.MIN_SALT_SIZE..RatioJurisPhtxFormat.MAX_SALT_SIZE)
        val passwordBytes = ratioJurisPasswordBytes(password)
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(argon)
            val out = ByteArray(RatioJurisPhtxFormat.KEY_SIZE)
            generator.generateBytes(passwordBytes, out)
            return RatioJurisSecretKey(out)
        } finally {
            passwordBytes.fill(0)
        }
    }

    internal fun ratioJurisPasswordBytes(password: CharArray): ByteArray {
        val buffer = CharBuffer.wrap(password)
        return if (Normalizer.isNormalized(buffer, Normalizer.Form.NFC)) {
            val encoded = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
            val bytes = ByteArray(encoded.remaining())
            encoded.get(bytes)
            if (encoded.hasArray()) encoded.array().fill(0)
            bytes
        } else {
            // Normalizer only returns String; unavoidable short-lived immutable copy.
            Normalizer.normalize(buffer, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        }
    }
}
