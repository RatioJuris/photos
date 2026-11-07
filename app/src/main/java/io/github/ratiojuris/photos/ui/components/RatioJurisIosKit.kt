// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos

// =============================================================================== primitives

@Composable
fun RatioJurisHairline(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalRatioJurisIos.current.separator),
    )
}

/** iOS-style text button: tint colour, no container. */
@Composable
fun RatioJurisTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    enabled: Boolean = true,
    color: Color = LocalRatioJurisIos.current.tint,
) {
    val ios = LocalRatioJurisIos.current
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) color else ios.tertiaryLabel,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
        )
    }
}

/** Capsule button. [overPhoto] uses a translucent material suitable over imagery. */
@Composable
fun RatioJurisPill(
    text: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    overPhoto: Boolean = false,
    selected: Boolean = false,
) {
    val ios = LocalRatioJurisIos.current
    val bg = when {
        selected -> ios.tint
        overPhoto -> if (ios.isDark) Color(0x8C2C2C2E) else Color(0x8CE5E5EA)
        else -> ios.fill
    }
    val fg = if (selected) Color.White else ios.label
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (text != null) 14.dp else 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(18.dp))
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) Text(text, color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

// =============================================================================== navigation chrome

/** Standard 44dp navigation bar with optional back chevron, centred title and trailing items. */
@Composable
fun RatioJurisNavBar(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    translucent: Boolean = true,
    showSeparator: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val ios = LocalRatioJurisIos.current
    Column(modifier.background(if (translucent) ios.bar else ios.background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            if (onBack != null) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(start = 6.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = backLabel ?: "Back", tint = ios.tint, modifier = Modifier.size(20.dp))
                    if (backLabel != null) Text(backLabel, color = ios.tint, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = ios.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 220.dp)
                    .semantics { heading() },
            )
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        if (showSeparator) RatioJurisHairline()
    }
}

/** Large title header used at the top of root screens. */
@Composable
fun RatioJurisLargeTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val ios = LocalRatioJurisIos.current
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = ios.label, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ios.secondaryLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Immutable
data class RatioJurisTabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun RatioJurisTabBar(items: List<RatioJurisTabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ios = LocalRatioJurisIos.current
    Column(modifier.fillMaxWidth().background(ios.bar)) {
        RatioJurisHairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            items.forEachIndexed { i, item ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(i) }
                        .semantics(mergeDescendants = true) { },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val color = if (active) ios.tint else Color(0xFF8E8E93)
                    Icon(if (active) item.selectedIcon else item.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                    Text(item.label, color = color, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Capsule segmented control, as floated above the tab bar in the library. */
@Composable
fun RatioJurisSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, overPhoto: Boolean = true) {
    val ios = LocalRatioJurisIos.current
    val bg = if (overPhoto) (if (ios.isDark) Color(0xD92C2C2E) else Color(0xD9E5E5EA)) else ios.fill
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .padding(3.dp)
            .height(IntrinsicSize.Min),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) (if (ios.isDark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium), color = ios.label, maxLines = 1)
            }
        }
    }
}

@Composable
fun RatioJurisSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    val ios = LocalRatioJurisIos.current
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ios.fill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = ios.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = ios.secondaryLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label),
                cursorBrush = SolidColor(ios.tint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel, contentDescription = "Clear", tint = ios.secondaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

// =============================================================================== grouped lists

/** Inset grouped section: optional header, rounded card of rows separated by inset hairlines, optional footer. */
@Composable
fun RatioJurisGroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    val ios = LocalRatioJurisIos.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = ios.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 22.dp).semantics { heading() },
            )
        } else {
            Spacer(Modifier.height(22.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.cell)) {
            rows.forEachIndexed { i, row ->
                row()
                if (i < rows.lastIndex) RatioJurisHairline(startIndent = 16.dp)
            }
        }
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** Coloured rounded icon tile, as used in iOS settings lists. */
@Composable
fun RatioJurisIconTile(icon: ImageVector, background: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun RatioJurisRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    chevron: Boolean = true,
    destructive: Boolean = false,
    tinted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val ios = LocalRatioJurisIos.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = when { destructive -> ios.destructive; tinted -> ios.tint; else -> ios.label },
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        }
        if (chevron && onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = ios.tertiaryLabel, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun RatioJurisIosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val ios = LocalRatioJurisIos.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = ios.positive,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (ios.isDark) Color(0xFF39393D) else Color(0xFFE9E9EB),
            uncheckedBorderColor = Color.Transparent,
        ),
        thumbContent = {},
    )
}

@Composable
fun RatioJurisToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    val ios = LocalRatioJurisIos.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ios.label)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel)
        }
        Spacer(Modifier.width(12.dp))
        RatioJurisIosSwitch(checked, onCheckedChange = null)
    }
}

/** Secure text entry styled as a list cell. Uses the password keyboard (no suggestions or learning). */
@Composable
fun RatioJurisSecureField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    showRevealToggle: Boolean = true,
) {
    val ios = LocalRatioJurisIos.current
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = ios.tertiaryLabel, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label),
                cursorBrush = SolidColor(ios.tint),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onAny = { onIme() }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
            )
        }
        if (showRevealToggle) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { visible = !visible },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = ios.secondaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Transient HUD message (bottom, rounded, auto-dismissing). */
@Composable
fun RatioJurisToast(message: String?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            onShown()
        }
    }
    AnimatedVisibility(message != null, modifier = modifier, enter = fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6333336))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(message.orEmpty(), color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

// =============================================================================== alerts & sheets

enum class RatioJurisButtonStyle { DEFAULT, PREFERRED, CANCEL, DESTRUCTIVE }

@Immutable
data class RatioJurisAlertButton(
    val label: String,
    val style: RatioJurisButtonStyle = RatioJurisButtonStyle.DEFAULT,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Centered 270dp alert with optional content (e.g. a secure text field) and hairline-separated buttons. */
@Composable
fun RatioJurisAlert(
    title: String,
    message: String? = null,
    buttons: List<RatioJurisAlertButton>,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ios = LocalRatioJurisIos.current
    Dialog(onDismissRequest = { if (dismissible) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = false)) {
        Column(
            Modifier
                .width(286.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (ios.isDark) Color(0xFF2C2C2E) else Color(0xFFF7F7F7))
                .imePadding(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ios.label, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = ios.label, textAlign = TextAlign.Center)
                }
                if (content != null) {
                    Spacer(Modifier.height(14.dp))
                    content()
                }
            }
            RatioJurisHairline()
            if (buttons.size == 2) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    AlertButton(buttons[0], Modifier.weight(1f))
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(ios.separator))
                    AlertButton(buttons[1], Modifier.weight(1f))
                }
            } else {
                buttons.forEachIndexed { i, b ->
                    AlertButton(b, Modifier.fillMaxWidth())
                    if (i < buttons.lastIndex) RatioJurisHairline()
                }
            }
        }
    }
}

@Composable
private fun AlertButton(b: RatioJurisAlertButton, modifier: Modifier) {
    val ios = LocalRatioJurisIos.current
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clickable(enabled = b.enabled, role = Role.Button, onClick = b.onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            b.label,
            color = when {
                !b.enabled -> ios.tertiaryLabel
                b.style == RatioJurisButtonStyle.DESTRUCTIVE -> ios.destructive
                else -> ios.tint
            },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (b.style == RatioJurisButtonStyle.PREFERRED || b.style == RatioJurisButtonStyle.CANCEL) FontWeight.SemiBold else FontWeight.Normal,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
/** [small] renders a low-emphasis option (used for discreet actions). */
data class RatioJurisSheetAction(val label: String, val destructive: Boolean = false, val enabled: Boolean = true, val small: Boolean = false, val onClick: () -> Unit)

/** Bottom action sheet: grouped options card plus a separate bold Cancel card. */
@Composable
fun RatioJurisActionSheet(
    actions: List<RatioJurisSheetAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    title: String? = null,
    message: String? = null,
) {
    val ios = LocalRatioJurisIos.current
    val cardColor = if (ios.isDark) Color(0xFF2C2C2E) else Color(0xFFF7F7F7)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visible, enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200))) {
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardColor)) {
                        if (title != null || message != null) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (title != null) Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = ios.secondaryLabel, textAlign = TextAlign.Center)
                                if (message != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(message, style = MaterialTheme.typography.bodySmall, color = ios.secondaryLabel, textAlign = TextAlign.Center)
                                }
                            }
                            RatioJurisHairline()
                        }
                        actions.forEachIndexed { i, a ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = if (a.small) 46.dp else 57.dp)
                                    .clickable(enabled = a.enabled, role = Role.Button, onClick = a.onClick),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    a.label,
                                    color = when { !a.enabled -> ios.tertiaryLabel; a.destructive -> ios.destructive; a.small -> ios.secondaryLabel; else -> ios.tint },
                                    style = TextStyle(fontSize = if (a.small) 15.sp else 20.sp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (i < actions.lastIndex) RatioJurisHairline()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 57.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (ios.isDark) Color(0xFF2C2C2E) else Color.White)
                            .clickable(role = Role.Button, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(cancelLabel, color = ios.tint, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

/** Modal form sheet with "Cancel" / title / confirm navigation row over a grouped background. */
@Composable
fun RatioJurisFormSheet(
    title: String,
    cancelLabel: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ios = LocalRatioJurisIos.current
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(ios.groupedBackground)
                    .imePadding(),
            ) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
                    RatioJurisTextButton(cancelLabel, onCancel, Modifier.align(Alignment.CenterStart))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = ios.label, modifier = Modifier.align(Alignment.Center).semantics { heading() })
                    RatioJurisTextButton(confirmLabel, onConfirm, Modifier.align(Alignment.CenterEnd), bold = true, enabled = confirmEnabled)
                }
                RatioJurisHairline()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    content = content,
                )
            }
        }
    }
}

/** Translucent backdrop behind the status bar so scrolled content never collides with it (iOS bar material). */
@Composable
fun RatioJurisStatusBarBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(LocalRatioJurisIos.current.bar)
            .statusBarsPadding(),
    )
}