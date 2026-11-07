// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ratiojuris.photos.data.RatioJurisDetection
import io.github.ratiojuris.photos.data.RatioJurisImaging
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisThumbnailPolicy
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxEnvelope
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxException
import io.github.ratiojuris.photos.phtx.RatioJurisProtectionOutcome
import io.github.ratiojuris.photos.ui.RatioJurisMainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Runs on a real device: MediaStore, ImageDecoder, Argon2id with production parameters, the UI. */
@RunWith(AndroidJUnit4::class)
class RatioJurisVaultDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = context.ratioJurisContainer
    private val resolver get() = context.contentResolver
    private val created = mutableListOf<Uri>()
    private val password = "Instrumented-Test-2026"

    @Before fun grant() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val perms = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        perms.forEach { ui.grantRuntimePermission(context.packageName, it) }
    }

    @After fun cleanup() {
        created.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    private fun insertTestJpeg(name: String, w: Int = 2000, h: Int = 1500): Pair<Uri, ByteArray> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(0xFF2266AA.toInt())
            drawCircle(w / 2f, h / 2f, h / 3f, Paint().apply { color = 0xFFFFCC00.toInt() })
        }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotosInstrumentationTest")
        })!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        created += uri
        return uri to bytes
    }

    private fun photoFor(uri: Uri, size: Long) = RatioJurisPhoto(
        id = uri.lastPathSegment!!.toLong(), uri = uri, displayName = "test.jpg", bucketId = "", bucketName = "",
        relativePath = null, mimeType = "image/jpeg", width = 2000, height = 1500, orientation = 0,
        dateTakenMillis = null, dateModifiedMillis = 0, sizeBytes = size,
    )

    @Test fun protectsIntoCarrierThatOtherAppsSeeAsPreviewOnly() = runBlocking {
        val (uri, original) = insertTestJpeg("rj_instrumented_${System.nanoTime()}.jpg")
        val outcome = container.vault.ratioJurisProtect(photoFor(uri, original.size.toLong()), password.toCharArray(), RatioJurisThumbnailPolicy.STANDARD)
        assertTrue("protect failed: $outcome", outcome is RatioJurisProtectionOutcome.Success)
        val carrier = (outcome as RatioJurisProtectionOutcome.Success).location
        created += carrier

        // 1. The platform decoder (what any gallery/viewer uses) sees only the small preview.
        val preview = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, carrier))
        assertTrue("preview too large: ${preview.width}x${preview.height}", maxOf(preview.width, preview.height) <= RatioJurisImaging.STANDARD_THUMB_MAX)

        // 2. Photos finds the encrypted block by content.
        val located = container.vault.ratioJurisWithChannel(carrier) { RatioJurisPhtxEnvelope.ratioJurisLocate(it) }
        assertNotNull(located); assertTrue(located!!.carrier)
        assertTrue(container.vault.ratioJurisDetect(carrier) is RatioJurisDetection.Protected)

        // 3. Wrong password never reveals the original.
        try {
            container.vault.ratioJurisUnlock(carrier, "wrong-password".toCharArray())
            fail("wrong password accepted")
        } catch (e: RatioJurisPhtxException.WrongPassword) { /* expected */ }

        // 4. Correct password returns the exact original bytes.
        val decrypted = container.vault.ratioJurisUnlock(carrier, password.toCharArray())
        assertArrayEquals(original, decrypted.image)

        // 5. It is listed as a protected photo.
        assertTrue(container.vault.ratioJurisLoadProtected().any { it.uri == carrier && it.imageBacked })
    }

    @Test fun ordinaryImagesAreDetectedAsOrdinary() = runBlocking {
        val (uri, _) = insertTestJpeg("rj_ordinary_${System.nanoTime()}.jpg", 400, 300)
        val d = container.vault.ratioJurisDetect(uri)
        assertTrue("got $d", d is RatioJurisDetection.Ordinary)
        assertNull(container.vault.ratioJurisWithChannel(uri) { RatioJurisPhtxEnvelope.ratioJurisLocate(it) })
    }

    @Test fun thumbnailPoliciesProduceExpectedPreviews() {
        val (_, original) = insertTestJpeg("rj_thumb_${System.nanoTime()}.jpg", 1600, 1200)
        val (standard, info) = RatioJurisImaging.ratioJurisMakeThumbnail(original, RatioJurisThumbnailPolicy.STANDARD)
        assertEquals(1600, info.width)
        assertEquals(480, maxOf(standard!!.width, standard.height))
        val (reduced, _) = RatioJurisImaging.ratioJurisMakeThumbnail(original, RatioJurisThumbnailPolicy.REDUCED)
        assertTrue(maxOf(reduced!!.width, reduced.height) <= 192)
        val (neutral, _) = RatioJurisImaging.ratioJurisMakeThumbnail(original, RatioJurisThumbnailPolicy.NEUTRAL)
        assertEquals(256, neutral!!.width)
        assertNull(RatioJurisImaging.ratioJurisMakeThumbnail(original, RatioJurisThumbnailPolicy.NONE).first)
        // Thumbnails are re-encoded from pixels: no EXIF block copied from the original.
        assertTrue(standard.bytes.indexOfExif() < 0)
    }

    @Test fun temporaryPlaintextExportsAreCleanedUp() {
        val janitor = container.sharing.exportJanitor
        val f = janitor.ratioJurisNewFile("secret.jpg").apply { writeBytes(ByteArray(4096) { 7 }) }
        assertTrue(f.exists())
        janitor.ratioJurisPurgeAll()
        assertTrue(!f.exists())
    }

    private fun ByteArray.indexOfExif(): Int {
        val marker = "Exif".toByteArray()
        outer@ for (i in 0..size - marker.size) {
            for (j in marker.indices) if (this[i + j] != marker[j]) continue@outer
            return i
        }
        return -1
    }
}

/** UI smoke test of the iOS-style shell. */
@RunWith(AndroidJUnit4::class)
class RatioJurisUiTest {
    init {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, perm)
    }

    @get:Rule val compose = createAndroidComposeRule<RatioJurisMainActivity>()

    @Test fun tabsAndUtilitiesAreReachable() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTextExists("Library") }
        compose.onNodeWithText("Albums").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTextExists("Utilities") }
        compose.onNodeWithText("Search").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTextExists("Search names, albums, months") }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
