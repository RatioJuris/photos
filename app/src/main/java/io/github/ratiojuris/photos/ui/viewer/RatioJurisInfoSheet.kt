// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.viewer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import io.github.ratiojuris.photos.ratioJurisContainer
import io.github.ratiojuris.photos.ui.components.RatioJurisMap
import io.github.ratiojuris.photos.ui.components.ratioJurisOpenInMaps
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisThumbnailPolicy
import io.github.ratiojuris.photos.phtx.RatioJurisMetaKeys
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxHeader
import io.github.ratiojuris.photos.ui.components.ratioJurisPolicyLabel
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real metadata only: MediaStore columns, EXIF tags, PHTX public header and (after unlock) private metadata. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatioJurisInfoSheet(
    item: RatioJurisViewerItem,
    header: RatioJurisPhtxHeader?,
    unlocked: RatioJurisUnlocked?,
    onDismiss: () -> Unit,
    showLocation: Boolean = false,
    mapsEnabled: Boolean = false,
    onEnableMaps: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var exif by remember(item.key) { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    val container = context.ratioJurisContainer
    var location by remember(item.key) { mutableStateOf<io.github.ratiojuris.photos.data.RatioJurisLatLon?>(null) }
    var locationChecked by remember(item.key) { mutableStateOf(false) }
    val photo = (item as? RatioJurisViewerItem.Ordinary)?.photo
    if (showLocation && photo != null) {
        LaunchedEffect(item.key) {
            location = container.location.ratioJurisLocationOf(photo)
            locationChecked = true
        }
    }

    if (item is RatioJurisViewerItem.Ordinary && !item.isVideo) {
        LaunchedEffect(item.key) {
            exif = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { s ->
                        val e = ExifInterface(s)
                        buildList {
                            val camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
                                .joinToString(" ").trim()
                            if (camera.isNotEmpty()) add(R.string.info_camera to camera)
                            e.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { add(R.string.info_aperture to "f/%.1f".format(it)) }
                            e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                                add(R.string.info_exposure to if (it < 1) "1/${(1 / it).toInt()} s" else "%.1f s".format(it))
                            }
                            e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { add(R.string.info_iso to "ISO $it") }
                            e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { raw ->
                                val parts = raw.split('/')
                                val mm = if (parts.size == 2) parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0) else raw.toDoubleOrNull()
                                mm?.let { add(R.string.info_focal to "%.1f mm".format(it)) }
                            }
                        }
                    } ?: emptyList()
                }.getOrDefault(emptyList())
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = LocalRatioJurisIos.current.groupedBackground) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.info_title), style = MaterialTheme.typography.headlineSmall, color = LocalRatioJurisIos.current.label, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            when (item) {
                is RatioJurisViewerItem.Ordinary -> {
                    val p = item.photo
                    Row2(R.string.info_name, item.name)
                    if (p != null) {
                        Row2(R.string.info_date, ratioJurisDateTime(p.dateMillis))
                        if (p.width > 0 && p.height > 0) {
                            Row2(R.string.info_resolution, "${p.orientedWidth} × ${p.orientedHeight}  ·  %.1f MP".format(p.width * p.height / 1_000_000.0))
                        }
                        Row2(R.string.info_size, Formatter.formatFileSize(context, p.sizeBytes))
                        p.mimeType?.let { Row2(R.string.info_type, it) }
                        if (p.isVideo && p.durationMs > 0) Row2(R.string.info_duration, io.github.ratiojuris.photos.ui.components.ratioJurisDuration(p.durationMs))
                        Row2(R.string.info_location, (p.relativePath ?: p.bucketName).trimEnd('/'))
                    }
                    exif.forEach { (label, value) -> Row2(label, value) }
                    if (showLocation && photo != null) {
                        Section(R.string.location)
                        when {
                            !container.location.ratioJurisHasPermission() -> {
                                Text(stringResource(R.string.location_permission_needed), style = MaterialTheme.typography.bodySmall, color = LocalRatioJurisIos.current.secondaryLabel)
                                io.github.ratiojuris.photos.ui.components.RatioJurisTextButton(stringResource(R.string.allow), onRequestLocationPermission)
                            }
                            location != null -> RatioJurisLocationCard(location!!, mapsEnabled, onEnableMaps)
                            locationChecked -> Text(stringResource(R.string.no_location), style = MaterialTheme.typography.bodySmall, color = LocalRatioJurisIos.current.secondaryLabel)
                        }
                    }
                }
                is RatioJurisViewerItem.Protected -> {
                    Row2(R.string.info_file, item.name)
                    item.stored?.let {
                        Row2(R.string.info_size, Formatter.formatFileSize(context, it.sizeBytes))
                        Row2(R.string.info_date, ratioJurisDateTime(it.dateMillis))
                    }
                    if (unlocked != null) {
                        header?.publicMetadata?.get(RatioJurisMetaKeys.CREATED_AT)?.toLongOrNull()?.let {
                            Row2(R.string.info_protected_on, ratioJurisDateTime(it))
                        }
                        val policy = RatioJurisThumbnailPolicy.ratioJurisFromWire(header?.publicMetadata?.get(RatioJurisMetaKeys.THUMB_POLICY))
                        if (policy != null) Row2(R.string.info_preview, stringResource(ratioJurisPolicyLabel(policy)))
                        Section(R.string.info_original_section)
                        val m = unlocked.decrypted.privateMetadata
                        m[RatioJurisMetaKeys.ORIGINAL_NAME]?.let { Row2(R.string.info_name, it) }
                        m[RatioJurisMetaKeys.ORIGINAL_DATE_TAKEN]?.toLongOrNull()?.let { Row2(R.string.info_date, ratioJurisDateTime(it)) }
                        val w = m[RatioJurisMetaKeys.ORIGINAL_WIDTH]; val h = m[RatioJurisMetaKeys.ORIGINAL_HEIGHT]
                        if (w != null && h != null) Row2(R.string.info_resolution, "$w × $h")
                        Row2(R.string.info_size, Formatter.formatFileSize(context, unlocked.decrypted.image.size.toLong()))
                        m[RatioJurisMetaKeys.ORIGINAL_MIME]?.let { Row2(R.string.info_type, it) }
                    }
                    if (header != null && unlocked != null) {
                        Section(R.string.info_security_section)
                        Row2(R.string.info_encryption, "AES-256-GCM")
                        Row2(R.string.info_key_derivation, "Argon2id · ${header.kdfParams.memoryKiB / 1024} MiB · ${header.kdfParams.iterations}×")
                        Row2(R.string.info_format, "PHTX v${header.version}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(stringResource(title).uppercase(), style = MaterialTheme.typography.bodySmall, color = LocalRatioJurisIos.current.secondaryLabel, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Row2(label: Int, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = LocalRatioJurisIos.current.secondaryLabel, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = LocalRatioJurisIos.current.label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RatioJurisLocationCard(p: io.github.ratiojuris.photos.data.RatioJurisLatLon, mapsEnabled: Boolean, onEnableMaps: () -> Unit) {
    val context = LocalContext.current
    val ios = LocalRatioJurisIos.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    if (mapsEnabled) {
        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(shape).clickable { ratioJurisOpenInMaps(context, p) },
        ) {
            RatioJurisMap(center = p, initialZoom = 14, points = emptyList(), interactive = false, showPin = true, modifier = Modifier.fillMaxSize())
        }
    } else {
        // No network request until the user opts in: the tile server would learn this location.
        Column(
            Modifier.fillMaxWidth().clip(shape).background(ios.fill).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Map, null, tint = ios.secondaryLabel, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.maps_off_hint), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel, textAlign = TextAlign.Center)
            io.github.ratiojuris.photos.ui.components.RatioJurisTextButton(stringResource(R.string.show_map), onEnableMaps)
        }
    }
    Row2(R.string.coordinates, p.format())
    io.github.ratiojuris.photos.ui.components.RatioJurisTextButton(stringResource(R.string.open_in_maps), { ratioJurisOpenInMaps(context, p) })
}