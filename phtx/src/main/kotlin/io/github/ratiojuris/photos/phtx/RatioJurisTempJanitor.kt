// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

import java.io.File
import java.io.RandomAccessFile

/**
 * Manages a directory holding short-lived plaintext exports (e.g. a decrypted original being
 * shared). Files are overwritten with zeros before deletion on a best-effort basis
 * (flash storage may still retain old blocks; see docs).
 */
class RatioJurisTempJanitor(
    private val directory: File,
    private val maxAgeMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun ratioJurisNewFile(name: String): File {
        directory.mkdirs()
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "photo" }
        val unique = File(directory, "${clock()}_$safe")
        return unique
    }

    /** Deletes files older than [maxAgeMillis]. Returns the number removed. */
    fun ratioJurisSweepExpired(): Int {
        val now = clock()
        return listFiles().count { now - it.lastModified() >= maxAgeMillis && ratioJurisShred(it) }
    }

    /** Deletes every file. Returns the number removed. */
    fun ratioJurisPurgeAll(): Int = listFiles().count { ratioJurisShred(it) }

    private fun listFiles(): List<File> = directory.listFiles()?.filter { it.isFile } ?: emptyList()

    companion object {
        fun ratioJurisShred(file: File): Boolean {
            runCatching {
                RandomAccessFile(file, "rw").use { raf ->
                    val zero = ByteArray(64 * 1024)
                    var remaining = raf.length()
                    raf.seek(0)
                    while (remaining > 0) {
                        val n = minOf(remaining, zero.size.toLong()).toInt()
                        raf.write(zero, 0, n)
                        remaining -= n
                    }
                    raf.fd.sync()
                }
            }
            return file.delete() || !file.exists()
        }
    }
}
