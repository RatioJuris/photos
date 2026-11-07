// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisThumbnailPolicy
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos

const val RATIOJURIS_MIN_PASSWORD = 8

/** Options chosen for a protect operation. */
class RatioJurisProtectChoice(val password: CharArray, val policy: RatioJurisThumbnailPolicy, val removeOriginals: Boolean)

fun ratioJurisPolicyLabel(p: RatioJurisThumbnailPolicy) = when (p) {
    RatioJurisThumbnailPolicy.STANDARD -> R.string.policy_standard
    RatioJurisThumbnailPolicy.REDUCED -> R.string.policy_reduced
    RatioJurisThumbnailPolicy.NEUTRAL -> R.string.policy_neutral
    RatioJurisThumbnailPolicy.NONE -> R.string.policy_none
}

fun ratioJurisPolicyHint(p: RatioJurisThumbnailPolicy) = when (p) {
    RatioJurisThumbnailPolicy.STANDARD -> R.string.policy_standard_hint
    RatioJurisThumbnailPolicy.REDUCED -> R.string.policy_reduced_hint
    RatioJurisThumbnailPolicy.NEUTRAL -> R.string.policy_neutral_hint
    RatioJurisThumbnailPolicy.NONE -> R.string.policy_none_hint
}

/** Honest, simple estimate from length and character variety. 0 = too short .. 3 = strong. */
fun ratioJurisStrength(password: String): Int {
    val classes = listOf(password.any { it.isLowerCase() }, password.any { it.isUpperCase() }, password.any { it.isDigit() }, password.any { !it.isLetterOrDigit() }).count { it }
    return when {
        password.length < RATIOJURIS_MIN_PASSWORD -> 0
        password.length >= 16 || (password.length >= 12 && classes >= 3) -> 3
        password.length >= 10 && classes >= 2 -> 2
        else -> 1
    }
}

@Composable
private fun ratioJurisStrengthFooter(password: String, confirm: String?, attempted: Boolean): String {
    val base = stringResource(R.string.password_min, RATIOJURIS_MIN_PASSWORD)
    if (password.isEmpty()) return base
    val label = stringResource(listOf(R.string.strength_too_short, R.string.strength_fair, R.string.strength_good, R.string.strength_strong)[ratioJurisStrength(password)])
    val mismatch = confirm != null && attempted && confirm != password
    return if (mismatch) stringResource(R.string.password_mismatch) else "${stringResource(R.string.strength_label)}: $label"
}

/** Checkmark row used for single-choice lists (iOS style). */
@Composable
fun RatioJurisCheckRow(title: String, subtitle: String?, checked: Boolean, onClick: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ios.label)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
        }
        if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = ios.tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun RatioJurisProtectDialog(
    count: Int,
    defaultPolicy: RatioJurisThumbnailPolicy,
    defaultRemoveOriginals: Boolean,
    requireConfirmation: Boolean,
    onConfirm: (RatioJurisProtectChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val ios = LocalRatioJurisIos.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf(defaultPolicy) }
    var removeOriginals by remember { mutableStateOf(defaultRemoveOriginals) }
    var attempted by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { password = ""; confirm = "" } }
    val valid = password.length >= RATIOJURIS_MIN_PASSWORD && (!requireConfirmation || confirm == password)

    RatioJurisFormSheet(
        title = pluralStringResource(R.plurals.protect_title, count, count),
        cancelLabel = stringResource(R.string.cancel),
        confirmLabel = stringResource(R.string.action_protect),
        confirmEnabled = password.isNotEmpty(),
        onCancel = onDismiss,
        onConfirm = {
            attempted = true
            if (valid) {
                val chars = password.toCharArray()
                password = ""; confirm = ""
                onConfirm(RatioJurisProtectChoice(chars, policy, removeOriginals))
            }
        },
    ) {
        Text(
            stringResource(R.string.protect_body),
            style = MaterialTheme.typography.bodySmall,
            color = ios.secondaryLabel,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp),
        )
        val passwordRows = buildList<@Composable () -> Unit> {
            add {
                RatioJurisSecureField(
                    password, { password = it }, stringResource(R.string.password),
                    imeAction = if (requireConfirmation) ImeAction.Next else ImeAction.Done, focusRequester = focus,
                )
            }
            if (requireConfirmation) add { RatioJurisSecureField(confirm, { confirm = it }, stringResource(R.string.password_confirm)) }
        }
        RatioJurisGroupedSection(
            header = stringResource(R.string.password),
            footer = ratioJurisStrengthFooter(password, if (requireConfirmation) confirm else null, attempted),
            rows = passwordRows,
        )
        RatioJurisGroupedSection(
            header = stringResource(R.string.thumbnail_policy),
            rows = RatioJurisThumbnailPolicy.entries.map { p ->
                @Composable { RatioJurisCheckRow(stringResource(ratioJurisPolicyLabel(p)), stringResource(ratioJurisPolicyHint(p)), p == policy) { policy = p } }
            },
        )
        RatioJurisGroupedSection(
            footer = stringResource(R.string.remove_originals_hint),
            rows = listOf { RatioJurisToggleRow(stringResource(R.string.remove_originals), removeOriginals, { removeOriginals = it }) },
        )
        Text(
            stringResource(R.string.password_loss_warning),
            style = MaterialTheme.typography.bodySmall,
            color = ios.destructive,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp),
        )
    }
}

/** Password entry alert. A wrong password closes it silently (handled by the caller); nothing is revealed. */
@Composable
fun RatioJurisPasswordDialog(
    title: String,
    busy: Boolean,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val ios = LocalRatioJurisIos.current
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { password = "" } }
    fun submit() {
        if (password.isNotEmpty() && !busy) {
            val chars = password.toCharArray()
            password = ""
            onSubmit(chars)
        }
    }
    RatioJurisAlert(
        title = title,
        dismissible = !busy,
        onDismiss = onDismiss,
        buttons = listOf(
            RatioJurisAlertButton(stringResource(R.string.cancel), RatioJurisButtonStyle.DEFAULT, enabled = !busy, onClick = onDismiss),
            RatioJurisAlertButton(stringResource(R.string.unlock), RatioJurisButtonStyle.PREFERRED, enabled = !busy && password.isNotEmpty(), onClick = ::submit),
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (ios.isDark) androidx.compose.ui.graphics.Color(0xFF1C1C1E) else androidx.compose.ui.graphics.Color.White),
        ) {
            RatioJurisSecureField(password, { password = it }, stringResource(R.string.password), onIme = ::submit, focusRequester = focus)
        }
        if (busy) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ios.secondaryLabel)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.verifying), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
            }
        }

    }
}

@Composable
fun RatioJurisChangePasswordDialog(
    busy: Boolean,
    error: String?,
    requireConfirmation: Boolean,
    onSubmit: (old: CharArray, new: CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val ios = LocalRatioJurisIos.current
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { current = ""; next = ""; confirm = "" } }
    LaunchedEffect(error) { if (error != null) current = "" }
    val valid = next.length >= RATIOJURIS_MIN_PASSWORD && (!requireConfirmation || next == confirm)
    RatioJurisFormSheet(
        title = stringResource(R.string.change_password),
        cancelLabel = stringResource(R.string.cancel),
        confirmLabel = stringResource(R.string.change_password_action),
        confirmEnabled = !busy && current.isNotEmpty() && next.isNotEmpty(),
        onCancel = { if (!busy) onDismiss() },
        onConfirm = {
            attempted = true
            if (valid) {
                val o = current.toCharArray(); val n = next.toCharArray()
                current = ""; next = ""; confirm = ""
                onSubmit(o, n)
            }
        },
    ) {
        RatioJurisGroupedSection(
            header = stringResource(R.string.password_current),
            footer = error,
            rows = listOf { RatioJurisSecureField(current, { current = it }, stringResource(R.string.password_current), imeAction = ImeAction.Next) },
        )
        val rows = buildList<@Composable () -> Unit> {
            add { RatioJurisSecureField(next, { next = it }, stringResource(R.string.password_new), imeAction = if (requireConfirmation) ImeAction.Next else ImeAction.Done) }
            if (requireConfirmation) add { RatioJurisSecureField(confirm, { confirm = it }, stringResource(R.string.password_confirm)) }
        }
        RatioJurisGroupedSection(
            header = stringResource(R.string.password_new),
            footer = ratioJurisStrengthFooter(next, if (requireConfirmation) confirm else null, attempted),
            rows = rows,
        )
        if (busy) {
            Row(Modifier.padding(start = 32.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ios.secondaryLabel)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reencrypting), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
            }
        }
        Text(
            stringResource(R.string.change_password_hint),
            style = MaterialTheme.typography.bodySmall,
            color = ios.secondaryLabel,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp),
        )
    }
}

/** Two-button confirmation alert (Cancel + action). */
@Composable
fun RatioJurisConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    RatioJurisAlert(
        title = title,
        message = body,
        onDismiss = onDismiss,
        buttons = listOf(
            RatioJurisAlertButton(stringResource(R.string.cancel), RatioJurisButtonStyle.CANCEL, onClick = onDismiss),
            RatioJurisAlertButton(confirmLabel, if (destructive) RatioJurisButtonStyle.DESTRUCTIVE else RatioJurisButtonStyle.DEFAULT, onClick = onConfirm),
        ),
    )
}
