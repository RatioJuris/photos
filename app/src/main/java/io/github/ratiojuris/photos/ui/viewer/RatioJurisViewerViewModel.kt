// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.viewer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisImaging
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedPhoto
import io.github.ratiojuris.photos.phtx.RatioJurisDecryptedImage
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxException
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxHeader
import io.github.ratiojuris.photos.ratioJurisContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One page of the viewer. */
@Immutable
sealed interface RatioJurisViewerItem {
    val key: String
    val uri: Uri
    val name: String

    /** Ordinary image. [photo] is present when it comes from the device library. */
    data class Ordinary(override val key: String, override val uri: Uri, override val name: String, val photo: RatioJurisPhoto?, val video: Boolean = false) : RatioJurisViewerItem {
        val isVideo: Boolean get() = video || photo?.isVideo == true
    }

    /** Protected container. [stored] is present when it lives in the Photos protected folder. */
    data class Protected(override val key: String, override val uri: Uri, override val name: String, val stored: RatioJurisProtectedPhoto?) : RatioJurisViewerItem
}

/** The authenticated original, held only in memory while the viewer shows it. */
class RatioJurisUnlocked(val key: String, val bitmap: ImageBitmap, val decrypted: RatioJurisDecryptedImage)

sealed interface RatioJurisUnlockUi {
    data object None : RatioJurisUnlockUi
    data class Password(val key: String, val busy: Boolean = false) : RatioJurisUnlockUi
}

enum class RatioJurisPendingAction { NONE, SHARE_ORIGINAL, RESTORE }

class RatioJurisViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.ratioJurisContainer
    private val vault get() = container.vault

    val headers = mutableStateMapOf<String, Result<RatioJurisPhtxHeader>>()
    var unlocked by mutableStateOf<RatioJurisUnlocked?>(null)
        private set
    var unlockUi by mutableStateOf<RatioJurisUnlockUi>(RatioJurisUnlockUi.None)
    var pendingAction by mutableStateOf(RatioJurisPendingAction.NONE)
    var changePasswordState by mutableStateOf<Pair<Boolean, String?>?>(null) // busy, error
    var message by mutableStateOf<String?>(null)
    private var unlockJob: Job? = null

    fun ratioJurisEnsureHeader(item: RatioJurisViewerItem.Protected) {
        if (headers.containsKey(item.key)) return
        viewModelScope.launch {
            headers[item.key] = runCatching { vault.ratioJurisReadHeader(item.uri) }
        }
    }

    /** The discreet "Continue Securely" action: asks for the password, nothing else. */
    fun ratioJurisRequestUnlock(key: String, action: RatioJurisPendingAction = RatioJurisPendingAction.NONE) {
        pendingAction = action
        unlockUi = RatioJurisUnlockUi.Password(key)
    }

    fun ratioJurisUnlock(item: RatioJurisViewerItem.Protected, password: CharArray) {
        val ctx = getApplication<Application>()
        unlockUi = RatioJurisUnlockUi.Password(item.key, busy = true)
        unlockJob?.cancel()
        unlockJob = viewModelScope.launch {
            try {
                val decrypted = vault.ratioJurisUnlock(item.uri, password)
                val bitmap = try {
                    withContext(Dispatchers.Default) { RatioJurisImaging.ratioJurisDecodeBounded(decrypted.image, MAX_VIEW_DIM).first.asImageBitmap() }
                } catch (e: Exception) {
                    decrypted.ratioJurisWipe()
                    throw e
                }
                ratioJurisRelock()
                unlocked = RatioJurisUnlocked(item.key, bitmap, decrypted)
                unlockUi = RatioJurisUnlockUi.None
            } catch (e: RatioJurisPhtxException.WrongPassword) {
                // Privacy by design: no warning. The dialog closes and the preview simply stays.
                unlockUi = RatioJurisUnlockUi.None
                pendingAction = RatioJurisPendingAction.NONE
            } catch (e: RatioJurisPhtxException) {
                unlockUi = RatioJurisUnlockUi.None
                pendingAction = RatioJurisPendingAction.NONE
                message = ctx.getString(ratioJurisErrorText(e))
            } catch (e: OutOfMemoryError) {
                unlockUi = RatioJurisUnlockUi.None
                message = ctx.getString(R.string.error_too_large)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unlock failed: ${e.javaClass.simpleName}")
                unlockUi = RatioJurisUnlockUi.None
                pendingAction = RatioJurisPendingAction.NONE
                message = ctx.getString(R.string.error_open_failed)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun ratioJurisDismissUnlock() {
        unlockJob?.cancel()
        unlockUi = RatioJurisUnlockUi.None
        pendingAction = RatioJurisPendingAction.NONE
    }

    /** Drops the decrypted original from memory. */
    fun ratioJurisRelock() {
        unlocked?.decrypted?.ratioJurisWipe()
        unlocked = null
    }

    fun ratioJurisShareOriginal(activity: Context) {
        val u = unlocked ?: return
        viewModelScope.launch {
            runCatching { container.sharing.ratioJurisShareOriginal(activity, u.decrypted) }
                .onFailure { message = activity.getString(R.string.error_share_failed) }
        }
    }

    fun ratioJurisShareThumbnail(activity: Context, item: RatioJurisViewerItem.Protected) {
        val thumb = headers[item.key]?.getOrNull()?.thumbnail ?: run {
            message = activity.getString(R.string.error_no_thumbnail); return
        }
        viewModelScope.launch {
            runCatching { container.sharing.ratioJurisShareThumbnail(activity, thumb, item.name) }
                .onFailure { message = activity.getString(R.string.error_share_failed) }
        }
    }

    fun ratioJurisRestore(onDone: (Uri) -> Unit) {
        val u = unlocked ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            runCatching { vault.ratioJurisRestore(u.decrypted) }
                .onSuccess { message = ctx.getString(R.string.restored_message); onDone(it) }
                .onFailure { message = ctx.getString(R.string.error_restore_failed) }
        }
    }

    fun ratioJurisChangePassword(item: RatioJurisProtectedPhoto, old: CharArray, new: CharArray, onDone: () -> Unit) {
        val ctx = getApplication<Application>()
        changePasswordState = true to null
        viewModelScope.launch {
            try {
                vault.ratioJurisChangePassword(item, old, new)
                changePasswordState = null
                ratioJurisRelock()
                message = ctx.getString(R.string.password_changed)
                onDone()
            } catch (e: RatioJurisPhtxException.WrongPassword) {
                changePasswordState = false to ctx.getString(R.string.error_wrong_password)
            } catch (e: Exception) {
                Log.w(TAG, "Password change failed: ${e.javaClass.simpleName}")
                changePasswordState = null
                message = ctx.getString(R.string.error_change_password)
            } finally {
                old.fill('\u0000'); new.fill('\u0000')
            }
        }
    }

    fun ratioJurisDeleteProtected(item: RatioJurisProtectedPhoto, onDone: () -> Unit) {
        viewModelScope.launch {
            if (vault.ratioJurisDelete(item)) {
                if (unlocked?.key == "p:${item.id}") ratioJurisRelock()
                onDone()
            } else {
                message = getApplication<Application>().getString(R.string.error_delete_failed)
            }
        }
    }

    fun ratioJurisImport(uri: Uri, onDone: () -> Unit) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            runCatching { vault.ratioJurisImport(uri) }
                .onSuccess { message = ctx.getString(R.string.imported_message); onDone() }
                .onFailure { message = ctx.getString(R.string.error_import_failed) }
        }
    }

    override fun onCleared() {
        ratioJurisRelock()
        super.onCleared()
    }

    companion object {
        private const val TAG = "PhotosViewer"
        const val MAX_VIEW_DIM = 4096

        fun ratioJurisErrorText(e: RatioJurisPhtxException): Int = when (e) {
            is RatioJurisPhtxException.WrongPassword -> R.string.error_wrong_password
            is RatioJurisPhtxException.Corrupted -> R.string.error_corrupted
            is RatioJurisPhtxException.Unsupported -> R.string.error_unsupported_version
            is RatioJurisPhtxException.TooLarge -> R.string.error_too_large
            is RatioJurisPhtxException.NotProtectedImage, is RatioJurisPhtxException.Malformed -> R.string.error_damaged
            is RatioJurisPhtxException.EmptyInput -> R.string.error_damaged
        }
    }
}
