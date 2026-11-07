// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.components

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisFailureReason
import io.github.ratiojuris.photos.data.RatioJurisOpState
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes MediaStore photos with the user's consent, using the platform confirmation:
 * Android 11+ batches via createDeleteRequest; Android 10 asks per item through
 * RecoverableSecurityException (device-test build only).
 */
class RatioJurisPhotoDeleter internal constructor(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val launch: (IntentSenderRequest) -> Unit,
    private val onFinished: (Boolean) -> Unit,
) {
    private val queue = ArrayDeque<Uri>()

    /**
     * Deletes [uris]. On Android 11+ with [toBin] the items go to the system Bin (restorable for ~30 days);
     * otherwise they are erased. Either way the platform shows its own confirmation.
     */
    fun ratioJurisDelete(uris: List<Uri>, toBin: Boolean = false) {
        if (uris.isEmpty()) return onFinished(true)
        if (Build.VERSION.SDK_INT >= 30) {
            val sender = if (toBin) MediaStore.createTrashRequest(resolver, uris, true).intentSender
            else MediaStore.createDeleteRequest(resolver, uris).intentSender
            launch(IntentSenderRequest.Builder(sender).build())
        } else {
            queue.clear(); queue.addAll(uris)
            next()
        }
    }

    /** Android 11+: takes items back out of the Bin. */
    fun ratioJurisRestore(uris: List<Uri>) {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < 30) return onFinished(true)
        launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(resolver, uris, false).intentSender).build())
    }

    internal fun onResult(ok: Boolean) {
        if (Build.VERSION.SDK_INT >= 30 || !ok) {
            queue.clear(); onFinished(ok); return
        }
        next()
    }

    private fun next() {
        scope.launch {
            while (queue.isNotEmpty()) {
                val uri = queue.first()
                val sender = withContext(Dispatchers.IO) {
                    try {
                        resolver.delete(uri, null, null); null
                    } catch (e: RecoverableSecurityException) {
                        e.userAction.actionIntent.intentSender
                    } catch (e: SecurityException) {
                        null
                    }
                }
                if (sender != null) {
                    launch(IntentSenderRequest.Builder(sender).build())
                    return@launch
                }
                queue.removeFirst()
            }
            onFinished(true)
        }
    }
}

@Composable
fun rememberRatioJurisPhotoDeleter(scope: CoroutineScope, onFinished: (Boolean) -> Unit): RatioJurisPhotoDeleter {
    val context = LocalContext.current
    var deleterRef: RatioJurisPhotoDeleter? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        deleterRef?.onResult(result.resultCode == Activity.RESULT_OK)
    }
    val deleter = remember(launcher) {
        RatioJurisPhotoDeleter(context.contentResolver, scope, { launcher.launch(it) }, onFinished)
    }
    deleterRef = deleter
    return deleter
}

/** Non-dismissable progress while protection runs, then a summary with an explicit choice about originals. */
@Composable
fun RatioJurisOperationDialogs(
    state: RatioJurisOpState,
    onRemoveOriginals: (List<Uri>) -> Unit,
    onAcknowledge: () -> Unit,
) {
    val ios = LocalRatioJurisIos.current
    when (state) {
        RatioJurisOpState.Idle -> Unit
        is RatioJurisOpState.Running -> RatioJurisAlert(
            title = stringResource(R.string.protecting_title),
            message = stringResource(R.string.protecting_progress, (state.done + 1).coerceAtMost(state.total), state.total),
            buttons = emptyList(),
            dismissible = false,
            onDismiss = {},
        ) {
            LinearProgressIndicator(
                progress = { state.done / state.total.toFloat() },
                color = ios.tint,
                trackColor = ios.fill,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.protecting_hint), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel, textAlign = TextAlign.Center)
        }
        is RatioJurisOpState.Finished -> {
            val remove = state.originalsToRemove
            val title = if (state.failures.isEmpty()) pluralStringResource(R.plurals.protect_done, state.succeeded, state.succeeded)
            else stringResource(R.string.protect_partial, state.succeeded, state.failures.size)
            val message = when {
                remove.isNotEmpty() -> pluralStringResource(R.plurals.remove_originals_prompt, remove.size, remove.size)
                state.succeeded > 0 -> stringResource(R.string.originals_kept)
                else -> null
            }
            RatioJurisAlert(
                title = title,
                message = message,
                onDismiss = onAcknowledge,
                buttons = if (remove.isNotEmpty()) {
                    listOf(
                        RatioJurisAlertButton(stringResource(R.string.keep_originals), RatioJurisButtonStyle.CANCEL, onClick = onAcknowledge),
                        RatioJurisAlertButton(stringResource(R.string.remove_originals_action), RatioJurisButtonStyle.DESTRUCTIVE) { onRemoveOriginals(remove); onAcknowledge() },
                    )
                } else {
                    listOf(RatioJurisAlertButton(stringResource(R.string.done), RatioJurisButtonStyle.PREFERRED, onClick = onAcknowledge))
                },
            ) {
                if (state.failures.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.protect_failures_intro), style = MaterialTheme.typography.bodySmall, color = ios.label)
                        Spacer(Modifier.height(4.dp))
                        state.failures.take(6).forEach { f ->
                            Text("• ${f.name}: ${stringResource(ratioJurisReasonText(f.reason))}", style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
                        }
                        if (state.failures.size > 6) Text("…", style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
                    }
                }
            }
        }
    }
}

fun ratioJurisReasonText(r: RatioJurisFailureReason) = when (r) {
    RatioJurisFailureReason.TOO_LARGE -> R.string.reason_too_large
    RatioJurisFailureReason.EMPTY -> R.string.reason_empty
    RatioJurisFailureReason.UNREADABLE -> R.string.reason_unreadable
    RatioJurisFailureReason.UNSUPPORTED_FORMAT -> R.string.reason_format
    RatioJurisFailureReason.STORAGE -> R.string.reason_storage
    RatioJurisFailureReason.UNKNOWN -> R.string.reason_unknown
}