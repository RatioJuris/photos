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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class RatioJurisPipelineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cipher = ratioJurisCipher()
    private val pipeline = RatioJurisProtectionPipeline(cipher)

    private fun source(): Pair<File, ByteArray> {
        val bytes = ratioJurisPng()
        val f = tmp.newFile("IMG_0001.png").apply { writeBytes(bytes) }
        return f to bytes
    }

    @Test fun successfulProtectionCommitsVerifiedContainer() {
        val (src, bytes) = source()
        val target = File(tmp.root, "IMG_0001.phtx")
        val outcome = pipeline.ratioJurisProtect(ratioJurisRequest(src.readBytes()), PASSWORD, RatioJurisFileStaging(target))
        assertTrue(outcome is RatioJurisProtectionOutcome.Success)
        assertTrue(target.exists())
        assertFalse(File(tmp.root, "IMG_0001.phtx.partial").exists())
        val opened = cipher.ratioJurisOpen(RatioJurisPhtxReader.parse(target.readBytes()), PASSWORD)
        assertArrayEquals(bytes, opened.image)
        assertArrayEquals("pipeline never touches the source", bytes, src.readBytes())
    }

    // 18 + 19: write failure midway -> source intact, no container, no partial file
    @Test fun failedEncryptionPreservesSourceAndLeavesNoContainer() {
        val (src, bytes) = source()
        for (failAfter in listOf(0L, 10L, 200L, 5_000L, bytes.size / 2L)) {
            val target = File(tmp.root, "fail_$failAfter.phtx")
            val staging = object : RatioJurisStagedOutput<File> {
                val inner = RatioJurisFileStaging(target)
                override fun openOutput(): OutputStream = FailingStream(inner.openOutput(), failAfter)
                override fun openInput(): InputStream = inner.openInput()
                override fun stagedSize() = inner.stagedSize()
                override fun commit() = inner.commit()
                override fun abort() = inner.abort()
            }
            val outcome = pipeline.ratioJurisProtect(ratioJurisRequest(src.readBytes()), PASSWORD, staging)
            assertTrue(outcome is RatioJurisProtectionOutcome.Failure)
            assertFalse(target.exists())
            assertFalse(staging.inner.partial.exists())
            assertArrayEquals(bytes, src.readBytes())
        }
    }

    @Test fun corruptionDuringStagingIsCaughtByVerification() {
        val (src, bytes) = source()
        val target = File(tmp.root, "flip.phtx")
        val staging = object : RatioJurisStagedOutput<File> {
            val inner = RatioJurisFileStaging(target)
            override fun openOutput(): OutputStream = inner.openOutput()
            override fun openInput(): InputStream {
                // Simulate a storage bit flip on read-back.
                val data = inner.openInput().use { it.readBytes() }
                data[data.size - 40] = (data[data.size - 40].toInt() xor 1).toByte()
                return data.inputStream()
            }
            override fun stagedSize() = inner.stagedSize()
            override fun commit() = inner.commit()
            override fun abort() = inner.abort()
        }
        val outcome = pipeline.ratioJurisProtect(ratioJurisRequest(src.readBytes()), PASSWORD, staging)
        assertTrue(outcome is RatioJurisProtectionOutcome.Failure)
        assertTrue((outcome as RatioJurisProtectionOutcome.Failure).error is RatioJurisPhtxException.Corrupted)
        assertFalse(target.exists())
        assertArrayEquals(bytes, src.readBytes())
    }

    @Test fun existingTargetIsNeverOverwritten() {
        val (src, _) = source()
        val target = File(tmp.root, "exists.phtx").apply { writeText("keep") }
        val outcome = pipeline.ratioJurisProtect(ratioJurisRequest(src.readBytes()), PASSWORD, RatioJurisFileStaging(target))
        assertTrue(outcome is RatioJurisProtectionOutcome.Failure)
        assertEquals("keep", target.readText())
    }

    @Test fun emptySourceFailsSafely() {
        val target = File(tmp.root, "empty.phtx")
        val outcome = pipeline.ratioJurisProtect(ratioJurisRequest(ByteArray(0), withThumb = false), PASSWORD, RatioJurisFileStaging(target))
        assertTrue((outcome as RatioJurisProtectionOutcome.Failure).error is RatioJurisPhtxException.EmptyInput)
        assertFalse(target.exists())
    }

    // 20: plaintext temp exports are cleaned up
    @Test fun temporaryPlaintextCleanup() {
        var now = 1_000_000L
        val dir = tmp.newFolder("exports")
        val janitor = RatioJurisTempJanitor(dir, maxAgeMillis = 60_000, clock = { now })
        val old = janitor.ratioJurisNewFile("../../evil name?.jpg").apply { writeBytes(ByteArray(5000) { 9 }); setLastModified(now - 120_000) }
        assertEquals("path traversal neutralised", dir, old.parentFile)
        now += 1
        val fresh = janitor.ratioJurisNewFile("fresh.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(now) }
        assertEquals(1, janitor.ratioJurisSweepExpired())
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertEquals(1, janitor.ratioJurisPurgeAll())
        assertFalse(fresh.exists())
        assertEquals(0, dir.listFiles()!!.size)
    }

    private class FailingStream(out: OutputStream, private val failAfter: Long) : FilterOutputStream(out) {
        private var count = 0L
        override fun write(b: Int) {
            if (count++ >= failAfter) throw IOException("Simulated storage failure")
            out.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) write(b[i].toInt())
        }
        override fun close() {
            runCatching { out.close() }
        }
    }
}
