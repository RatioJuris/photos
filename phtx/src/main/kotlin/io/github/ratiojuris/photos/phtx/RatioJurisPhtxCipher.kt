// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** What to protect. [image] is the exact original file content. */
class RatioJurisProtectRequest(
    val image: ByteArray,
    val privateMetadata: Map<String, String> = emptyMap(),
    val publicMetadata: Map<String, String> = emptyMap(),
    /** Encoded thumbnail bytes (e.g. JPEG), or null for no thumbnail. */
    val thumbnail: ByteArray? = null,
)

/** Result of writing a container. [verificationKey] lets the caller verify without re-running Argon2. */
class RatioJurisProtectResult internal constructor(
    val bytesWritten: Long,
    val originalSha256: ByteArray,
    val verificationKey: RatioJurisSecretKey,
)

/** Decrypted original plus its private metadata. */
class RatioJurisDecryptedImage(
    val image: ByteArray,
    val privateMetadata: Map<String, String>,
) {
    fun ratioJurisWipe() = image.fill(0)
}

/**
 * PHTX v1 encryption/decryption.
 *
 * Key hierarchy: a random 256-bit content key (CEK) encrypts the payload with AES-256-GCM.
 * The CEK is wrapped with AES-256-GCM under a key-encryption key (KEK) derived from the
 * password with Argon2id and a per-file random salt. Fresh random CEK, salt and nonces are
 * generated for every container, so nonces are never reused under one key.
 */
class RatioJurisPhtxCipher(
    private val kdfParams: RatioJurisKdfParams = RatioJurisKdfParams.DEFAULT,
    private val random: SecureRandom = SecureRandom(),
    private val saltSize: Int = RatioJurisPhtxFormat.DEFAULT_SALT_SIZE,
    /** Implementation limit for originals (memory bound). */
    val maxImageSize: Long = DEFAULT_MAX_IMAGE_SIZE,
) {
    init {
        kdfParams.validate()
        require(saltSize in RatioJurisPhtxFormat.MIN_SALT_SIZE..RatioJurisPhtxFormat.MAX_SALT_SIZE)
    }

    /** Payload limit derived from the image limit (adds room for metadata and tag). */
    val maxPayloadSize: Long
        get() = maxImageSize + 4 + RatioJurisPhtxFormat.MAX_METADATA_SIZE + RatioJurisPhtxFormat.TAG_SIZE

    /**
     * Encrypts [request] and writes a complete container to [out].
     * The caller owns the returned [RatioJurisProtectResult.verificationKey] and must close it.
     */
    fun ratioJurisProtect(out: OutputStream, request: RatioJurisProtectRequest, password: CharArray): RatioJurisProtectResult {
        val image = request.image
        if (image.isEmpty()) throw RatioJurisPhtxException.EmptyInput()
        if (image.size > maxImageSize) throw RatioJurisPhtxException.TooLarge("image exceeds limit")
        if (password.isEmpty()) throw IllegalArgumentException("Password must not be empty")
        val thumbnail = request.thumbnail?.takeIf { it.isNotEmpty() }
        if (thumbnail != null && thumbnail.size > RatioJurisPhtxFormat.MAX_THUMBNAIL_SIZE) {
            throw RatioJurisPhtxException.TooLarge("thumbnail exceeds limit")
        }

        val sha256 = MessageDigest.getInstance("SHA-256").digest(image)
        val privateMeta = LinkedHashMap(request.privateMetadata).apply {
            put(RatioJurisMetaKeys.ORIGINAL_SIZE, image.size.toString())
            put(RatioJurisMetaKeys.ORIGINAL_SHA256, sha256.ratioJurisHex())
        }
        val publicMeta = LinkedHashMap(request.publicMetadata).apply {
            putIfAbsent(RatioJurisMetaKeys.CREATOR, RatioJurisPhtxFormat.RATIOJURIS_CREATOR)
        }
        val privateMetaBytes = RatioJurisMetadataCodec.encode(privateMeta)
        val publicMetaBytes = RatioJurisMetadataCodec.encode(publicMeta)

        val salt = ByteArray(saltSize).also(random::nextBytes)
        val wrapNonce = ByteArray(RatioJurisPhtxFormat.NONCE_SIZE).also(random::nextBytes)
        val payloadNonce = ByteArray(RatioJurisPhtxFormat.NONCE_SIZE).also(random::nextBytes)
        val cek = ByteArray(RatioJurisPhtxFormat.KEY_SIZE).also(random::nextBytes)

        val kek = RatioJurisKeyDerivation.ratioJurisDeriveKey(password, salt, kdfParams)
        try {
            val header = ByteArrayOutputStream(1024 + (thumbnail?.size ?: 0))
            val flags = if (thumbnail != null) RatioJurisPhtxFormat.FLAG_HAS_THUMBNAIL else 0
            header.write(RatioJurisPhtxFormat.MAGIC)
            header.u16(RatioJurisPhtxFormat.VERSION_1)
            header.u16(flags)
            header.write(RatioJurisPhtxFormat.CIPHER_AES_256_GCM)
            header.write(RatioJurisPhtxFormat.KDF_ARGON2ID)
            header.write(saltSize)
            header.write(RatioJurisPhtxFormat.NONCE_SIZE)
            header.u32(kdfParams.memoryKiB.toLong())
            header.u32(kdfParams.iterations.toLong())
            header.write(kdfParams.parallelism)
            header.write(ByteArray(3))
            header.write(salt)
            val keyBlockAad = header.toByteArray()

            val wrapped = gcm(Cipher.ENCRYPT_MODE, kek.bytes, wrapNonce, keyBlockAad).doFinal(cek)
            check(wrapped.size == RatioJurisPhtxFormat.WRAPPED_KEY_SIZE)
            header.write(wrapNonce)
            header.write(wrapped)
            header.write(payloadNonce)
            header.u32(publicMetaBytes.size.toLong())
            header.write(publicMetaBytes)
            header.u32((thumbnail?.size ?: 0).toLong())
            if (thumbnail != null) header.write(thumbnail)
            val payloadLength = 4L + privateMetaBytes.size + image.size + RatioJurisPhtxFormat.TAG_SIZE
            header.u64(payloadLength)
            val headerBytes = header.toByteArray()

            out.write(headerBytes)
            val cipher = gcm(Cipher.ENCRYPT_MODE, cek, payloadNonce, headerBytes)
            val lengthPrefix = ByteArray(4).also { ratioJurisPutU32(it, privateMetaBytes.size.toLong()) }
            var written = 0L
            fun emit(chunk: ByteArray?) {
                if (chunk != null && chunk.isNotEmpty()) {
                    out.write(chunk); written += chunk.size
                }
            }
            emit(cipher.update(lengthPrefix))
            emit(cipher.update(privateMetaBytes))
            var offset = 0
            while (offset < image.size) {
                val len = minOf(CHUNK, image.size - offset)
                emit(cipher.update(image, offset, len))
                offset += len
            }
            emit(cipher.doFinal())
            out.flush()
            check(written == payloadLength) { "payload length mismatch" }
            return RatioJurisProtectResult(
                bytesWritten = headerBytes.size + written,
                originalSha256 = sha256,
                verificationKey = kek,
            )
        } catch (t: Throwable) {
            kek.close()
            throw t
        } finally {
            cek.fill(0)
        }
    }

    /** Derives the key for [header]. Expensive (Argon2id); run off the main thread. */
    fun ratioJurisDeriveKey(header: RatioJurisPhtxHeader, password: CharArray): RatioJurisSecretKey =
        RatioJurisKeyDerivation.ratioJurisDeriveKey(password, header.salt, header.kdfParams)

    /** Authenticates [password] and decrypts the original. */
    fun ratioJurisOpen(container: RatioJurisPhtxContainer, password: CharArray): RatioJurisDecryptedImage =
        ratioJurisDeriveKey(container.header, password).use { ratioJurisOpenWithKey(container, it) }

    /** Decrypts using an already-derived key-encryption key. */
    fun ratioJurisOpenWithKey(container: RatioJurisPhtxContainer, kek: RatioJurisSecretKey): RatioJurisDecryptedImage {
        val header = container.header
        val cek = try {
            gcm(Cipher.DECRYPT_MODE, kek.bytes, header.wrapNonce, header.keyBlockAad).doFinal(header.wrappedKey)
        } catch (e: AEADBadTagException) {
            throw RatioJurisPhtxException.WrongPassword()
        }
        val plain = try {
            gcm(Cipher.DECRYPT_MODE, cek, header.payloadNonce, header.headerBytes).doFinal(container.payload)
        } catch (e: GeneralSecurityException) {
            throw RatioJurisPhtxException.Corrupted(e)
        } finally {
            cek.fill(0)
        }
        try {
            if (plain.size < 4) throw RatioJurisPhtxException.Corrupted()
            val metaLen = ratioJurisGetU32(plain, 0)
            if (metaLen > RatioJurisPhtxFormat.MAX_METADATA_SIZE || metaLen > plain.size - 4) {
                throw RatioJurisPhtxException.Corrupted()
            }
            val meta = try {
                RatioJurisMetadataCodec.decode(plain.copyOfRange(4, 4 + metaLen.toInt()))
            } catch (e: RatioJurisPhtxException.Malformed) {
                throw RatioJurisPhtxException.Corrupted(e)
            }
            val image = plain.copyOfRange(4 + metaLen.toInt(), plain.size)
            val expectedHash = meta[RatioJurisMetaKeys.ORIGINAL_SHA256]
            if (expectedHash != null) {
                val actual = MessageDigest.getInstance("SHA-256").digest(image).ratioJurisHex()
                if (!MessageDigest.isEqual(actual.toByteArray(), expectedHash.toByteArray())) {
                    image.fill(0)
                    throw RatioJurisPhtxException.Corrupted()
                }
            }
            return RatioJurisDecryptedImage(image, meta)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * Re-protects [container] under [newPassword]: authenticates the old password, decrypts,
     * then writes a brand-new container (new salt, new content key, new nonces) to [out].
     * Public metadata and thumbnail are carried over.
     */
    fun ratioJurisChangePassword(
        container: RatioJurisPhtxContainer,
        oldPassword: CharArray,
        newPassword: CharArray,
        out: OutputStream,
    ): RatioJurisProtectResult {
        val decrypted = ratioJurisOpen(container, oldPassword)
        try {
            val request = RatioJurisProtectRequest(
                image = decrypted.image,
                privateMetadata = decrypted.privateMetadata,
                publicMetadata = container.header.publicMetadata,
                thumbnail = container.header.thumbnail,
            )
            return ratioJurisProtect(out, request, newPassword)
        } finally {
            decrypted.ratioJurisWipe()
        }
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(RatioJurisPhtxFormat.TAG_SIZE * 8, nonce))
            updateAAD(aad)
        }

    companion object {
        const val DEFAULT_MAX_IMAGE_SIZE: Long = 100L * 1024 * 1024
        private const val CHUNK = 64 * 1024
    }
}

private fun ByteArrayOutputStream.u16(v: Int) {
    write((v ushr 8) and 0xFF); write(v and 0xFF)
}

private fun ByteArrayOutputStream.u32(v: Long) {
    for (shift in intArrayOf(24, 16, 8, 0)) write(((v ushr shift) and 0xFF).toInt())
}

private fun ByteArrayOutputStream.u64(v: Long) {
    for (shift in 56 downTo 0 step 8) write(((v ushr shift) and 0xFF).toInt())
}

internal fun ratioJurisPutU32(target: ByteArray, v: Long) {
    target[0] = (v ushr 24).toByte(); target[1] = (v ushr 16).toByte()
    target[2] = (v ushr 8).toByte(); target[3] = v.toByte()
}

internal fun ratioJurisGetU32(src: ByteArray, at: Int): Long =
    (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (src[at + i].toLong() and 0xFF) }

fun ByteArray.ratioJurisHex(): String = joinToString("") { "%02x".format(it) }
