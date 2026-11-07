// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ratiojuris.photos.data.RatioJurisThemeMode

/**
 * iOS-inspired system colours (independently defined values; no Apple assets are used).
 * Photographs stay the focus: neutral surfaces, one tint colour, hairline separators.
 */
@Immutable
data class RatioJurisIosColors(
    val background: Color,
    val groupedBackground: Color,
    val cell: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val tint: Color,
    val destructive: Color,
    val positive: Color,
    val bar: Color,
    val chromeOverPhoto: Color,
    val isDark: Boolean,
)

val RatioJurisLightIos = RatioJurisIosColors(
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    elevated = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x33787880),
    tint = Color(0xFF007AFF),
    destructive = Color(0xFFFF3B30),
    positive = Color(0xFF34C759),
    bar = Color(0xF2F9F9F9),
    chromeOverPhoto = Color(0xB3FFFFFF),
    isDark = false,
)

val RatioJurisDarkIos = RatioJurisIosColors(
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    elevated = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x5C787880),
    tint = Color(0xFF0A84FF),
    destructive = Color(0xFFFF453A),
    positive = Color(0xFF30D158),
    bar = Color(0xF01D1D1F),
    chromeOverPhoto = Color(0x99000000),
    isDark = true,
)

val LocalRatioJurisIos = staticCompositionLocalOf { RatioJurisLightIos }

/** Kept for components that mark protected content. */
@Immutable
data class RatioJurisExtras(
    val protectedAccent: Color,
    val onProtectedAccent: Color,
    val scrim: Color,
    val tilePlaceholder: Color,
)

val LocalRatioJurisExtras = staticCompositionLocalOf {
    RatioJurisExtras(Color(0xFF007AFF), Color.White, Color(0x66000000), Color(0xFFE5E5EA))
}

object RatioJurisPalette {
    val Ink = Color(0xFF1C1C1E)
    val Blue = Color(0xFF007AFF)
}

private val Sans = FontFamily.SansSerif

/** iOS text styles (pt -> sp): Large Title 34, Title 28/22/20, Headline 17 semibold, Body 17, Subhead 15, Footnote 13, Caption 12/11. */
private val RatioJurisTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = 0.25.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.1.sp),
)

private val RatioJurisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private fun RatioJurisIosColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = tint, onPrimary = Color.White,
        primaryContainer = tint.copy(alpha = 0.15f), onPrimaryContainer = tint,
        secondary = secondaryLabel, onSecondary = background,
        secondaryContainer = fill, onSecondaryContainer = label,
        tertiary = tint, onTertiary = Color.White,
        background = background, onBackground = label,
        surface = background, onSurface = label,
        surfaceVariant = fill, onSurfaceVariant = secondaryLabel,
        surfaceContainerLowest = cell, surfaceContainerLow = groupedBackground,
        surfaceContainer = cell, surfaceContainerHigh = elevated, surfaceContainerHighest = elevated,
        outline = separator, outlineVariant = separator,
        error = destructive, onError = Color.White,
    )
}

@Composable
fun ratioJurisIsDark(mode: RatioJurisThemeMode): Boolean = when (mode) {
    RatioJurisThemeMode.SYSTEM -> isSystemInDarkTheme()
    RatioJurisThemeMode.LIGHT -> false
    RatioJurisThemeMode.DARK -> true
}

@Composable
fun RatioJurisTheme(mode: RatioJurisThemeMode = RatioJurisThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val ios = if (ratioJurisIsDark(mode)) RatioJurisDarkIos else RatioJurisLightIos
    val extras = RatioJurisExtras(
        protectedAccent = ios.tint,
        onProtectedAccent = Color.White,
        scrim = Color(0x66000000),
        tilePlaceholder = if (ios.isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA),
    )
    CompositionLocalProvider(LocalRatioJurisIos provides ios, LocalRatioJurisExtras provides extras) {
        MaterialTheme(colorScheme = ios.toMaterial(), typography = RatioJurisTypography, shapes = RatioJurisShapes) {
            // iOS-style press highlight instead of Material ripples for every clickable.
            val press = RatioJurisPressIndication(if (ios.isDark) Color(0x33FFFFFF) else Color(0x1F000000))
            CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides press, content = content)
        }
    }
}
