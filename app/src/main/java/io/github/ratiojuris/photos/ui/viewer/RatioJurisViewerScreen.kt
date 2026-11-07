// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.viewer

import android.text.format.DateUtils
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisMediaThumb
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedThumb
import io.github.ratiojuris.photos.data.RatioJurisSettingsState
import io.github.ratiojuris.photos.data.ratioJurisShareUri
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxException
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxHeader
import io.github.ratiojuris.photos.ratioJurisContainer
import io.github.ratiojuris.photos.ui.components.RatioJurisActionSheet
import io.github.ratiojuris.photos.ui.components.RatioJurisChangePasswordDialog
import io.github.ratiojuris.photos.ui.components.RatioJurisConfirmDialog
import io.github.ratiojuris.photos.ui.components.RatioJurisHairline
import io.github.ratiojuris.photos.ui.components.RatioJurisLockedArt
import io.github.ratiojuris.photos.ui.components.RatioJurisPasswordDialog
import io.github.ratiojuris.photos.ui.components.RatioJurisSheetAction
import io.github.ratiojuris.photos.ui.components.RatioJurisToast
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisExtras
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen viewer (iOS-style chrome). Protected pages show only the public preview
 * until the password authenticates the original.
 */
@Composable
fun RatioJurisViewerScreen(
    items: List<RatioJurisViewerItem>,
    initialKey: String?,
    settings: RatioJurisSettingsState,
    onBack: () -> Unit,
    onProtect: ((RatioJurisPhoto) -> Unit)?,
    onDeletePhoto: ((io.github.ratiojuris.photos.data.RatioJurisGalleryItem) -> Unit)?,
    onLibraryChanged: () -> Unit,
    vm: RatioJurisViewerViewModel = viewModel(),
    isArchived: (String) -> Boolean = { false },
    onArchive: ((io.github.ratiojuris.photos.data.RatioJurisGalleryItem, Boolean) -> Unit)? = null,
    onEnableMaps: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val ios = LocalRatioJurisIos.current
    val scope = rememberCoroutineScope()

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val initialIndex = rememberSaveable { items.indexOfFirst { it.key == initialKey }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = initialIndex.coerceAtMost(items.lastIndex)) { items.size }
    val zoomStates = remember { HashMap<String, RatioJurisZoomState>() }
    fun zoomFor(key: String) = zoomStates.getOrPut(key) { RatioJurisZoomState() }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    var chrome by rememberSaveable { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var shareSheet by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var confirmShareOriginal by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    val unlocked = vm.unlocked
    val currentUnlocked = unlocked?.takeIf { it.key == current.key }
    val background by animateColorAsState(if (chrome && !ios.isDark) Color.White else Color.Black, label = "viewerBg")

    LaunchedEffect(unlocked, vm.pendingAction) {
        if (unlocked != null) {
            when (vm.pendingAction) {
                RatioJurisPendingAction.SHARE_ORIGINAL -> confirmShareOriginal = true
                RatioJurisPendingAction.RESTORE -> confirmRestore = true
                RatioJurisPendingAction.NONE -> Unit
            }
            vm.pendingAction = RatioJurisPendingAction.NONE
        }
    }
    // Leaving the page of an unlocked original locks it again.
    LaunchedEffect(pager.currentPage) {
        if (unlocked != null && unlocked.key != current.key) vm.ratioJurisRelock()
        zoomStates.forEach { (k, z) -> if (k != current.key) z.reset() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (settings.relockOnBackground) vm.ratioJurisRelock()
    }
    // Block screenshots and the recents thumbnail while an original is visible.
    DisposableEffect(currentUnlocked != null, settings.secureScreen) {
        val window = activity?.window
        if (currentUnlocked != null && settings.secureScreen) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    DisposableEffect(chrome, ios.isDark) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.isAppearanceLightStatusBars = chrome && !ios.isDark
        if (chrome) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = !ios.isDark
        }
    }

    val dragFade = (1f - kotlin.math.abs(zoomFor(current.key).dragY) / 900f).coerceIn(0.25f, 1f)
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(background.copy(alpha = dragFade))
            .focusRequester(keyFocus)
            .focusable()
            // Keyboard / D-pad: left-right between photos, up for details, down or Esc to close.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.PageUp -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                    Key.DirectionRight, Key.PageDown, Key.Spacebar -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(items.lastIndex)) }; true }
                    Key.DirectionUp -> { showInfo = true; true }
                    Key.DirectionDown, Key.Escape -> { onBack(); true }
                    Key.DirectionCenter, Key.Enter -> { chrome = !chrome; true }
                    else -> false
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            key = { items[it].key },
            // Always enabled: a zoomed photo consumes its own pans and hands edge swipes to the pager.
            pageSpacing = 16.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            ),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (val item = items[page]) {
                is RatioJurisViewerItem.Ordinary -> if (item.isVideo) {
                    RatioJurisVideoPage(item.uri, active = page == pager.currentPage, chrome = chrome, onTap = { chrome = !chrome }, onSwipeDown = onBack, onSwipeUp = { showInfo = true })
                } else {
                    RatioJurisOrdinaryPage(item, zoomFor(item.key), onBack, { showInfo = true }) { chrome = !chrome }
                }
                is RatioJurisViewerItem.Protected -> {
                    LaunchedEffect(item.key) { vm.ratioJurisEnsureHeader(item) }
                    val u = unlocked?.takeIf { it.key == item.key }
                    if (u != null) {
                        RatioJurisZoomable(zoomFor(item.key), onTap = { chrome = !chrome }, onSwipeDown = onBack, onSwipeUp = { showInfo = true }) {
                            Image(u.bitmap, contentDescription = stringResource(R.string.cd_original_unlocked), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        RatioJurisPreviewPage(vm.headers[item.key], zoomFor(item.key), onBack, { showInfo = true }) { chrome = !chrome }
                    }
                }
            }
        }

        // ------------------------------------------------------------------ top bar
        AnimatedVisibility(chrome && dragFade > 0.97f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxWidth().background(ios.bar).statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                            .semantics { contentDescription = context.getString(R.string.back) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = ios.tint, modifier = Modifier.size(22.dp)) }
                    val millis = when (current) {
                        is RatioJurisViewerItem.Ordinary -> current.photo?.dateMillis
                        is RatioJurisViewerItem.Protected -> current.stored?.dateMillis
                    }
                    Column(Modifier.align(Alignment.Center).widthIn(max = 240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            // Never label a photo as protected: show its date, or just its file name.
                            millis?.let { ratioJurisDayTitle(context, it) } ?: current.name,
                            style = MaterialTheme.typography.titleSmall, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (millis != null) {
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis)), style = MaterialTheme.typography.labelMedium, color = ios.secondaryLabel)
                        }
                    }
                    // Present on every photo, so it reveals nothing about which ones are protected.
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape).clickable(role = Role.Button) { moreSheet = true }
                            .semantics { contentDescription = context.getString(R.string.more) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.MoreHoriz, null, tint = ios.tint) }
                }
                RatioJurisHairline()
                // Only after successful authentication, so the owner knows they are seeing the original.
                if (currentUnlocked != null) RatioJurisStatusCapsule(onLock = { vm.ratioJurisRelock() })
            }
        }

        // ------------------------------------------------------------------ filmstrip + toolbar
        AnimatedVisibility(chrome && dragFade > 0.97f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(ios.bar)) {
                if (items.size > 1) {
                    RatioJurisFilmstrip(items, pager.currentPage, settings.showProtectedThumbnails) { scope.launch { pager.animateScrollToPage(it) } }
                }
                RatioJurisHairline()
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (val item = current) {
                        is RatioJurisViewerItem.Ordinary -> {
                            ToolbarIcon(Icons.Outlined.IosShare, stringResource(R.string.share)) {
                                if (item.photo != null) context.ratioJurisContainer.sharing.ratioJurisSharePhotos(context, listOf(item.photo))
                                else context.ratioJurisShareUri(item.uri)
                            }
                            if (item.photo != null && item.photo.canProtect && onProtect != null) ToolbarIcon(Icons.Outlined.Lock, stringResource(R.string.action_protect)) { onProtect(item.photo) }
                            ToolbarIcon(Icons.Outlined.Info, stringResource(R.string.info)) { showInfo = true }
                            if (item.photo != null && onDeletePhoto != null) ToolbarIcon(Icons.Outlined.Delete, stringResource(R.string.delete)) { onDeletePhoto(item.photo) }
                        }
                        is RatioJurisViewerItem.Protected -> {
                            ToolbarIcon(Icons.Outlined.IosShare, stringResource(R.string.share)) { shareSheet = true }
                            if (item.stored == null) ToolbarIcon(Icons.Outlined.Download, stringResource(R.string.save_to_photos)) { vm.ratioJurisImport(item.uri) { onLibraryChanged() } }
                            ToolbarIcon(Icons.Outlined.Info, stringResource(R.string.info)) { showInfo = true }
                            if (item.stored != null && onDeletePhoto != null) ToolbarIcon(Icons.Outlined.Delete, stringResource(R.string.delete)) { onDeletePhoto(item.stored) }
                        }
                    }
                }
            }
        }

        RatioJurisToast(vm.message, onShown = { vm.message = null }, modifier = Modifier.align(Alignment.Center))
    }

    // ---------------------------------------------------------------------- sheets & alerts
    val protectedCurrent = current as? RatioJurisViewerItem.Protected
    when (val ui = vm.unlockUi) {
        is RatioJurisUnlockUi.Password -> {
            val target = items.firstOrNull { it.key == ui.key } as? RatioJurisViewerItem.Protected
            if (target != null) {
                RatioJurisPasswordDialog(
                    title = stringResource(R.string.unlock_title),
                    busy = ui.busy,
                    onSubmit = { vm.ratioJurisUnlock(target, it) },
                    onDismiss = { vm.ratioJurisDismissUnlock() },
                )
            }
        }
        RatioJurisUnlockUi.None -> Unit
    }
    if (shareSheet && protectedCurrent != null) {
        RatioJurisActionSheet(
            actions = buildList {
                add(RatioJurisSheetAction(stringResource(R.string.share_file)) {
                    shareSheet = false
                    context.ratioJurisContainer.sharing.ratioJurisShareProtectedUri(context, protectedCurrent.uri)
                })
                if (vm.headers[protectedCurrent.key]?.getOrNull()?.thumbnail != null) {
                    add(RatioJurisSheetAction(stringResource(R.string.share_preview_image)) {
                        shareSheet = false
                        vm.ratioJurisShareThumbnail(context, protectedCurrent)
                    })
                }
                // The plaintext export exists only inside an authenticated session.
                if (currentUnlocked != null) {
                    add(RatioJurisSheetAction(stringResource(R.string.share_original)) { shareSheet = false; confirmShareOriginal = true })
                }
            },
            cancelLabel = stringResource(R.string.cancel),
            onDismiss = { shareSheet = false },
        )
    }
    if (moreSheet) {
        RatioJurisActionSheet(
            actions = buildList {
                when (val item = current) {
                    is RatioJurisViewerItem.Ordinary -> {
                        if (item.photo != null && item.photo.canProtect && onProtect != null) add(RatioJurisSheetAction(stringResource(R.string.action_protect_ellipsis)) { moreSheet = false; onProtect(item.photo) })
                        if (item.photo != null && onArchive != null) {
                            val archived = isArchived(item.photo.key)
                            add(RatioJurisSheetAction(stringResource(if (archived) R.string.unarchive else R.string.archive_action)) { moreSheet = false; onArchive(item.photo, !archived) })
                        }
                        add(RatioJurisSheetAction(stringResource(R.string.info_title)) { moreSheet = false; showInfo = true })
                    }
                    is RatioJurisViewerItem.Protected -> if (currentUnlocked == null) {
                        if (item.stored != null && onArchive != null) {
                            val archived = isArchived(item.stored.key)
                            add(RatioJurisSheetAction(stringResource(if (archived) R.string.unarchive else R.string.archive_action)) { moreSheet = false; onArchive(item.stored, !archived) })
                        }
                        add(RatioJurisSheetAction(stringResource(R.string.info_title)) { moreSheet = false; showInfo = true })
                        add(RatioJurisSheetAction(stringResource(R.string.prompt_continue), small = true) { moreSheet = false; vm.ratioJurisRequestUnlock(item.key) })
                    } else {
                        if (item.stored != null) add(RatioJurisSheetAction(stringResource(R.string.change_password)) { moreSheet = false; changePassword = true })
                        add(RatioJurisSheetAction(stringResource(R.string.save_unprotected_copy)) { moreSheet = false; confirmRestore = true })
                        add(RatioJurisSheetAction(stringResource(R.string.lock)) { moreSheet = false; vm.ratioJurisRelock() })
                    }
                }
            },
            cancelLabel = stringResource(R.string.cancel),
            onDismiss = { moreSheet = false },
        )
    }
    if (confirmShareOriginal) {
        RatioJurisConfirmDialog(
            title = stringResource(R.string.share_original_title),
            body = stringResource(R.string.share_original_body),
            confirmLabel = stringResource(R.string.share_original_confirm),
            destructive = false,
            onConfirm = { confirmShareOriginal = false; vm.ratioJurisShareOriginal(context) },
            onDismiss = { confirmShareOriginal = false },
        )
    }
    if (confirmRestore) {
        RatioJurisConfirmDialog(
            title = stringResource(R.string.restore_title),
            body = stringResource(R.string.restore_body),
            confirmLabel = stringResource(R.string.restore_confirm),
            destructive = false,
            onConfirm = { confirmRestore = false; vm.ratioJurisRestore { onLibraryChanged() } },
            onDismiss = { confirmRestore = false },
        )
    }
    val cp = vm.changePasswordState
    if ((changePassword || cp != null) && protectedCurrent?.stored != null) {
        RatioJurisChangePasswordDialog(
            busy = cp?.first == true,
            error = cp?.second,
            requireConfirmation = settings.confirmPassword,
            onSubmit = { old, new -> vm.ratioJurisChangePassword(protectedCurrent.stored, old, new) { changePassword = false; onLibraryChanged() } },
            onDismiss = { changePassword = false; vm.changePasswordState = null },
        )
    }
    if (showInfo) {
        RatioJurisInfoSheet(
            item = current,
            header = protectedCurrent?.let { vm.headers[it.key]?.getOrNull() },
            unlocked = currentUnlocked,
            onDismiss = { showInfo = false },
            showLocation = settings.showLocation,
            mapsEnabled = settings.mapsEnabled,
            onEnableMaps = onEnableMaps,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}

@Composable
private fun RatioJurisOrdinaryPage(item: RatioJurisViewerItem.Ordinary, zoom: RatioJurisZoomState, onSwipeDown: () -> Unit, onSwipeUp: () -> Unit, onTap: () -> Unit) {
    val context = LocalContext.current
    var failed by remember(item.key) { mutableStateOf(false) }
    RatioJurisZoomable(zoom, onTap = onTap, onSwipeDown = onSwipeDown, onSwipeUp = onSwipeUp) {
        if (failed) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.BrokenImage, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.error_cannot_display), color = Color(0xFF8E8E93), textAlign = TextAlign.Center)
            }
        } else {
            // Low-resolution preview first, then a bounded full-resolution decode on top.
            item.photo?.let { p ->
                AsyncImage(model = RatioJurisMediaThumb(p.uri, p.dateModifiedMillis), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = remember(item.uri) { ImageRequest.Builder(context).data(item.uri).size(RatioJurisViewerViewModel.MAX_VIEW_DIM).build() },
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A protected photo before authentication: just its public preview, presented like any other photo. */
@Composable
private fun RatioJurisPreviewPage(header: Result<RatioJurisPhtxHeader>?, zoom: RatioJurisZoomState, onSwipeDown: () -> Unit, onSwipeUp: () -> Unit, onTap: () -> Unit) {
    RatioJurisZoomable(zoom, onTap = onTap, onSwipeDown = onSwipeDown, onSwipeUp = onSwipeUp) {
        val thumb = header?.getOrNull()?.thumbnail
        when {
            header == null -> Unit
            thumb != null -> AsyncImage(model = thumb, contentDescription = stringResource(R.string.cd_photo_generic), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            header.isFailure && header.exceptionOrNull() !is RatioJurisPhtxException -> Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.BrokenImage, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.error_cannot_display), color = Color(0xFF8E8E93), textAlign = TextAlign.Center)
            }
            else -> RatioJurisLockedArt(Modifier.fillMaxSize())
        }
    }
}

/** Shown only after authentication, so the owner knows the original is on screen. */
@Composable
private fun RatioJurisStatusCapsule(onLock: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.clip(CircleShape).background(ios.fill).clickable(role = Role.Button, onClick = onLock).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.status_original_unlocked_tap), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ios.label)
        }
    }
}
/** Thumbnail scrubber: the current photo is shown wider, tap to jump. */
@Composable
private fun RatioJurisFilmstrip(items: List<RatioJurisViewerItem>, current: Int, showProtectedThumbnails: Boolean, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem((current - 4).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
            val width by animateDpAsState(if (index == current) 44.dp else 28.dp, label = "filmWidth")
            Box(
                Modifier
                    .width(width)
                    .height(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(LocalRatioJurisExtras.current.tilePlaceholder)
                    .clickable { onSelect(index) },
            ) {
                when (item) {
                    is RatioJurisViewerItem.Ordinary -> item.photo?.let {
                        AsyncImage(RatioJurisMediaThumb(it.uri, it.dateModifiedMillis), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    is RatioJurisViewerItem.Protected -> {
                        val stored = item.stored
                        if (stored != null && showProtectedThumbnails) {
                            AsyncImage(if (stored.imageBacked) RatioJurisMediaThumb(stored.uri, stored.dateModifiedMillis) else RatioJurisProtectedThumb(stored.uri, stored.dateModifiedMillis), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            RatioJurisLockedArt(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val ios = LocalRatioJurisIos.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ios.tint, modifier = Modifier.size(26.dp))
    }
}

private fun ratioJurisDayTitle(context: android.content.Context, millis: Long): String = when {
    DateUtils.isToday(millis) -> context.getString(R.string.today)
    DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.yesterday)
    else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH)
}

fun ratioJurisDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
