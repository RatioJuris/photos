// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.viewer

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Stable
class RatioJurisZoomState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set
    internal var size by mutableStateOf(IntSize.Zero)

    /** Vertical drag offset at 1x (swipe down to close, swipe up for details). */
    var dragY by mutableFloatStateOf(0f)
        internal set

    internal var animation: Job? = null

    val isZoomed: Boolean get() = scale > 1.01f

    internal fun maxOffset(s: Float = scale) = Offset((size.width * (s - 1f)).coerceAtLeast(0f) / 2f, (size.height * (s - 1f)).coerceAtLeast(0f) / 2f)

    internal fun clamp(o: Offset, s: Float = scale): Offset {
        val m = maxOffset(s)
        return Offset(o.x.coerceIn(-m.x, m.x), o.y.coerceIn(-m.y, m.y))
    }

    /** Pan with iOS-like rubber-band resistance outside the content bounds. */
    internal fun rubberPan(pan: Offset) {
        val m = maxOffset()
        fun axis(cur: Float, d: Float, limit: Float): Float {
            val next = cur + d
            return if (next > limit || next < -limit) cur + d * RUBBER else next
        }
        offset = Offset(axis(offset.x, pan.x, m.x), axis(offset.y, pan.y, m.y))
    }

    internal fun zoomBy(zoom: Float, centroid: Offset, pan: Offset) {
        // Elastic beyond limits while the fingers are down; settles back on release.
        val raw = scale * zoom
        val newScale = when {
            raw > MAX -> MAX + (raw - MAX) * RUBBER
            raw < MIN -> MIN - (MIN - raw) * RUBBER
            else -> raw
        }.coerceIn(0.6f, MAX * 1.5f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val focus = centroid - center
        offset = (offset - focus) * (newScale / scale) + focus + pan
        scale = newScale
    }

    fun reset() {
        animation?.cancel()
        scale = 1f; offset = Offset.Zero; dragY = 0f
    }

    companion object {
        const val MIN = 1f
        const val MAX = 6f
        const val DOUBLE_TAP = 2.5f
        private const val RUBBER = 0.33f
    }
}

/**
 * iOS-like photo gestures:
 *  - pinch to zoom with elastic limits, double-tap to zoom to the tapped point;
 *  - when zoomed, pan with momentum and rubber-band edges; at an edge the swipe passes to the pager;
 *  - at 1x, a vertical swipe follows the finger: down (or a fast flick) closes, up opens details.
 */
@Composable
fun RatioJurisZoomable(
    state: RatioJurisZoomState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val decay = remember(density) { splineBasedDecay<Float>(density) }
    val swipeDown by rememberUpdatedState(onSwipeDown)
    val swipeUp by rememberUpdatedState(onSwipeUp)
    val tap by rememberUpdatedState(onTap)

    fun settle(velocity: Offset = Offset.Zero) {
        state.animation?.cancel()
        state.animation = scope.launch {
            // 1. Scale back into range around the centre of the current view.
            val target = state.scale.coerceIn(RatioJurisZoomState.MIN, RatioJurisZoomState.MAX)
            if (target != state.scale) {
                val fromScale = state.scale
                val fromOffset = state.offset
                val toOffset = state.clamp(fromOffset * (target / fromScale), target)
                animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                    state.scale = fromScale + (target - fromScale) * t
                    state.offset = fromOffset + (toOffset - fromOffset) * t
                }
                if (target <= 1f) { state.offset = Offset.Zero; return@launch }
            }
            // 2. Momentum, then spring back inside the bounds.
            coroutineScope {
                launch {
                    if (abs(velocity.x) > 50f && state.offset.x in -state.maxOffset().x..state.maxOffset().x) {
                        AnimationState(state.offset.x, velocity.x).animateDecay(decay) {
                            val m = state.maxOffset().x
                            state.offset = state.offset.copy(x = value)
                            if (value > m + 80f || value < -m - 80f) cancelAnimation()
                        }
                    }
                }
                launch {
                    if (abs(velocity.y) > 50f && state.offset.y in -state.maxOffset().y..state.maxOffset().y) {
                        AnimationState(state.offset.y, velocity.y).animateDecay(decay) {
                            val m = state.maxOffset().y
                            state.offset = state.offset.copy(y = value)
                            if (value > m + 80f || value < -m - 80f) cancelAnimation()
                        }
                    }
                }
            }
            val from = state.offset
            val to = state.clamp(from)
            if (from != to) {
                animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { t, _ -> state.offset = from + (to - from) * t }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.size = it }
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { point ->
                        state.animation?.cancel()
                        state.animation = scope.launch {
                            val fromScale = state.scale
                            val toScale = if (state.isZoomed) 1f else RatioJurisZoomState.DOUBLE_TAP
                            val fromOffset = state.offset
                            val center = Offset(state.size.width / 2f, state.size.height / 2f)
                            val toOffset = if (toScale == 1f) Offset.Zero else state.clamp((center - point) * (toScale - 1f), toScale)
                            animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                                state.scale = fromScale + (toScale - fromScale) * t
                                state.offset = fromOffset + (toOffset - fromOffset) * t
                            }
                        }
                    },
                )
            }
            .pointerInput(state) {
                val dismissDistance = 110.dp.toPx()
                val flickVelocity = 1400.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.animation?.cancel()
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    // 0 undecided, 1 handed to pager, 2 vertical swipe at 1x, 3 pinch, 4 pan while zoomed
                    var mode = 0
                    var total = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                        if (pressedCount >= 2) {
                            mode = 3
                            state.dragY = 0f
                            state.zoomBy(event.calculateZoom(), event.calculateCentroid(useCurrent = true), event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (mode != 1 && mode != 3) {
                            val pan = event.calculatePan()
                            total += pan
                            if (mode == 0 && total.getDistance() > viewConfiguration.touchSlop) {
                                mode = if (state.isZoomed) {
                                    val m = state.maxOffset()
                                    val horizontal = abs(total.x) > abs(total.y)
                                    val atLeftEdge = state.offset.x >= m.x - 1f && total.x > 0
                                    val atRightEdge = state.offset.x <= -m.x + 1f && total.x < 0
                                    if (horizontal && (atLeftEdge || atRightEdge)) 1 else 4
                                } else if (abs(total.y) > abs(total.x) * 1.2f && (swipeDown != null || swipeUp != null)) {
                                    2
                                } else {
                                    1
                                }
                            }
                            when (mode) {
                                4 -> { state.rubberPan(pan); event.changes.forEach { if (it.positionChanged()) it.consume() } }
                                2 -> { state.dragY += pan.y; event.changes.forEach { if (it.positionChanged()) it.consume() } }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val v = tracker.calculateVelocity()
                    val velocity = Offset(v.x, v.y)
                    when (mode) {
                        3, 4 -> settle(if (mode == 4) velocity else Offset.Zero)
                        2 -> {
                            val dy = state.dragY
                            state.animation = scope.launch {
                                when {
                                    swipeDown != null && (dy > dismissDistance || (velocity.y > flickVelocity && dy > 0)) -> {
                                        // Continue the motion off-screen, then close.
                                        animate(dy, state.size.height.toFloat(), initialVelocity = velocity.y, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> state.dragY = value }
                                        swipeDown?.invoke()
                                    }
                                    swipeUp != null && (dy < -dismissDistance || (velocity.y < -flickVelocity && dy < 0)) -> {
                                        swipeUp?.invoke()
                                        animate(dy, 0f, initialVelocity = velocity.y, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { value, _ -> state.dragY = value }
                                    }
                                    else -> animate(dy, 0f, initialVelocity = velocity.y, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { value, _ -> state.dragY = value }
                                }
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                val shrink = 1f - (state.dragY.coerceAtLeast(0f) / 3000f).coerceAtMost(0.25f)
                scaleX = state.scale * shrink
                scaleY = state.scale * shrink
                translationX = state.offset.x
                translationY = state.offset.y + state.dragY
            },
    ) {
        content()
    }
}
