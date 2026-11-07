// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisAlbum
import io.github.ratiojuris.photos.data.RatioJurisGalleryItem
import io.github.ratiojuris.photos.data.RatioJurisGridDensity
import io.github.ratiojuris.photos.data.RatioJurisMediaAccess
import io.github.ratiojuris.photos.data.RatioJurisMediaThumb
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedThumb
import io.github.ratiojuris.photos.data.RatioJurisProtectedPhoto
import io.github.ratiojuris.photos.data.RatioJurisSortOrder
import io.github.ratiojuris.photos.ui.components.RatioJurisBanner
import io.github.ratiojuris.photos.ui.components.RatioJurisCenteredMessage
import io.github.ratiojuris.photos.ui.components.RatioJurisHairline
import io.github.ratiojuris.photos.ui.components.RatioJurisLargeTitle
import io.github.ratiojuris.photos.ui.components.RatioJurisLoading
import io.github.ratiojuris.photos.ui.components.RatioJurisLockedArt
import io.github.ratiojuris.photos.ui.components.RatioJurisNavBar
import io.github.ratiojuris.photos.ui.components.RatioJurisPhotoGrid
import io.github.ratiojuris.photos.ui.components.RatioJurisPill
import io.github.ratiojuris.photos.ui.components.RatioJurisSearchField
import io.github.ratiojuris.photos.ui.components.RatioJurisSegmented
import io.github.ratiojuris.photos.ui.components.RatioJurisTextButton
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisExtras
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Callbacks shared by every grid screen. */
class RatioJurisGridActions(
    val onOpen: (RatioJurisGalleryItem, String) -> Unit,
    val onLongPress: (RatioJurisGalleryItem) -> Unit,
    val onRequestPermission: () -> Unit,
    val permissionPermanentlyDenied: Boolean,
    val onOpenAppSettings: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onImport: () -> Unit,
)

private fun RatioJurisGridDensity.ratioJurisPinch(zoomIn: Boolean): RatioJurisGridDensity = when (this) {
    RatioJurisGridDensity.COMPACT -> if (zoomIn) RatioJurisGridDensity.COMFORTABLE else this
    RatioJurisGridDensity.COMFORTABLE -> if (zoomIn) RatioJurisGridDensity.LARGE else RatioJurisGridDensity.COMPACT
    RatioJurisGridDensity.LARGE -> if (zoomIn) this else RatioJurisGridDensity.COMFORTABLE
}

// =====================================================================================  Library

private enum class RatioJurisLibraryView(val label: Int) { YEARS(R.string.years), MONTHS(R.string.months), ALL(R.string.all_photos) }

@Composable
fun RatioJurisLibraryTab(vm: RatioJurisGalleryViewModel, actions: RatioJurisGridActions, bottomInset: Dp) {
    val ios = LocalRatioJurisIos.current
    val library by vm.library.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val months by vm.months.collectAsStateWithLifecycle()
    val years by vm.years.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(RatioJurisLibraryView.ALL) }
    var menu by remember { mutableStateOf(false) }
    var pendingAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusTop + 92.dp

    BackHandler(enabled = selecting) { vm.ratioJurisClearSelection() }
    BackHandler(enabled = !selecting && view != RatioJurisLibraryView.ALL) { view = RatioJurisLibraryView.ALL }

    // iOS behaviour: oldest at the top, open at the newest photo (bottom).
    LaunchedEffect(timeline.isNotEmpty(), settings.sortOrder) {
        if (timeline.isNotEmpty() && !initialScrollDone) {
            if (settings.sortOrder == RatioJurisSortOrder.DATE_OLDEST) gridState.scrollToItem(timeline.size) // +1 for header row
            initialScrollDone = true
        }
    }
    LaunchedEffect(pendingAnchor, view, timeline) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        if (view == RatioJurisLibraryView.ALL) {
            val idx = timeline.indexOfFirst { it.key == anchor }
            if (idx >= 0) gridState.scrollToItem(idx + 1)
            pendingAnchor = null
        }
    }
    val monthFormat = remember { SimpleDateFormat("LLLL yyyy", Locale.getDefault()) }
    val topTitle by remember(timeline) {
        derivedStateOf {
            val idx = (gridState.firstVisibleItemIndex - 1).coerceIn(0, (timeline.size - 1).coerceAtLeast(0))
            (timeline.getOrNull(idx) as? RatioJurisGridEntry.Tile)?.item?.dateMillis?.let { monthFormat.format(Date(it)).replaceFirstChar { c -> c.titlecase() } }
        }
    }
    val itemCount = timeline.count { it is RatioJurisGridEntry.Tile }

    Box(Modifier.fillMaxSize().background(ios.background)) {
        when {
            library.loading -> RatioJurisLoading()
            library.access == RatioJurisMediaAccess.DENIED && filter != RatioJurisFilter.PROTECTED && library.protected.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(top = headerHeight), contentAlignment = Alignment.Center) { RatioJurisPermissionMessage(actions) }
            view == RatioJurisLibraryView.ALL -> RatioJurisPhotoGrid(
                entries = timeline,
                selection = selection,
                selecting = selecting,
                baseColumns = settings.gridDensity.columns,
                showProtectedThumbnails = settings.showProtectedThumbnails,
                onOpen = { actions.onOpen(it, RatioJurisGalleryViewModel.SOURCE_TIMELINE) },
                onToggle = vm::ratioJurisToggle,
                onToggleGroup = vm::ratioJurisToggleGroup,
                onLongPress = actions.onLongPress,
                onPinch = { zoomIn -> vm.ratioJurisSetDensity(settings.gridDensity.ratioJurisPinch(zoomIn)) },
                state = gridState,
                contentPadding = PaddingValues(top = headerHeight, bottom = bottomInset + 64.dp),
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "banner", contentType = "banner") {
                        Column {
                            if (library.access == RatioJurisMediaAccess.PARTIAL) {
                                RatioJurisBanner(stringResource(R.string.partial_access_banner), stringResource(R.string.manage), actions.onRequestPermission)
                            }
                            if (library.access == RatioJurisMediaAccess.DENIED && filter != RatioJurisFilter.PROTECTED) {
                                RatioJurisBanner(stringResource(R.string.permission_body), stringResource(R.string.allow), actions.onRequestPermission)
                            }
                        }
                    }
                },
                footer = {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "footer", contentType = "footer") {
                        if (itemCount == 0) {
                            RatioJurisCenteredMessage(
                                if (filter == RatioJurisFilter.PROTECTED) Icons.Rounded.Lock else Icons.Outlined.PhotoLibrary,
                                stringResource(if (filter == RatioJurisFilter.PROTECTED) R.string.no_protected_title else R.string.no_photos_title),
                                stringResource(if (filter == RatioJurisFilter.PROTECTED) R.string.no_protected_body else R.string.no_photos_body),
                            )
                        } else {
                            Text(
                                ratioJurisCountText(library, filter, settings.showArchived),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = ios.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            )
                        }
                    }
                },
            )
            else -> RatioJurisPeriodList(
                periods = if (view == RatioJurisLibraryView.YEARS) years else months,
                big = view == RatioJurisLibraryView.YEARS,
                showProtectedThumbnails = settings.showProtectedThumbnails,
                contentPadding = PaddingValues(top = headerHeight, bottom = bottomInset + 72.dp),
                onOpen = { pendingAnchor = it.anchorKey; view = RatioJurisLibraryView.ALL },
            )
        }

        // Floating title over the photos (iOS Library header)
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight + 24.dp)
                .background(Brush.verticalGradient(listOf(ios.background, ios.background.copy(alpha = 0.85f), ios.background.copy(alpha = 0f)))),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (selecting) stringResource(R.string.select_items) else (if (view == RatioJurisLibraryView.ALL) topTitle else null) ?: stringResource(R.string.library),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ios.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (!selecting && view == RatioJurisLibraryView.ALL && itemCount > 0) {
                    Text(pluralStringResource(R.plurals.item_count, itemCount, itemCount), style = MaterialTheme.typography.bodyMedium, color = ios.secondaryLabel)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (view == RatioJurisLibraryView.ALL && itemCount > 0) {
                    RatioJurisPill(
                        text = stringResource(if (selecting) R.string.cancel else R.string.select),
                        onClick = { if (selecting) vm.ratioJurisClearSelection() else vm.ratioJurisStartSelecting() },
                        overPhoto = true,
                    )
                }
                if (!selecting) {
                    Box {
                        RatioJurisPill(icon = Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.more), onClick = { menu = true }, overPhoto = true)
                        RatioJurisLibraryMenu(menu, filter, settings.sortOrder,
                            onFilter = { vm.filter.value = it; menu = false },
                            onSort = { vm.ratioJurisSetSort(it); menu = false },
                            onSettings = { menu = false; actions.onOpenSettings() },
                            onDismiss = { menu = false })
                    }
                }
            }
        }

        // Years / Months / All Photos
        if (!selecting && !library.loading) {
            RatioJurisSegmented(
                options = RatioJurisLibraryView.entries.map { stringResource(it.label) },
                selected = view.ordinal,
                onSelect = { view = RatioJurisLibraryView.entries[it] },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp),
            )
        }
    }
}

@Composable
private fun ratioJurisCountText(library: RatioJurisLibrary, filter: RatioJurisFilter, showArchived: Boolean): String {
    val photos = library.photos.filter { library.ratioJurisVisible(it, showArchived) }
    val locked = library.protected.filter { library.ratioJurisVisible(it, showArchived) }
    val n = when (filter) {
        RatioJurisFilter.ALL -> photos.size + locked.size
        RatioJurisFilter.PHOTOS -> photos.count { !it.isVideo }
        RatioJurisFilter.VIDEOS -> photos.count { it.isVideo }
        RatioJurisFilter.PROTECTED -> locked.size
    }
    return pluralStringResource(R.plurals.item_count, n, n)
}

@Composable
private fun RatioJurisLibraryMenu(
    expanded: Boolean,
    filter: RatioJurisFilter,
    sort: RatioJurisSortOrder,
    onFilter: (RatioJurisFilter) -> Unit,
    onSort: (RatioJurisSortOrder) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ios = LocalRatioJurisIos.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = if (ios.isDark) Color(0xFF2C2C2E) else Color(0xFFF7F7F7),
    ) {
        MenuHeader(stringResource(R.string.filter_title))
        RatioJurisFilter.entries.forEach { f ->
            MenuCheckItem(stringResource(ratioJurisFilterLabel(f)), f == filter) { onFilter(f) }
        }
        HorizontalDivider(color = ios.separator)
        MenuHeader(stringResource(R.string.sort))
        RatioJurisSortOrder.entries.forEach { o ->
            MenuCheckItem(stringResource(ratioJurisSortLabel(o)), o == sort) { onSort(o) }
        }
        HorizontalDivider(color = ios.separator)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tab_settings), color = ios.label) },
            trailingIcon = { Icon(Icons.Rounded.Settings, null, tint = ios.label) },
            onClick = onSettings,
        )
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalRatioJurisIos.current.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun MenuCheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    DropdownMenuItem(
        text = { Text(text, color = ios.label) },
        leadingIcon = { Box(Modifier.size(20.dp)) { if (checked) Icon(Icons.Rounded.Check, null, tint = ios.label) } },
        onClick = onClick,
    )
}

fun ratioJurisFilterLabel(f: RatioJurisFilter) = when (f) {
    RatioJurisFilter.ALL -> R.string.filter_all
    RatioJurisFilter.PHOTOS -> R.string.filter_photos
    RatioJurisFilter.VIDEOS -> R.string.filter_videos
    RatioJurisFilter.PROTECTED -> R.string.tab_protected
}

fun ratioJurisSortLabel(o: RatioJurisSortOrder) = when (o) {
    RatioJurisSortOrder.DATE_NEWEST -> R.string.sort_newest
    RatioJurisSortOrder.DATE_OLDEST -> R.string.sort_oldest
    RatioJurisSortOrder.NAME -> R.string.sort_name
    RatioJurisSortOrder.SIZE -> R.string.sort_size
}

@Composable
private fun RatioJurisPeriodList(
    periods: List<RatioJurisPeriod>,
    big: Boolean,
    showProtectedThumbnails: Boolean,
    contentPadding: PaddingValues,
    onOpen: (RatioJurisPeriod) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(periods.size) { if (periods.isNotEmpty()) listState.scrollToItem(periods.lastIndex) }
    LazyColumn(state = listState, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        items(periods, key = { it.key }) { p ->
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(if (big) 1.1f else 1.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalRatioJurisExtras.current.tilePlaceholder)
                    .clickable(role = Role.Button) { onOpen(p) }
                    .semantics(mergeDescendants = true) { contentDescription = "${p.title}, ${p.subtitle}" },
            ) {
                RatioJurisCover(p.cover, showProtectedThumbnails, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.45f to Color.Transparent)))
                Column(Modifier.padding(16.dp)) {
                    Text(p.title, color = Color.White, style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
                    Text(p.subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Cover image for cards: MediaStore thumbnail for photos, the public preview (or lock art) for protected items. */
@Composable
fun RatioJurisCover(item: RatioJurisGalleryItem, showProtectedThumbnails: Boolean, modifier: Modifier) {
    when (item) {
        is RatioJurisPhoto -> AsyncImage(model = RatioJurisMediaThumb(item.uri, item.dateModifiedMillis), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        is RatioJurisProtectedPhoto -> if (showProtectedThumbnails) {
            AsyncImage(model = if (item.imageBacked) RatioJurisMediaThumb(item.uri, item.dateModifiedMillis) else RatioJurisProtectedThumb(item.uri, item.dateModifiedMillis), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        } else {
            RatioJurisLockedArt(modifier)
        }
    }
}

@Composable
private fun RatioJurisPermissionMessage(actions: RatioJurisGridActions) {
    RatioJurisCenteredMessage(
        icon = Icons.Outlined.PhotoLibrary,
        title = stringResource(R.string.permission_title),
        body = stringResource(if (actions.permissionPermanentlyDenied) R.string.permission_denied_body else R.string.permission_body),
        primaryAction = if (actions.permissionPermanentlyDenied) stringResource(R.string.open_settings) to actions.onOpenAppSettings
        else stringResource(R.string.allow_access) to actions.onRequestPermission,
    )
}

// =====================================================================================  Albums

@Composable
fun RatioJurisAlbumsTab(
    vm: RatioJurisGalleryViewModel,
    actions: RatioJurisGridActions,
    bottomInset: Dp,
    onOpenCollection: (String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenPlaces: () -> Unit = {},
    onOpenBin: () -> Unit = {},
) {
    val ios = LocalRatioJurisIos.current
    val library by vm.library.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val bin by vm.bin.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().background(ios.background),
        contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = bottomInset + 24.dp),
    ) {
        item {
            RatioJurisLargeTitle(stringResource(R.string.tab_albums)) {
                RatioJurisPill(icon = Icons.Rounded.Settings, contentDescription = stringResource(R.string.tab_settings), onClick = actions.onOpenSettings)
            }
        }
        when {
            library.loading -> item { RatioJurisLoading(Modifier.height(240.dp)) }
            library.access == RatioJurisMediaAccess.DENIED -> item { RatioJurisPermissionMessage(actions) }
            else -> {
                item { RatioJurisSectionHeader(stringResource(R.string.my_albums), if (library.albums.size > 4) stringResource(R.string.see_all) to onSeeAll else null) }
                if (library.albums.isEmpty()) {
                    item { Text(stringResource(R.string.no_albums_body), color = ios.secondaryLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
                } else {
                    item {
                        val rows = if (library.albums.size > 2) 2 else 1
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(rows),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth().height((rows * 214).dp),
                        ) {
                            items(library.albums, key = { it.bucketId }) { album ->
                                RatioJurisAlbumCard(album, Modifier.width(160.dp)) { onOpenCollection(album.bucketId) }
                            }
                        }
                    }
                }
            }
        }
        if (collections.isNotEmpty()) {
            item { RatioJurisSectionHeader(stringResource(R.string.media_types), null) }
            collections.forEachIndexed { i, c ->
                item(key = c.id) {
                    RatioJurisListRow(
                        icon = when (c.id) {
                            RatioJurisGalleryViewModel.COLLECTION_VIDEOS -> Icons.Outlined.PlayCircle
                            RatioJurisGalleryViewModel.COLLECTION_SCREENSHOTS -> Icons.Outlined.Screenshot
                            RatioJurisGalleryViewModel.COLLECTION_ANIMATED -> Icons.Outlined.Gif
                            else -> Icons.Outlined.Photo
                        },
                        title = stringResource(c.titleRes),
                        value = c.items.size.toString(),
                        last = i == collections.lastIndex,
                    ) { onOpenCollection(c.id) }
                }
            }
        }
        item { RatioJurisSectionHeader(stringResource(R.string.utilities), null) }
        item {
            RatioJurisListRow(Icons.Outlined.Place, stringResource(R.string.places), places.size.toString(), last = false, onClick = onOpenPlaces)
        }
        item {
            RatioJurisListRow(Icons.Outlined.Lock, stringResource(R.string.tab_protected), library.protected.count { it.key !in library.archived }.toString(), last = false) {
                onOpenCollection(RatioJurisGalleryViewModel.COLLECTION_PROTECTED)
            }
        }
        item {
            RatioJurisListRow(Icons.Outlined.Archive, stringResource(R.string.archive), library.archived.size.toString(), last = false) {
                onOpenCollection(RatioJurisGalleryViewModel.COLLECTION_ARCHIVE)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            item { RatioJurisListRow(Icons.Outlined.Delete, stringResource(R.string.bin), bin.size.toString(), last = false, onClick = onOpenBin) }
        }
        item { RatioJurisListRow(Icons.Outlined.FileOpen, stringResource(R.string.import_protected), null, last = true, onClick = actions.onImport) }
    }
    io.github.ratiojuris.photos.ui.components.RatioJurisStatusBarBackdrop()
    }
}

@Composable
private fun RatioJurisSectionHeader(title: String, action: Pair<String, () -> Unit>?) {
    val ios = LocalRatioJurisIos.current
    Column {
        RatioJurisHairline(startIndent = 16.dp)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = ios.label, modifier = Modifier.weight(1f).semantics { heading() })
            if (action != null) RatioJurisTextButton(action.first, action.second)
        }
    }
}

@Composable
private fun RatioJurisListRow(icon: ImageVector, title: String, value: String?, last: Boolean, onClick: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = ios.tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ios.tint, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = MaterialTheme.typography.bodyLarge, color = ios.secondaryLabel)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = ios.tertiaryLabel, modifier = Modifier.size(14.dp))
        }
        if (!last) RatioJurisHairline(startIndent = 54.dp)
    }
}

@Composable
fun RatioJurisAlbumCard(album: RatioJurisAlbum, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    val countText = album.count.toString()
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${album.name}, $countText" },
    ) {
        AsyncImage(
            model = RatioJurisMediaThumb(album.cover.uri, album.cover.dateModifiedMillis),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(LocalRatioJurisExtras.current.tilePlaceholder),
        )
        Spacer(Modifier.height(6.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(countText, style = MaterialTheme.typography.bodyMedium, color = ios.secondaryLabel)
    }
}

@Composable
fun RatioJurisAllAlbumsScreen(vm: RatioJurisGalleryViewModel, onBack: () -> Unit, onOpenCollection: (String) -> Unit) {
    val ios = LocalRatioJurisIos.current
    val library by vm.library.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(ios.background)) {
        RatioJurisNavBar(stringResource(R.string.my_albums), backLabel = stringResource(R.string.tab_albums), onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(library.albums, key = { it.bucketId }) { album -> RatioJurisAlbumCard(album) { onOpenCollection(album.bucketId) } }
        }
    }
}

/** An album, media-type collection or the Protected collection. */
@Composable
fun RatioJurisCollectionScreen(vm: RatioJurisGalleryViewModel, id: String, actions: RatioJurisGridActions, bottomInset: Dp, onBack: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    val library by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val entries = remember(library, settings, collections, id) { vm.ratioJurisCollectionGrid(id) }
    val title = remember(library, collections, id) { vm.ratioJurisCollectionTitle(id) }
    val isProtected = id == RatioJurisGalleryViewModel.COLLECTION_PROTECTED
    BackHandler(enabled = selecting) { vm.ratioJurisClearSelection() }
    Column(Modifier.fillMaxSize().background(ios.background)) {
        RatioJurisNavBar(
            title = if (selecting) pluralStringResource(R.plurals.selected_count, selection.size, selection.size) else title,
            backLabel = stringResource(R.string.tab_albums),
            onBack = { vm.ratioJurisClearSelection(); onBack() },
        ) {
            if (isProtected && !selecting) RatioJurisTextButton(stringResource(R.string.import_short), actions.onImport)
            if (entries.isNotEmpty()) {
                RatioJurisTextButton(
                    stringResource(if (selecting) R.string.cancel else R.string.select),
                    { if (selecting) vm.ratioJurisClearSelection() else vm.ratioJurisStartSelecting() },
                    bold = selecting,
                )
            }
        }
        when {
            library.loading -> RatioJurisLoading()
            id == RatioJurisGalleryViewModel.COLLECTION_ARCHIVE && entries.isEmpty() -> RatioJurisCenteredMessage(
                Icons.Outlined.Archive, stringResource(R.string.archive_empty_title), stringResource(R.string.archive_empty_body),
            )
            !isProtected && entries.isEmpty() -> RatioJurisCenteredMessage(
                Icons.Outlined.PhotoLibrary, stringResource(R.string.album_gone_title), stringResource(R.string.album_gone_body),
                primaryAction = stringResource(R.string.back) to onBack,
            )
            else -> RatioJurisPhotoGrid(
                entries = entries,
                selection = selection,
                selecting = selecting,
                baseColumns = if (isProtected) minOf(settings.gridDensity.columns, 4) else settings.gridDensity.columns,
                showProtectedThumbnails = settings.showProtectedThumbnails,
                onOpen = { actions.onOpen(it, RatioJurisGalleryViewModel.SOURCE_ALBUM_PREFIX + id) },
                onToggle = vm::ratioJurisToggle,
                onToggleGroup = vm::ratioJurisToggleGroup,
                onLongPress = actions.onLongPress,
                onPinch = { zoomIn -> vm.ratioJurisSetDensity(settings.gridDensity.ratioJurisPinch(zoomIn)) },
                contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
                header = if (isProtected) {
                    {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "intro") {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text(stringResource(R.string.protected_intro_title), style = MaterialTheme.typography.titleSmall, color = ios.label)
                                Spacer(Modifier.height(2.dp))
                                Text(stringResource(R.string.protected_intro_body), style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
                            }
                        }
                        if (entries.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                                RatioJurisCenteredMessage(Icons.Rounded.Lock, stringResource(R.string.no_protected_title), stringResource(R.string.no_protected_body),
                                    secondaryAction = stringResource(R.string.import_protected) to actions.onImport)
                            }
                        }
                    }
                } else null,
            )
        }
    }
}

// =====================================================================================  Search

@Composable
fun RatioJurisSearchTab(vm: RatioJurisGalleryViewModel, actions: RatioJurisGridActions, bottomInset: Dp, onOpenCollection: (String) -> Unit) {
    val ios = LocalRatioJurisIos.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val months by vm.months.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    BackHandler(enabled = selecting) { vm.ratioJurisClearSelection() }
    Column(Modifier.fillMaxSize().background(ios.background).statusBarsPadding()) {
        RatioJurisLargeTitle(stringResource(R.string.search))
        RatioJurisSearchField(query, { vm.query.value = it }, stringResource(R.string.search_hint), Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        if (query.isBlank()) {
            LazyColumn(contentPadding = PaddingValues(bottom = bottomInset + 24.dp)) {
                if (library.albums.isNotEmpty()) {
                    item { RatioJurisSectionHeader(stringResource(R.string.tab_albums), null) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(library.albums, key = { it.bucketId }) { a -> RatioJurisAlbumCard(a, Modifier.width(120.dp)) { onOpenCollection(a.bucketId) } }
                        }
                    }
                }
                if (months.isNotEmpty()) {
                    item { RatioJurisSectionHeader(stringResource(R.string.months), null) }
                    val recent = months.takeLast(12).reversed()
                    recent.forEachIndexed { i, m ->
                        item(key = m.key) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().clickable { vm.query.value = "${m.title} ${m.subtitle.substringBefore(' ')}" }.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RatioJurisCover(m.cover, settings.showProtectedThumbnails, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(m.title, style = MaterialTheme.typography.bodyLarge, color = ios.label)
                                        Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
                                    }
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = ios.tertiaryLabel, modifier = Modifier.size(14.dp))
                                }
                                if (i < recent.lastIndex) RatioJurisHairline(startIndent = 82.dp)
                            }
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            RatioJurisCenteredMessage(Icons.Outlined.SearchOff, stringResource(R.string.no_results_title), stringResource(R.string.no_results_body, query))
        } else {
            RatioJurisPhotoGrid(
                entries = results,
                selection = selection,
                selecting = selecting,
                baseColumns = settings.gridDensity.columns,
                showProtectedThumbnails = settings.showProtectedThumbnails,
                onOpen = { actions.onOpen(it, RatioJurisGalleryViewModel.SOURCE_SEARCH) },
                onToggle = vm::ratioJurisToggle,
                onToggleGroup = vm::ratioJurisToggleGroup,
                onLongPress = actions.onLongPress,
                contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "count") {
                        Text(
                            pluralStringResource(R.plurals.item_count, results.size, results.size),
                            style = MaterialTheme.typography.titleSmall, color = ios.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                },
            )
        }
    }
}

// =====================================================================================  Welcome

@Composable
fun RatioJurisWelcome(onAllow: () -> Unit, onSkip: () -> Unit, permanentlyDenied: Boolean, onOpenSettings: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Column(
        Modifier
            .fillMaxSize()
            .background(ios.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(0.5.dp, ios.separator, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(140.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displaySmall, color = ios.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        RatioJurisWelcomePoint(Icons.Outlined.PhotoLibrary, R.string.welcome_point_gallery_title, R.string.welcome_point_gallery)
        RatioJurisWelcomePoint(Icons.Outlined.Lock, R.string.welcome_point_protect_title, R.string.welcome_point_protect)
        RatioJurisWelcomePoint(Icons.Outlined.CloudOff, R.string.welcome_point_private_title, R.string.welcome_point_private)
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ios.tint)
                .clickable(role = Role.Button, onClick = if (permanentlyDenied) onOpenSettings else onAllow),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(if (permanentlyDenied) R.string.open_settings else R.string.continue_label), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        RatioJurisTextButton(stringResource(R.string.not_now), onSkip)
    }
}

@Composable
private fun RatioJurisWelcomePoint(icon: ImageVector, title: Int, text: Int) {
    val ios = LocalRatioJurisIos.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = ios.tint, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = ios.label)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = ios.secondaryLabel)
        }
    }
}
