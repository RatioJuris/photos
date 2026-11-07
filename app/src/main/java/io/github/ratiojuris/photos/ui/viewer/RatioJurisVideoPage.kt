// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.viewer

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.ui.components.ratioJurisDuration
import kotlinx.coroutines.delay

/**
 * Video playback with the platform VideoView (no extra dependencies). Videos are viewed and shared
 * like photos but are never protected. Swipe down closes, swipe up shows details.
 */
@Composable
fun RatioJurisVideoPage(
    uri: Uri,
    active: Boolean,
    chrome: Boolean,
    onTap: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
) {
    val context = LocalContext.current
    var playing by remember(uri) { mutableStateOf(false) }
    var duration by remember(uri) { mutableIntStateOf(0) }
    var position by remember(uri) { mutableIntStateOf(0) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val down by rememberUpdatedState(onSwipeDown)
    val up by rememberUpdatedState(onSwipeUp)
    val view = remember(uri) {
        VideoView(context).apply {
            setVideoURI(uri)
            setOnPreparedListener { mp ->
                duration = mp.duration
                seekTo(1) // show the first frame
            }
            setOnCompletionListener { playing = false; position = duration }
        }
    }
    DisposableEffect(view) { onDispose { view.stopPlayback() } }
    LaunchedEffect(active) { if (!active && playing) { view.pause(); playing = false } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (playing) { view.pause(); playing = false } }
    LaunchedEffect(playing) {
        while (playing) {
            position = view.currentPosition
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().align(Alignment.Center))
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                .pointerInput(Unit) {
                    val threshold = 110.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > threshold) down() else if (dragY < -threshold) up()
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragY += amount
                    }
                },
        )
        if (!playing) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x8C000000))
                    .clickable(role = Role.Button) { view.start(); playing = true }
                    .semantics { contentDescription = context.getString(R.string.play) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
        if (chrome && duration > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 118.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) {
                        if (playing) { view.pause(); playing = false } else { view.start(); playing = true }
                    }.semantics { contentDescription = context.getString(if (playing) R.string.pause else R.string.play) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
                }
                Text(ratioJurisDuration(position.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                Slider(
                    value = if (duration > 0) position / duration.toFloat() else 0f,
                    onValueChange = { f -> position = (f * duration).toInt(); view.seekTo(position) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x66FFFFFF)),
                    modifier = Modifier.weight(1f),
                )
                Text(ratioJurisDuration(duration.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
