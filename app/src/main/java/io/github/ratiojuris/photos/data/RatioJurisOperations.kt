// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.net.Uri
import android.util.Log
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxException
import io.github.ratiojuris.photos.phtx.RatioJurisProtectionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RatioJurisOpState {
    data object Idle : RatioJurisOpState
    data class Running(val done: Int, val total: Int) : RatioJurisOpState
    data class Finished(
        val succeeded: Int,
        val failures: List<RatioJurisOpFailure>,
        /** Originals that were protected and verified, and that the user asked to remove. */
        val originalsToRemove: List<Uri>,
    ) : RatioJurisOpState
}

data class RatioJurisOpFailure(val name: String, val reason: RatioJurisFailureReason)

enum class RatioJurisFailureReason { TOO_LARGE, EMPTY, UNREADABLE, UNSUPPORTED_FORMAT, STORAGE, UNKNOWN }

/**
 * Process-scoped protection jobs, so leaving a screen or rotating does not cancel encryption.
 * The password copy is wiped when the batch ends.
 */
class RatioJurisOperations(
    private val vault: RatioJurisVaultRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RatioJurisOpState>(RatioJurisOpState.Idle)
    val state: StateFlow<RatioJurisOpState> = _state.asStateFlow()

    fun ratioJurisIsBusy(): Boolean = _state.value is RatioJurisOpState.Running

    fun ratioJurisProtect(
        photos: List<RatioJurisPhoto>,
        password: CharArray,
        policy: RatioJurisThumbnailPolicy,
        removeOriginals: Boolean,
    ) {
        if (ratioJurisIsBusy() || photos.isEmpty()) {
            password.fill('\u0000'); return
        }
        val secret = password.copyOf()
        password.fill('\u0000')
        _state.value = RatioJurisOpState.Running(0, photos.size)
        scope.launch {
            val failures = ArrayList<RatioJurisOpFailure>()
            val protectedOriginals = ArrayList<Uri>()
            try {
                photos.forEachIndexed { index, photo ->
                    when (val outcome = vault.ratioJurisProtect(photo, secret, policy)) {
                        is RatioJurisProtectionOutcome.Success -> protectedOriginals += photo.uri
                        is RatioJurisProtectionOutcome.Failure -> {
                            Log.w(TAG, "Protection failed: ${outcome.error.javaClass.simpleName}")
                            failures += RatioJurisOpFailure(photo.displayName, ratioJurisReason(outcome.error))
                        }
                    }
                    _state.value = RatioJurisOpState.Running(index + 1, photos.size)
                }
            } finally {
                secret.fill('\u0000')
                _state.value = RatioJurisOpState.Finished(
                    succeeded = protectedOriginals.size,
                    failures = failures,
                    originalsToRemove = if (removeOriginals) protectedOriginals else emptyList(),
                )
            }
        }
    }

    fun ratioJurisAcknowledge() {
        if (_state.value is RatioJurisOpState.Finished) _state.value = RatioJurisOpState.Idle
    }

    companion object {
        private const val TAG = "PhotosOps"

        fun ratioJurisReason(e: Throwable): RatioJurisFailureReason = when (e) {
            is RatioJurisPhtxException.TooLarge -> RatioJurisFailureReason.TOO_LARGE
            is RatioJurisPhtxException.EmptyInput -> RatioJurisFailureReason.EMPTY
            is java.io.FileNotFoundException, is SecurityException -> RatioJurisFailureReason.UNREADABLE
            is android.graphics.ImageDecoder.DecodeException -> RatioJurisFailureReason.UNSUPPORTED_FORMAT
            is java.io.IOException, is IllegalStateException -> RatioJurisFailureReason.STORAGE
            is OutOfMemoryError -> RatioJurisFailureReason.TOO_LARGE
            else -> RatioJurisFailureReason.UNKNOWN
        }
    }
}
