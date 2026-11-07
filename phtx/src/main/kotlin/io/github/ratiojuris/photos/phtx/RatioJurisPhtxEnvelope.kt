// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

/** Where the PHTX block lives inside a file. */
data class RatioJurisBlockLocation(val offset: Long, val length: Long, val carrier: Boolean)

/**
 * Image-carrier envelope (PHTX v1 specification, section "Carrier envelope").
 *
 *   [carrier image, e.g. a baseline JPEG of the public preview]
 *   [PHTX block, exactly as in a bare .phtx file]
 *   [24-byte trailer: "PHTXTRL1" | u64 block offset | u64 block length]   (big-endian)
 *
 * Image decoders stop at the JPEG end-of-image marker, so other apps display only the preview.
 * A bare PHTX file (magic at offset 0, no carrier, no trailer) is also accepted.
 */
object RatioJurisPhtxEnvelope {
    val TRAILER_MAGIC: ByteArray = "PHTXTRL1".toByteArray(Charsets.US_ASCII)
    const val TRAILER_SIZE: Int = 24

    fun ratioJurisTrailer(blockOffset: Long, blockLength: Long): ByteArray {
        val b = ByteBuffer.allocate(TRAILER_SIZE)
        b.put(TRAILER_MAGIC).putLong(blockOffset).putLong(blockLength)
        return b.array()
    }

    /** Writes carrier + block + trailer. [writeBlock] must return the number of block bytes it wrote. */
    fun ratioJurisWrite(out: OutputStream, carrier: ByteArray, writeBlock: (OutputStream) -> Long): Long {
        require(carrier.isNotEmpty()) { "Carrier image required" }
        out.write(carrier)
        val blockLength = writeBlock(out)
        out.write(ratioJurisTrailer(carrier.size.toLong(), blockLength))
        out.flush()
        return carrier.size + blockLength + TRAILER_SIZE
    }

    /** Finds the PHTX block, or returns null if this is not a protected image. Never trusts the trailer blindly. */
    fun ratioJurisLocate(channel: SeekableByteChannel): RatioJurisBlockLocation? {
        val size = channel.size()
        if (size < RatioJurisPhtxFormat.MAGIC.size) return null
        val head = readAt(channel, 0, RatioJurisPhtxFormat.MAGIC.size)
        if (RatioJurisPhtxFormat.hasMagic(head)) return RatioJurisBlockLocation(0, size, carrier = false)
        if (size < TRAILER_SIZE + RatioJurisPhtxFormat.FIXED_HEADER_SIZE + 2) return null
        val t = ByteBuffer.wrap(readAt(channel, size - TRAILER_SIZE, TRAILER_SIZE))
        val magic = ByteArray(8).also { t.get(it) }
        if (!magic.contentEquals(TRAILER_MAGIC)) return null
        val offset = t.long
        val length = t.long
        if (offset < 2 || length < RatioJurisPhtxFormat.FIXED_HEADER_SIZE || offset + length + TRAILER_SIZE != size) return null
        if (!RatioJurisPhtxFormat.hasMagic(readAt(channel, offset, RatioJurisPhtxFormat.MAGIC.size))) return null
        return RatioJurisBlockLocation(offset, length, carrier = true)
    }

    /** Stream over exactly the block bytes. The channel must stay open while reading. */
    fun ratioJurisBlockStream(channel: SeekableByteChannel, location: RatioJurisBlockLocation): InputStream {
        channel.position(location.offset)
        return RatioJurisBoundedChannelStream(channel, location.length)
    }

    fun ratioJurisReadHeader(channel: SeekableByteChannel, maxPayloadSize: Long = RatioJurisPhtxFormat.MAX_PAYLOAD_SIZE): RatioJurisPhtxHeader {
        val loc = ratioJurisLocate(channel) ?: throw RatioJurisPhtxException.NotProtectedImage()
        return RatioJurisPhtxReader.readHeader(ratioJurisBlockStream(channel, loc).buffered(64 * 1024), maxPayloadSize, loc.length)
    }

    fun ratioJurisReadContainer(channel: SeekableByteChannel, maxPayloadSize: Long): RatioJurisPhtxContainer {
        val loc = ratioJurisLocate(channel) ?: throw RatioJurisPhtxException.NotProtectedImage()
        return RatioJurisPhtxReader.readContainer(ratioJurisBlockStream(channel, loc).buffered(64 * 1024), maxPayloadSize, loc.length)
    }

    private fun readAt(channel: SeekableByteChannel, position: Long, n: Int): ByteArray {
        val buf = ByteBuffer.allocate(n)
        channel.position(position)
        while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) throw RatioJurisPhtxException.Malformed("truncated")
        }
        return buf.array()
    }
}

private class RatioJurisBoundedChannelStream(private val channel: SeekableByteChannel, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = minOf(len.toLong(), remaining).toInt()
        val r = channel.read(ByteBuffer.wrap(b, off, n))
        if (r < 0) throw IOException("Unexpected end of file")
        remaining -= r
        return r
    }
}

/** Read-only in-memory channel (tests, and sources that cannot be opened as files). */
class RatioJurisByteArrayChannel(private val bytes: ByteArray) : SeekableByteChannel {
    private var pos = 0L
    private var open = true
    override fun read(dst: ByteBuffer): Int {
        if (pos >= bytes.size) return -1
        val n = minOf(dst.remaining().toLong(), bytes.size - pos).toInt()
        dst.put(bytes, pos.toInt(), n)
        pos += n
        return n
    }
    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun position(): Long = pos
    override fun position(newPosition: Long): SeekableByteChannel = apply { pos = newPosition }
    override fun size(): Long = bytes.size.toLong()
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
}
