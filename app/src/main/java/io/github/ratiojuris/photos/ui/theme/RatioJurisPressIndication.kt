// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * iOS-style touch feedback: a quick, subtle highlight on press that fades out on release,
 * instead of Material's expanding ripple.
 */
data class RatioJurisPressIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, color)

    private class Node(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
        private val alpha = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> launch { alpha.animateTo(1f, tween(40)) { invalidateDraw() } }
                        is PressInteraction.Release, is PressInteraction.Cancel ->
                            launch { alpha.animateTo(0f, tween(260)) { invalidateDraw() } }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val a = alpha.value
            if (a > 0f) drawRect(color.copy(alpha = color.alpha * a))
        }
    }
}
