// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.components

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisGalleryItem
import io.github.ratiojuris.photos.data.RatioJurisLatLon
import io.github.ratiojuris.photos.data.RatioJurisMediaThumb
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.ratioJurisContainer
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/** A geotagged item shown on the map. */
data class RatioJurisGeoPoint(val item: RatioJurisGalleryItem, val position: RatioJurisLatLon)

private const val TILE = 256

/** Web Mercator: lat/lon -> world pixel at zoom [z]. */
fun ratioJurisProject(p: RatioJurisLatLon, z: Int): Offset {
    val world = TILE * (1 shl z).toDouble()
    val x = (p.lon + 180.0) / 360.0 * world
    val latRad = Math.toRadians(p.lat.coerceIn(-85.05, 85.05))
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world
    return Offset(x.toFloat(), y.toFloat())
}

fun ratioJurisUnproject(o: Offset, z: Int): RatioJurisLatLon {
    val world = TILE * (1 shl z).toDouble()
    val lon = o.x / world * 360.0 - 180.0
    val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * o.y / world))))
    return RatioJurisLatLon(lat, lon)
}

/**
 * Lightweight slippy map: OpenStreetMap raster tiles, pan and pinch-zoom, clustered photo markers.
 * Callers must only show it when the user has enabled Maps (tiles are downloaded from the internet).
 */
@Composable
fun RatioJurisMap(
    center: RatioJurisLatLon,
    initialZoom: Int,
    points: List<RatioJurisGeoPoint>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showPin: Boolean = false,
    onCluster: (List<RatioJurisGeoPoint>) -> Unit = {},
) {
    val context = LocalContext.current
    val tiles = context.ratioJurisContainer.tiles
    val ios = LocalRatioJurisIos.current
    var zoom by remember { mutableIntStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    var centerWorld by remember { mutableStateOf(ratioJurisProject(center, zoom)) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val loaded = remember { mutableStateMapOf<String, ImageBitmap>() }
    val density = LocalDensity.current

    fun setZoom(newZoom: Int, focus: Offset = Offset(size.width / 2f, size.height / 2f)) {
        val z = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (z == zoom) return
        val topLeft = centerWorld - Offset(size.width / 2f, size.height / 2f)
        val focusWorld = topLeft + focus
        val factor = if (z > zoom) (1 shl (z - zoom)).toFloat() else 1f / (1 shl (zoom - z))
        centerWorld = focusWorld * factor - (focus - Offset(size.width / 2f, size.height / 2f))
        zoom = z
    }

    // Tiles needed for the current view.
    val n = 1 shl zoom
    val topLeft = centerWorld - Offset(size.width / 2f, size.height / 2f)
    val x0 = floor(topLeft.x / TILE).toInt()
    val y0 = floor(topLeft.y / TILE).toInt().coerceAtLeast(0)
    val x1 = floor((topLeft.x + size.width) / TILE).toInt()
    val y1 = floor((topLeft.y + size.height) / TILE).toInt().coerceAtMost(n - 1)
    val needed = buildList {
        for (ty in y0..y1) for (tx in x0..x1) add(Triple(zoom, ((tx % n) + n) % n, ty))
    }
    LaunchedEffect(needed) {
        needed.forEach { (z, x, y) ->
            val key = "$z/$x/$y"
            if (key !in loaded) launch { tiles.ratioJurisTile(z, x, y)?.let { loaded[key] = it } }
        }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .background(if (ios.isDark) Color(0xFF242424) else Color(0xFFEDEAE4))
            .let { m ->
                if (!interactive) m else m
                    .pointerInput(Unit) {
                        var accumulated = 1f
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            centerWorld -= pan
                            accumulated *= gestureZoom
                            if (accumulated > 1.6f) { setZoom(zoom + 1, centroid); accumulated = 1f }
                            if (accumulated < 0.62f) { setZoom(zoom - 1, centroid); accumulated = 1f }
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { setZoom(zoom + 1, it) }) }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for ((z, x, y) in needed) {
                val bmp = loaded["$z/$x/$y"] ?: continue
                // Draw each tile at its unwrapped column so the world repeats horizontally.
                for (tx in x0..x1) {
                    if (((tx % n) + n) % n != x) continue
                    drawImage(bmp, dstOffset = IntOffset((tx * TILE - topLeft.x).roundToInt(), (y * TILE - topLeft.y).roundToInt()), dstSize = IntSize(TILE, TILE))
                }
            }
        }

        // Cluster markers by screen cell so dense areas stay readable.
        val cellPx = with(density) { 64.dp.toPx() }
        val clusters = remember(points, zoom) {
            points.groupBy { p ->
                val w = ratioJurisProject(p.position, zoom)
                Pair(floor(w.x / cellPx).toInt(), floor(w.y / cellPx).toInt())
            }.values.toList()
        }
        clusters.forEach { group ->
            val w = ratioJurisProject(group.first().position, zoom) - topLeft
            if (w.x < -60 || w.y < -60 || w.x > size.width + 60 || w.y > size.height + 60) return@forEach
            val markerSize = 48.dp
            val half = with(density) { (markerSize / 2).roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(w.x.roundToInt() - half, w.y.roundToInt() - half) }
                    .size(markerSize)
                    .shadow(4.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(role = Role.Button) { onCluster(group) }
                    .semantics { contentDescription = context.resources.getQuantityString(R.plurals.item_count, group.size, group.size) },
            ) {
                val cover = group.first().item
                if (cover is RatioJurisPhoto) {
                    AsyncImage(RatioJurisMediaThumb(cover.uri, cover.dateModifiedMillis), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (group.size > 1) {
                    Text(
                        group.size.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).clip(CircleShape).background(ios.tint).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        if (showPin) {
            val w = ratioJurisProject(center, zoom) - topLeft
            val r = with(density) { 9.dp.roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(w.x.roundToInt() - r, w.y.roundToInt() - r) }
                    .size(18.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(ios.tint),
            )
        }

        if (interactive) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .width(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ios.isDark) Color(0xE62C2C2E) else Color(0xEBFFFFFF)),
            ) {
                MapButton(Icons.Rounded.Add, stringResource(R.string.zoom_in)) { setZoom(zoom + 1) }
                RatioJurisHairline()
                MapButton(Icons.Rounded.Remove, stringResource(R.string.zoom_out)) { setZoom(zoom - 1) }
            }
        }
        // Required attribution (OpenStreetMap ODbL / tile usage policy).
        Text(
            stringResource(R.string.osm_attribution),
            fontSize = 10.sp,
            color = Color(0xFF333333),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0xB3FFFFFF))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.openstreetmap.org/copyright".toUri())) }
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = LocalRatioJurisIos.current.tint) }
}

/** Zoom that roughly fits all [points] into a ~360dp-wide view. */
fun ratioJurisFitZoom(points: List<RatioJurisLatLon>): Pair<RatioJurisLatLon, Int> {
    if (points.isEmpty()) return RatioJurisLatLon(20.0, 0.0) to 2
    val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }
    val center = RatioJurisLatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2)
    var z = MAX_ZOOM - 4
    while (z > MIN_ZOOM) {
        val a = ratioJurisProject(RatioJurisLatLon(maxLat, minLon), z)
        val b = ratioJurisProject(RatioJurisLatLon(minLat, maxLon), z)
        if (b.x - a.x < 700 && b.y - a.y < 900) break
        z--
    }
    return center to z
}

/** Opens the location in the user's maps app (no network use by Photos itself). */
fun ratioJurisOpenInMaps(context: android.content.Context, p: RatioJurisLatLon) {
    val uri = "geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}".toUri()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val MIN_ZOOM = 2
private const val MAX_ZOOM = 18
