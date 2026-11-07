// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisDetection
import io.github.ratiojuris.photos.data.RatioJurisSettingsState
import io.github.ratiojuris.photos.ratioJurisContainer
import io.github.ratiojuris.photos.ui.components.RatioJurisCenteredMessage
import io.github.ratiojuris.photos.ui.components.RatioJurisLoading
import io.github.ratiojuris.photos.ui.gallery.RatioJurisGalleryViewModel
import io.github.ratiojuris.photos.ui.theme.RatioJurisTheme
import io.github.ratiojuris.photos.ui.theme.ratioJurisIsDark
import io.github.ratiojuris.photos.ui.viewer.RatioJurisViewerItem
import io.github.ratiojuris.photos.ui.viewer.RatioJurisViewerScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Status / navigation bar icons follow the app theme (which may differ from the system theme). */
@androidx.compose.runtime.Composable
private fun ratioJurisSystemBars(activity: ComponentActivity, dark: Boolean) {
    androidx.compose.runtime.SideEffect {
        val window = activity.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
class RatioJurisMainActivity : ComponentActivity() {
    private val vm: RatioJurisGalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            ratioJurisSystemBars(this, ratioJurisIsDark(settings.theme))
            RatioJurisTheme(settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RatioJurisPhotosRoot(vm)
                }
            }
        }
    }
}

/** Holds the result of inspecting an incoming file across configuration changes. */
class RatioJurisOpenViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.ratioJurisContainer
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, RatioJurisSettingsState())
    var detection by mutableStateOf<RatioJurisDetection?>(null)
        private set
    private var inspected: Uri? = null

    fun ratioJurisInspect(uri: Uri?) {
        if (uri == null) {
            detection = RatioJurisDetection.Unsupported("missing", null); return
        }
        if (uri == inspected) return
        inspected = uri
        detection = null
        viewModelScope.launch { detection = container.vault.ratioJurisDetect(uri) }
    }
}

/**
 * Entry point for files from file managers, messengers, e-mail, other galleries and the Sharesheet.
 * The file type is decided from its content; protected files require the password, ordinary images open directly.
 */
class RatioJurisOpenActivity : ComponentActivity() {
    private val vm: RatioJurisOpenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.ratioJurisInspect(ratioJurisIncomingUri(intent))
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            ratioJurisSystemBars(this, ratioJurisIsDark(settings.theme))
            RatioJurisTheme(settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val uri = ratioJurisIncomingUri(intent)
                    when (val d = vm.detection) {
                        null -> RatioJurisLoading()
                        is RatioJurisDetection.Unsupported -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RatioJurisCenteredMessage(
                                icon = Icons.Outlined.BrokenImage,
                                title = stringResource(R.string.open_unsupported_title),
                                body = stringResource(
                                    when (d.reason) {
                                        "version" -> R.string.error_unsupported_version
                                        "damaged" -> R.string.error_damaged
                                        "unreadable", "missing" -> R.string.open_unreadable
                                        else -> R.string.open_unsupported_body
                                    },
                                ),
                                primaryAction = stringResource(R.string.close) to { finish() },
                            )
                        }
                        is RatioJurisDetection.Ordinary -> RatioJurisViewerScreen(
                            items = listOf(RatioJurisViewerItem.Ordinary("x:$uri", uri!!, d.displayName ?: "", null, video = d.info.mimeType?.startsWith("video/") == true)),
                            initialKey = null, settings = settings, onBack = ::finish,
                            onProtect = null, onDeletePhoto = null, onLibraryChanged = {},
                        )
                        is RatioJurisDetection.Protected -> RatioJurisViewerScreen(
                            items = listOf(RatioJurisViewerItem.Protected("x:$uri", uri!!, d.displayName ?: "", null)),
                            initialKey = null, settings = settings, onBack = ::finish,
                            onProtect = null, onDeletePhoto = null, onLibraryChanged = {},
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.ratioJurisInspect(ratioJurisIncomingUri(intent))
    }

    private fun ratioJurisIncomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> intent.data
    }
}
