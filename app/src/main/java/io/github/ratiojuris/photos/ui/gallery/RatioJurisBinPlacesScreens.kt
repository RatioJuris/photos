// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.gallery

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisBinItem
import io.github.ratiojuris.photos.data.RatioJurisMediaThumb
import io.github.ratiojuris.photos.ui.components.RatioJurisActionSheet
import io.github.ratiojuris.photos.ui.components.RatioJurisCenteredMessage
import io.github.ratiojuris.photos.ui.components.RatioJurisGeoPoint
import io.github.ratiojuris.photos.ui.components.RatioJurisHairline
import io.github.ratiojuris.photos.ui.components.RatioJurisLockedArt
import io.github.ratiojuris.photos.ui.components.RatioJurisMap
import io.github.ratiojuris.photos.ui.components.RatioJurisNavBar
import io.github.ratiojuris.photos.ui.components.RatioJurisSheetAction
import io.github.ratiojuris.photos.ui.components.RatioJurisTextButton
import io.github.ratiojuris.photos.ui.components.RatioJurisPhotoDeleter
import io.github.ratiojuris.photos.ui.components.ratioJurisFitZoom
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisExtras
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import kotlin.math.ceil

// =====================================================================================  Bin

/** iOS "Recently Deleted": the system Bin on Android 11+, with Restore / Delete / Empty. */
@Composable
fun RatioJurisBinScreen(vm: RatioJurisGalleryViewModel, deleter: RatioJurisPhotoDeleter, onBack: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    val items by vm.bin.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<List<RatioJurisBinItem>?>(null) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    val chosen = items.filter { it.key in selected }

    Column(Modifier.fillMaxSize().background(ios.background)) {
        RatioJurisNavBar(stringResource(R.string.bin), backLabel = stringResource(R.string.tab_albums), onBack = onBack) {
            if (items.isNotEmpty()) {
                RatioJurisTextButton(
                    stringResource(if (selected.size == items.size) R.string.deselect else R.string.select_all_short),
                    { selected = if (selected.size == items.size) emptySet() else items.map { it.key }.toSet() },
                )
            }
        }
        if (Build.VERSION.SDK_INT < 30) {
            RatioJurisCenteredMessage(Icons.Outlined.Delete, stringResource(R.string.bin), stringResource(R.string.bin_unsupported))
            return@Column
        }
        if (items.isEmpty()) {
            RatioJurisCenteredMessage(Icons.Outlined.Delete, stringResource(R.string.bin_empty_title), stringResource(R.string.bin_footer))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.bin_footer), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
            items(items, key = { it.key }) { b ->
                val isSel = b.key in selected
                val days = if (b.expiresMillis > 0) ceil((b.expiresMillis - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0) else null
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .background(LocalRatioJurisExtras.current.tilePlaceholder)
                        .clickable(role = Role.Checkbox) { selected = if (isSel) selected - b.key else selected + b.key }
                        .semantics { this.selected = isSel; contentDescription = b.displayName },
                ) {
                    var failed by remember(b.key) { mutableStateOf(false) }
                    if (failed) RatioJurisLockedArt(Modifier.fillMaxSize())
                    else AsyncImage(RatioJurisMediaThumb(b.uri, b.dateModifiedMillis), null, contentScale = ContentScale.Crop, onError = { failed = true }, modifier = Modifier.fillMaxSize())
                    if (days != null) {
                        Text(
                            pluralStringResource(R.plurals.days_left, days, days),
                            color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).background(Color(0x66000000)).padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    if (isSel) {
                        Box(Modifier.fillMaxSize().background(Color(0x33FFFFFF)))
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(ios.tint).border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        // Bottom bar: Delete (left), Recover (right) - acting on the selection, or on everything.
        Column(Modifier.fillMaxWidth().background(ios.bar)) {
            RatioJurisHairline()
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RatioJurisTextButton(
                    stringResource(if (selected.isEmpty()) R.string.delete_all else R.string.delete),
                    { confirm = if (selected.isEmpty()) items else chosen },
                    color = ios.destructive,
                )
                Box(Modifier.weight(1f))
                RatioJurisTextButton(
                    stringResource(if (selected.isEmpty()) R.string.recover_all else R.string.recover),
                    { deleter.ratioJurisRestore((if (selected.isEmpty()) items else chosen).map { it.uri }); selected = emptySet() },
                )
            }
        }
    }
    confirm?.let { list ->
        RatioJurisActionSheet(
            message = stringResource(R.string.bin_delete_message),
            actions = listOf(
                RatioJurisSheetAction(pluralStringResource(R.plurals.delete_action, list.size, list.size), destructive = true) {
                    confirm = null
                    deleter.ratioJurisDelete(list.map { it.uri }, toBin = false)
                    selected = emptySet()
                },
            ),
            cancelLabel = stringResource(R.string.cancel),
            onDismiss = { confirm = null },
        )
    }
}

// =====================================================================================  Places

/** iOS "Places": every geotagged photo on a map; tap a cluster to see its photos. */
@Composable
fun RatioJurisPlacesScreen(vm: RatioJurisGalleryViewModel, mapsEnabled: Boolean, onEnableMaps: () -> Unit, onBack: () -> Unit, onOpenCluster: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    val library by vm.library.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val points = remember(library, places) {
        library.photos.filter { it.key !in library.archived }.mapNotNull { p -> places[p.id]?.let { RatioJurisGeoPoint(p, it) } }
    }
    Column(Modifier.fillMaxSize().background(ios.background)) {
        RatioJurisNavBar(stringResource(R.string.places), backLabel = stringResource(R.string.tab_albums), onBack = onBack)
        when {
            points.isEmpty() -> RatioJurisCenteredMessage(Icons.Outlined.Place, stringResource(R.string.no_places_title), stringResource(R.string.no_places_body))
            !mapsEnabled -> RatioJurisCenteredMessage(
                Icons.Outlined.Map,
                pluralStringResource(R.plurals.places_count, points.size, points.size),
                stringResource(R.string.maps_consent_body),
                primaryAction = stringResource(R.string.maps_enable) to onEnableMaps,
            )
            else -> {
                val (center, zoom) = remember(points) { ratioJurisFitZoom(points.map { it.position }) }
                RatioJurisMap(
                    center = center,
                    initialZoom = zoom,
                    points = points,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    onCluster = { group ->
                        vm.placeSelection.value = group.map { it.item.key }
                        onOpenCluster()
                    },
                )
            }
        }
    }
}

