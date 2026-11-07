// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisGalleryItem
import io.github.ratiojuris.photos.data.RatioJurisMediaThumb
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedThumb
import io.github.ratiojuris.photos.ui.gallery.RatioJurisGridEntry
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisExtras
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * iOS-style photo grid: square tiles with hairline gaps, pinch to change density,
 * tap to open (or toggle while selecting), long press for quick actions.
 */
@Composable
fun RatioJurisPhotoGrid(
    entries: List<RatioJurisGridEntry>,
    selection: Set<String>,
    selecting: Boolean,
    baseColumns: Int,
    showProtectedThumbnails: Boolean,
    onOpen: (RatioJurisGalleryItem) -> Unit,
    onToggle: (String) -> Unit,
    onToggleGroup: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((RatioJurisGalleryItem) -> Unit)? = null,
    onPinch: ((zoomIn: Boolean) -> Unit)? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (LazyGridScope.() -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
) {
    val pinch by rememberUpdatedState(onPinch)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Columns are tuned for a ~400dp phone and scale up on tablets / landscape.
        val columns = max(1, (baseColumns * (maxWidth.value / 400f)).roundToInt().coerceAtLeast(baseColumns))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var zoom = 1f
                        var fired = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom *= event.calculateZoom()
                                if (!fired && (zoom > 1.25f || zoom < 0.8f)) {
                                    pinch?.invoke(zoom > 1f)
                                    fired = true
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            header?.invoke(this)
            items(
                count = entries.size,
                key = { entries[it].key },
                span = { if (entries[it] is RatioJurisGridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { if (entries[it] is RatioJurisGridEntry.Header) 0 else 1 },
            ) { index ->
                when (val entry = entries[index]) {
                    is RatioJurisGridEntry.Header -> RatioJurisDateHeader(
                        label = entry.label,
                        selecting = selecting,
                        allSelected = entry.itemKeys.isNotEmpty() && entry.itemKeys.all { it in selection },
                        onToggleGroup = { onToggleGroup(entry.itemKeys) },
                    )
                    is RatioJurisGridEntry.Tile -> RatioJurisTile(
                        item = entry.item,
                        selected = entry.item.key in selection,
                        showProtectedThumbnail = showProtectedThumbnails,
                        onClick = { if (selecting) onToggle(entry.item.key) else onOpen(entry.item) },
                        onLongClick = { if (onLongPress != null && !selecting) onLongPress(entry.item) else onToggle(entry.item.key) },
                    )
                }
            }
            footer?.invoke(this)
        }
    }
}

@Composable
private fun RatioJurisDateHeader(label: String, selecting: Boolean, allSelected: Boolean, onToggleGroup: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = ios.label, modifier = Modifier.weight(1f).semantics { heading() })
        if (selecting) {
            RatioJurisTextButton(
                stringResource(if (allSelected) R.string.deselect else R.string.select),
                onToggleGroup,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RatioJurisTile(
    item: RatioJurisGalleryItem,
    selected: Boolean,
    showProtectedThumbnail: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val extras = LocalRatioJurisExtras.current
    val ios = LocalRatioJurisIos.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var failed by remember(item.key) { mutableStateOf(false) }
    val isProtected = item is RatioJurisProtectedPhoto
    val dateText = remember(item.dateMillis) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateMillis)) }
    val video = item is RatioJurisPhoto && item.isVideo
    val description = if (video) stringResource(R.string.cd_video_date, ratioJurisDuration((item as RatioJurisPhoto).durationMs), dateText) else stringResource(R.string.cd_photo_date, dateText)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(extras.tilePlaceholder)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .combinedClickable(
                role = Role.Image,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        when {
            isProtected && (!showProtectedThumbnail || failed) -> RatioJurisLockedArt(Modifier.fillMaxSize())
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BrokenImage, null, tint = ios.secondaryLabel)
            }
            else -> {
                val data: Any = when (item) {
                    is RatioJurisPhoto -> RatioJurisMediaThumb(item.uri, item.dateModifiedMillis)
                    is RatioJurisProtectedPhoto -> if (item.imageBacked) RatioJurisMediaThumb(item.uri, item.dateModifiedMillis) else RatioJurisProtectedThumb(item.uri, item.dateModifiedMillis)
                }
                AsyncImage(
                    model = remember(data) { ImageRequest.Builder(context).data(data).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (video && !selected) {
            Text(
                ratioJurisDuration((item as RatioJurisPhoto).durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 6f),
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp),
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(Color(0x33FFFFFF)))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ios.tint)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Neutral artwork when a preview is hidden or absent. Deliberately shows no lock. */
@Composable
fun RatioJurisLockedArt(modifier: Modifier = Modifier) {
    val ios = LocalRatioJurisIos.current
    Box(modifier.background(if (ios.isDark) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = if (ios.isDark) Color(0xFF8E8E93) else Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun RatioJurisCenteredMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    val ios = LocalRatioJurisIos.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ios.tertiaryLabel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = ios.label, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = ios.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        if (primaryAction != null) {
            Spacer(Modifier.size(12.dp))
            RatioJurisTextButton(primaryAction.first, primaryAction.second, bold = true)
        }
        if (secondaryAction != null) RatioJurisTextButton(secondaryAction.first, secondaryAction.second)
    }
}

@Composable
fun RatioJurisLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalRatioJurisIos.current.secondaryLabel, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
    }
}

/** Slim information banner with an action, e.g. limited photo access. */
@Composable
fun RatioJurisBanner(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val ios = LocalRatioJurisIos.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(MaterialTheme.shapes.medium).background(ios.cell).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = ios.label, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        RatioJurisTextButton(action, onAction)
    }
}

/** iOS-style duration label: 0:07, 1:05, 1:02:03. */
fun ratioJurisDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}