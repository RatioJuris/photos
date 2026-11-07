// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import io.github.ratiojuris.photos.data.RatioJurisImageFetchersRegistry
import io.github.ratiojuris.photos.data.RatioJurisMediaRepository
import io.github.ratiojuris.photos.data.RatioJurisOperations
import io.github.ratiojuris.photos.data.RatioJurisSettingsRepository
import io.github.ratiojuris.photos.data.RatioJurisSharing
import io.github.ratiojuris.photos.data.RatioJurisVaultRepository
import io.github.ratiojuris.photos.phtx.RatioJurisKdfParams
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxCipher
import io.github.ratiojuris.photos.phtx.RatioJurisTempJanitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom

/** Manual dependency container; the app is small enough not to need a DI framework. */
class RatioJurisAppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = RatioJurisSettingsRepository(context)
    val media = RatioJurisMediaRepository(context)
    val cipher = RatioJurisPhtxCipher(RatioJurisKdfParams.DEFAULT, SecureRandom())
    val vault = RatioJurisVaultRepository(context, cipher)
    val operations = RatioJurisOperations(vault, appScope)
    val location = io.github.ratiojuris.photos.data.RatioJurisLocationRepository(context)
    val tiles = io.github.ratiojuris.photos.data.RatioJurisTileCache(context)
    val sharing = RatioJurisSharing(
        context,
        exportJanitor = RatioJurisTempJanitor(File(context.cacheDir, "exports"), EXPORT_MAX_AGE_MS),
        sharedJanitor = RatioJurisTempJanitor(File(context.cacheDir, "shared"), EXPORT_MAX_AGE_MS),
    )

    companion object {
        /** Decrypted exports live at most this long; also purged at every app start. */
        const val EXPORT_MAX_AGE_MS = 15 * 60_000L
    }
}

class RatioJurisPhotosApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: RatioJurisAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = RatioJurisAppContainer(this)
        container.appScope.launch {
            // Remove leftovers from interrupted work and any plaintext exports from earlier sessions.
            container.vault.ratioJurisCleanupStalePending()
            container.sharing.exportJanitor.ratioJurisPurgeAll()
            RatioJurisTempJanitor(File(cacheDir, "shared"), 0).ratioJurisPurgeAll()
            runCatching { container.tiles.ratioJurisTrim() }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { RatioJurisImageFetchersRegistry.ratioJurisRegister(this, context) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            // No fade while scrolling the grid (iOS shows thumbnails immediately).
            .crossfade(false)
            .build()
}

val Context.ratioJurisContainer: RatioJurisAppContainer
    get() = (applicationContext as RatioJurisPhotosApp).container
