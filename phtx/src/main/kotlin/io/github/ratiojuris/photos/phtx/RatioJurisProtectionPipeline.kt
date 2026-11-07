// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * A two-phase destination for a new container. Nothing written to a staged output may be
 * visible as a finished protected image until [commit] succeeds; [abort] must remove all traces.
 */
interface RatioJurisStagedOutput<T> {
    fun openOutput(): OutputStream
    fun openInput(): InputStream
    fun stagedSize(): Long
    fun commit(): T
    fun abort()
}

sealed interface RatioJurisProtectionOutcome<out T> {
    /** Container written, re-read, authenticated, byte-compared and committed. The source may now be removed. */
    data class Success<T>(val location: T, val bytesWritten: Long) : RatioJurisProtectionOutcome<T>

    /** Nothing was committed and the source must be left untouched. */
    data class Failure(val error: Throwable) : RatioJurisProtectionOutcome<Nothing>
}

/**
 * Safe protect workflow:
 *  1. encrypt into a staged (invisible) output;
 *  2. read the staged bytes back, parse strictly, authenticate and decrypt them;
 *  3. compare the decrypted bytes with the source byte-for-byte;
 *  4. commit only if every check passed, otherwise abort.
 * The pipeline never deletes the source; callers do that only after [RatioJurisProtectionOutcome.Success].
 */
class RatioJurisProtectionPipeline(private val cipher: RatioJurisPhtxCipher) {

    /**
     * @param carrier optional image (normally a JPEG of the public preview) placed in front of the block so
     * the file is an ordinary image for other apps; null writes a bare PHTX file.
     */
    fun <T> ratioJurisProtect(
        request: RatioJurisProtectRequest,
        password: CharArray,
        staging: RatioJurisStagedOutput<T>,
        carrier: ByteArray? = null,
    ): RatioJurisProtectionOutcome<T> {
        var committed = false
        try {
            var result: RatioJurisProtectResult? = null
            BufferedOutputStream(staging.openOutput()).use { out ->
                if (carrier != null) {
                    RatioJurisPhtxEnvelope.ratioJurisWrite(out, carrier) { o ->
                        cipher.ratioJurisProtect(o, request, password).also { result = it }.bytesWritten
                    }
                } else {
                    result = cipher.ratioJurisProtect(out, request, password)
                }
            }
            val written = result!!
            written.verificationKey.use { key -> ratioJurisVerify(staging, key, request.image, written.bytesWritten, carrier) }
            val location = staging.commit()
            committed = true
            val total = written.bytesWritten + (carrier?.let { it.size + RatioJurisPhtxEnvelope.TRAILER_SIZE } ?: 0)
            return RatioJurisProtectionOutcome.Success(location, total.toLong())
        } catch (t: Throwable) {
            if (t is VirtualMachineError && t !is OutOfMemoryError) throw t
            return RatioJurisProtectionOutcome.Failure(t)
        } finally {
            if (!committed) runCatching { staging.abort() }
        }
    }

    private fun ratioJurisVerify(
        staging: RatioJurisStagedOutput<*>,
        key: RatioJurisSecretKey,
        expected: ByteArray,
        blockLength: Long,
        carrier: ByteArray?,
    ) {
        val envelope = carrier?.let { it.size + RatioJurisPhtxEnvelope.TRAILER_SIZE } ?: 0
        if (staging.stagedSize() != blockLength + envelope) throw RatioJurisVerificationException("staged size mismatch")
        val container = BufferedInputStream(staging.openInput()).use { input ->
            if (carrier != null) {
                val head = ByteArray(carrier.size).also { RatioJurisPhtxReader.readFully(input, it) }
                if (!head.contentEquals(carrier)) throw RatioJurisVerificationException("carrier mismatch")
            }
            val c = RatioJurisPhtxReader.readContainer(RatioJurisBoundedStream(input, blockLength), cipher.maxPayloadSize, blockLength)
            if (carrier != null) {
                val trailer = ByteArray(RatioJurisPhtxEnvelope.TRAILER_SIZE).also { RatioJurisPhtxReader.readFully(input, it) }
                if (!trailer.contentEquals(RatioJurisPhtxEnvelope.ratioJurisTrailer(carrier.size.toLong(), blockLength))) {
                    throw RatioJurisVerificationException("trailer mismatch")
                }
                if (input.read() != -1) throw RatioJurisVerificationException("trailing data")
            }
            c
        }
        val decrypted = cipher.ratioJurisOpenWithKey(container, key)
        try {
            if (!MessageDigest.isEqual(decrypted.image, expected)) throw RatioJurisVerificationException("round-trip mismatch")
        } finally {
            decrypted.ratioJurisWipe()
        }
    }
}

internal class RatioJurisBoundedStream(private val input: InputStream, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        val r = input.read()
        if (r >= 0) remaining--
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val r = input.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (r > 0) remaining -= r
        return r
    }
}
class RatioJurisVerificationException(message: String) : Exception("Verification failed: $message")

/**
 * File-system staging: writes "<name>.partial" next to the target and atomically renames on commit.
 * Used by tests and by any desktop implementation; Android uses a MediaStore-pending equivalent.
 */
class RatioJurisFileStaging(private val target: File) : RatioJurisStagedOutput<File> {
    val partial: File = File(target.parentFile, target.name + PARTIAL_SUFFIX)

    override fun openOutput(): OutputStream {
        if (target.exists()) throw IllegalStateException("Target already exists")
        return partial.outputStream()
    }

    override fun openInput(): InputStream = partial.inputStream()
    override fun stagedSize(): Long = partial.length()

    override fun commit(): File {
        if (!partial.renameTo(target)) throw IllegalStateException("Could not finalise protected file")
        return target
    }

    override fun abort() {
        partial.delete()
    }

    companion object {
        const val PARTIAL_SUFFIX = ".partial"
    }
}
