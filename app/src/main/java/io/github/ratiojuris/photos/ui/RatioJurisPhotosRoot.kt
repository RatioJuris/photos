// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisGalleryItem
import io.github.ratiojuris.photos.data.RatioJurisMediaAccess
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedPhoto
import io.github.ratiojuris.photos.ratioJurisContainer
import io.github.ratiojuris.photos.ui.components.RatioJurisActionSheet
import io.github.ratiojuris.photos.ui.components.RatioJurisAlert
import io.github.ratiojuris.photos.ui.components.RatioJurisAlertButton
import io.github.ratiojuris.photos.ui.components.RatioJurisButtonStyle
import io.github.ratiojuris.photos.ui.gallery.RatioJurisBinScreen
import io.github.ratiojuris.photos.ui.gallery.RatioJurisPlacesScreen
import androidx.compose.material.icons.outlined.Archive
import io.github.ratiojuris.photos.ui.components.RatioJurisHairline
import io.github.ratiojuris.photos.ui.components.RatioJurisLoading
import io.github.ratiojuris.photos.ui.components.RatioJurisOperationDialogs
import io.github.ratiojuris.photos.ui.components.RatioJurisProtectDialog
import io.github.ratiojuris.photos.ui.components.RatioJurisSheetAction
import io.github.ratiojuris.photos.ui.components.RatioJurisTabBar
import io.github.ratiojuris.photos.ui.components.RatioJurisTabItem
import io.github.ratiojuris.photos.ui.components.RatioJurisToast
import io.github.ratiojuris.photos.ui.components.rememberRatioJurisPhotoDeleter
import io.github.ratiojuris.photos.ui.gallery.RatioJurisAlbumsTab
import io.github.ratiojuris.photos.ui.gallery.RatioJurisAllAlbumsScreen
import io.github.ratiojuris.photos.ui.gallery.RatioJurisCollectionScreen
import io.github.ratiojuris.photos.ui.gallery.RatioJurisGalleryViewModel
import io.github.ratiojuris.photos.ui.gallery.RatioJurisGridActions
import io.github.ratiojuris.photos.ui.gallery.RatioJurisLibraryTab
import io.github.ratiojuris.photos.ui.gallery.RatioJurisSearchTab
import io.github.ratiojuris.photos.ui.gallery.RatioJurisWelcome
import io.github.ratiojuris.photos.ui.settings.RatioJurisSettingsScreen
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import io.github.ratiojuris.photos.ui.viewer.RatioJurisViewerItem
import io.github.ratiojuris.photos.ui.viewer.RatioJurisViewerScreen
import kotlinx.coroutines.launch

/** iOS-like ease-out curve. */
fun <T> ratioJurisIosTween(durationMs: Int = 350) = tween<T>(durationMs, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))

fun ratioJurisMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

fun RatioJurisGalleryItem.ratioJurisToViewerItem(): RatioJurisViewerItem = when (this) {
    is RatioJurisPhoto -> RatioJurisViewerItem.Ordinary(key, uri, displayName, this)
    is RatioJurisProtectedPhoto -> RatioJurisViewerItem.Protected(key, uri, displayName, this)
}

@Composable
fun RatioJurisPhotosRoot(vm: RatioJurisGalleryViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val ios = LocalRatioJurisIos.current
    val container = context.ratioJurisContainer
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val library by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val opState by container.operations.state.collectAsStateWithLifecycle()

    var welcomeDismissed by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var protectTargets by remember { mutableStateOf<List<RatioJurisPhoto>?>(null) }
    var deleteTargets by remember { mutableStateOf<List<RatioJurisGalleryItem>?>(null) }
    var contextItem by remember { mutableStateOf<RatioJurisGalleryItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var askMaps by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.ratioJurisRefresh() }
    val requestLocation = { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }

    val permissions = remember { ratioJurisMediaPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.ratioJurisRefresh()
        permanentlyDenied = result.values.none { it } && activity != null && permissions.none { activity.shouldShowRequestPermissionRationale(it) }
    }
    val openAppSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { container.vault.ratioJurisImport(uri) }
                .fold({ context.getString(R.string.imported_message) }, { context.getString(R.string.error_import_failed) })
            vm.ratioJurisRefresh()
        }
    }
    val deleter = rememberRatioJurisPhotoDeleter(scope) { ok ->
        vm.ratioJurisClearSelection()
        if (!ok) message = context.getString(R.string.delete_cancelled)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.ratioJurisRefresh() }

    val useBin = settings.useBin && Build.VERSION.SDK_INT >= 30
    fun startDelete(items: List<RatioJurisGalleryItem>) {
        if (items.isEmpty()) return
        val photos = items.filterIsInstance<RatioJurisPhoto>()
        if (useBin) {
            // Everything (photos, videos and protected files) goes to the system Bin; the platform confirms.
            deleter.ratioJurisDelete(items.map { it.uri }, toBin = true)
        } else if (items.size == photos.size && Build.VERSION.SDK_INT >= 30) {
            deleter.ratioJurisDelete(photos.map { it.uri }) // the system dialog is the confirmation
        } else {
            deleteTargets = items
        }
    }
    fun startProtect(items: List<RatioJurisGalleryItem>) {
        // Encryption is for still photos only; videos in a selection are left as they are.
        val photos = items.filterIsInstance<RatioJurisPhoto>().filter { it.canProtect }
        if (items.any { it is RatioJurisPhoto && !it.canProtect }) message = context.getString(R.string.videos_not_protected)
        if (photos.isNotEmpty()) protectTargets = photos
    }

    /** Archives the selection, or un-archives it when everything selected is already archived. */
    fun archiveSelection() {
        val keys = vm.selection.value
        val allArchived = keys.isNotEmpty() && keys.all { it in library.archived }
        vm.ratioJurisSetArchived(keys, !allArchived)
        vm.ratioJurisClearSelection()
        message = context.getString(if (allArchived) R.string.unarchived_message else R.string.archived_message)
    }

    val actions = RatioJurisGridActions(
        onOpen = { item, source -> nav.navigate("viewer/${Uri.encode(source)}/${Uri.encode(item.key)}") },
        onLongPress = { contextItem = it },
        onRequestPermission = { permissionLauncher.launch(permissions) },
        permissionPermanentlyDenied = permanentlyDenied,
        onOpenAppSettings = openAppSettings,
        onOpenSettings = { nav.navigate("settings") },
        onImport = { importLauncher.launch(arrayOf("*/*")) },
    )

    val showWelcome = !library.loading && library.access == RatioJurisMediaAccess.DENIED && !welcomeDismissed && library.protected.isEmpty()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navInset + 53.dp

    Box(Modifier.fillMaxSize().background(ios.background)) {
        when {
            library.loading && library.photos.isEmpty() -> RatioJurisLoading()
            showWelcome -> RatioJurisWelcome(
                onAllow = { permissionLauncher.launch(permissions) },
                onSkip = { welcomeDismissed = true },
                permanentlyDenied = permanentlyDenied,
                onOpenSettings = openAppSettings,
            )
            else -> NavHost(
                nav,
                startDestination = "home",
                // iOS push / pop: the new screen slides in from the right, the old one moves a third and dims.
                enterTransition = { slideInHorizontally(ratioJurisIosTween()) { it } },
                exitTransition = { slideOutHorizontally(ratioJurisIosTween()) { -it / 3 } + fadeOut(ratioJurisIosTween(), targetAlpha = 0.7f) },
                popEnterTransition = { slideInHorizontally(ratioJurisIosTween()) { -it / 3 } + fadeIn(ratioJurisIosTween(), initialAlpha = 0.7f) },
                popExitTransition = { slideOutHorizontally(ratioJurisIosTween()) { it } },
            ) {
                composable("home") {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            0 -> RatioJurisLibraryTab(vm, actions, bottomInset)
                            1 -> RatioJurisAlbumsTab(
                                vm, actions, bottomInset,
                                onOpenCollection = { nav.navigate("collection/${Uri.encode(it)}") },
                                onSeeAll = { nav.navigate("albums") },
                                onOpenPlaces = { nav.navigate("places") },
                                onOpenBin = { nav.navigate("bin") },
                            )
                            else -> RatioJurisSearchTab(vm, actions, bottomInset, onOpenCollection = { nav.navigate("collection/${Uri.encode(it)}") })
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            if (selecting) {
                                RatioJurisSelectionToolbar(
                                    count = selection.size,
                                    canProtect = vm.ratioJurisSelectedItems().any { it is RatioJurisPhoto && it.canProtect },
                                    onShare = { container.sharing.ratioJurisShareItems(context, vm.ratioJurisSelectedItems()) },
                                    onProtect = { startProtect(vm.ratioJurisSelectedItems()) },
                                    onDelete = { startDelete(vm.ratioJurisSelectedItems()) },
                                    onArchive = { archiveSelection() },
                                )
                            } else {
                                RatioJurisTabBar(
                                    items = listOf(
                                        RatioJurisTabItem(stringResource(R.string.library), Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary),
                                        RatioJurisTabItem(stringResource(R.string.tab_albums), Icons.Outlined.Collections, Icons.Rounded.Collections),
                                        RatioJurisTabItem(stringResource(R.string.search), Icons.Outlined.Search, Icons.Rounded.Search),
                                    ),
                                    selected = tab,
                                    onSelect = { if (it != tab) { vm.ratioJurisClearSelection(); tab = it } },
                                )
                            }
                        }
                    }
                }
                composable("albums") {
                    RatioJurisAllAlbumsScreen(vm, onBack = { nav.popBackStack() }, onOpenCollection = { nav.navigate("collection/${Uri.encode(it)}") })
                }
                composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    Box(Modifier.fillMaxSize()) {
                        RatioJurisCollectionScreen(vm, id, actions, if (selecting) bottomInset else navInset) { nav.popBackStack() }
                        if (selecting) {
                            Box(Modifier.align(Alignment.BottomCenter)) {
                                RatioJurisSelectionToolbar(
                                    count = selection.size,
                                    canProtect = vm.ratioJurisSelectedItems().any { it is RatioJurisPhoto && it.canProtect },
                                    onShare = { container.sharing.ratioJurisShareItems(context, vm.ratioJurisSelectedItems()) },
                                    onProtect = { startProtect(vm.ratioJurisSelectedItems()) },
                                    onDelete = { startDelete(vm.ratioJurisSelectedItems()) },
                                    onArchive = { archiveSelection() },
                                )
                            }
                        }
                    }
                }
                composable("settings") { RatioJurisSettingsScreen(vm.settings, onRequestLocationPermission = requestLocation) { nav.popBackStack() } }
                composable("bin") { RatioJurisBinScreen(vm, deleter) { nav.popBackStack() } }
                composable("places") {
                    RatioJurisPlacesScreen(
                        vm,
                        mapsEnabled = settings.mapsEnabled,
                        onEnableMaps = { askMaps = true },
                        onBack = { nav.popBackStack() },
                        onOpenCluster = { nav.navigate("collection/${Uri.encode(RatioJurisGalleryViewModel.COLLECTION_PLACE)}") },
                    )
                }
                composable(
                    "viewer/{source}/{key}",
                    arguments = listOf(navArgument("source") { type = NavType.StringType }, navArgument("key") { type = NavType.StringType }),
                    // Photos open by zooming up from the grid and close by fading, like iOS.
                    enterTransition = { fadeIn(ratioJurisIosTween(260)) + scaleIn(ratioJurisIosTween(300), initialScale = 0.88f) },
                    exitTransition = { fadeOut(ratioJurisIosTween(200)) },
                    popEnterTransition = { fadeIn(ratioJurisIosTween(200)) },
                    popExitTransition = { fadeOut(ratioJurisIosTween(220)) + scaleOut(ratioJurisIosTween(260), targetScale = 0.92f) },
                ) { entry ->
                    val source = entry.arguments?.getString("source").orEmpty()
                    val key = entry.arguments?.getString("key")
                    val timeline by vm.timeline.collectAsStateWithLifecycle()
                    val results by vm.searchResults.collectAsStateWithLifecycle()
                    val items = remember(library, timeline, results, settings, source) { vm.ratioJurisViewerItems(source).map { it.ratioJurisToViewerItem() } }
                    if (library.loading) {
                        RatioJurisLoading()
                    } else {
                        RatioJurisViewerScreen(
                            items = items,
                            initialKey = key,
                            settings = settings,
                            onBack = { nav.popBackStack() },
                            onProtect = { if (it.canProtect) protectTargets = listOf(it) },
                            onDeletePhoto = { startDelete(listOf(it)) },
                            onLibraryChanged = { vm.ratioJurisRefresh() },
                            isArchived = { it in library.archived },
                            onArchive = { item, archive ->
                                vm.ratioJurisSetArchived(listOf(item.key), archive)
                                message = context.getString(if (archive) R.string.archived_message else R.string.unarchived_message)
                            },
                            onEnableMaps = { askMaps = true },
                            onRequestLocationPermission = requestLocation,
                        )
                    }
                }
            }
        }
        RatioJurisToast(message, onShown = { message = null }, modifier = Modifier.align(Alignment.Center))
    }

    // ------------------------------------------------------------------ flows shared by every screen
    contextItem?.let { item ->
        RatioJurisActionSheet(
            title = if (item is RatioJurisProtectedPhoto) null else item.displayName,
            actions = buildList {
                add(RatioJurisSheetAction(stringResource(R.string.share)) {
                    contextItem = null; container.sharing.ratioJurisShareItems(context, listOf(item))
                })
                if (item is RatioJurisPhoto && item.canProtect) add(RatioJurisSheetAction(stringResource(R.string.action_protect_ellipsis)) { contextItem = null; startProtect(listOf(item)) })
                val archived = item.key in library.archived
                add(RatioJurisSheetAction(stringResource(if (archived) R.string.unarchive else R.string.archive_action)) {
                    contextItem = null
                    vm.ratioJurisSetArchived(listOf(item.key), !archived)
                    message = context.getString(if (archived) R.string.unarchived_message else R.string.archived_message)
                })
                add(RatioJurisSheetAction(stringResource(R.string.select)) { contextItem = null; vm.ratioJurisStartSelecting(item.key) })
                add(RatioJurisSheetAction(stringResource(if (useBin) R.string.move_to_bin else R.string.delete), destructive = true) { contextItem = null; startDelete(listOf(item)) })
            },
            cancelLabel = stringResource(R.string.cancel),
            onDismiss = { contextItem = null },
        )
    }
    protectTargets?.let { targets ->
        RatioJurisProtectDialog(
            count = targets.size,
            defaultPolicy = settings.thumbnailPolicy,
            defaultRemoveOriginals = settings.removeOriginals,
            requireConfirmation = settings.confirmPassword,
            onConfirm = { choice ->
                protectTargets = null
                vm.ratioJurisClearSelection()
                container.operations.ratioJurisProtect(targets, choice.password, choice.policy, choice.removeOriginals)
            },
            onDismiss = { protectTargets = null },
        )
    }
    if (askMaps) {
        RatioJurisAlert(
            title = stringResource(R.string.maps_consent_title),
            message = stringResource(R.string.maps_consent_body),
            onDismiss = { askMaps = false },
            buttons = listOf(
                RatioJurisAlertButton(stringResource(R.string.not_now), RatioJurisButtonStyle.DEFAULT) { askMaps = false },
                RatioJurisAlertButton(stringResource(R.string.maps_enable), RatioJurisButtonStyle.PREFERRED) {
                    askMaps = false
                    scope.launch { container.settings.ratioJurisSetMapsEnabled(true) }
                },
            ),
        )
    }
    RatioJurisOperationDialogs(
        state = opState,
        onRemoveOriginals = { deleter.ratioJurisDelete(it) },
        onAcknowledge = { container.operations.ratioJurisAcknowledge(); vm.ratioJurisRefresh() },
    )
    deleteTargets?.let { targets ->
        val lockedCount = targets.count { it is RatioJurisProtectedPhoto }
        RatioJurisActionSheet(
            message = stringResource(if (lockedCount > 0) R.string.delete_body_protected else R.string.delete_body),
            actions = listOf(
                RatioJurisSheetAction(pluralStringResource(R.plurals.delete_action, targets.size, targets.size), destructive = true) {
                    deleteTargets = null
                    scope.launch {
                        // Own files are deleted directly; anything Photos no longer owns goes through the system consent dialog.
                        val needConsent = targets.filterIsInstance<RatioJurisProtectedPhoto>().filterNot { container.vault.ratioJurisDelete(it) }
                        val uris = targets.filterIsInstance<RatioJurisPhoto>().map { it.uri } + needConsent.map { it.uri }
                        if (uris.isNotEmpty()) deleter.ratioJurisDelete(uris) else vm.ratioJurisClearSelection()
                        vm.ratioJurisRefresh()
                    }
                },
            ),
            cancelLabel = stringResource(R.string.cancel),
            onDismiss = { deleteTargets = null },
        )
    }
}

/** Replaces the tab bar while selecting: Share · Protect · "N Selected" · Archive · Delete. */
@Composable
private fun RatioJurisSelectionToolbar(count: Int, canProtect: Boolean, onShare: () -> Unit, onProtect: () -> Unit, onDelete: () -> Unit, onArchive: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Column(Modifier.fillMaxWidth().background(ios.bar)) {
        RatioJurisHairline()
        Box(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp).padding(horizontal = 8.dp)) {
            Row(Modifier.align(Alignment.CenterStart)) {
                SelectionIcon(Icons.Outlined.IosShare, stringResource(R.string.share), enabled = count > 0, onClick = onShare)
                if (canProtect) SelectionIcon(Icons.Outlined.Lock, stringResource(R.string.action_protect), enabled = count > 0, onClick = onProtect)
            }
            Text(
                if (count == 0) stringResource(R.string.select_items) else pluralStringResource(R.plurals.selected_count, count, count),
                style = MaterialTheme.typography.titleSmall,
                color = ios.label,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(Modifier.align(Alignment.CenterEnd)) {
                SelectionIcon(Icons.Outlined.Archive, stringResource(R.string.archive_action), enabled = count > 0, onClick = onArchive)
                SelectionIcon(Icons.Outlined.Delete, stringResource(R.string.delete), enabled = count > 0, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun SelectionIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) ios.tint else ios.tertiaryLabel, modifier = Modifier.size(26.dp))
    }
}
