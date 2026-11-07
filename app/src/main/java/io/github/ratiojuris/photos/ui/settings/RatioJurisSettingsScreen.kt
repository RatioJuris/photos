// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratiojuris.photos.BuildConfig
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisGridDensity
import io.github.ratiojuris.photos.data.RatioJurisSettingsRepository
import io.github.ratiojuris.photos.data.RatioJurisSettingsState
import io.github.ratiojuris.photos.data.RatioJurisSortOrder
import io.github.ratiojuris.photos.data.RatioJurisThemeMode
import io.github.ratiojuris.photos.data.RatioJurisThumbnailPolicy
import io.github.ratiojuris.photos.phtx.RatioJurisKdfParams
import io.github.ratiojuris.photos.phtx.RatioJurisPhtxFormat
import io.github.ratiojuris.photos.ratioJurisContainer
import io.github.ratiojuris.photos.ui.components.RatioJurisCheckRow
import io.github.ratiojuris.photos.ui.components.RatioJurisGroupedSection
import io.github.ratiojuris.photos.ui.components.RatioJurisIconTile
import io.github.ratiojuris.photos.ui.components.RatioJurisNavBar
import io.github.ratiojuris.photos.ui.components.RatioJurisRow
import io.github.ratiojuris.photos.ui.components.RatioJurisToggleRow
import io.github.ratiojuris.photos.ui.components.ratioJurisPolicyHint
import io.github.ratiojuris.photos.ui.components.ratioJurisPolicyLabel
import io.github.ratiojuris.photos.ui.gallery.ratioJurisSortLabel
import io.github.ratiojuris.photos.ui.theme.LocalRatioJurisIos
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class RatioJurisSettingsPage { ROOT, THEME, DENSITY, SORT, POLICY, PRIVACY, HOW, LICENSE }

@Composable
fun RatioJurisSettingsScreen(settingsFlow: StateFlow<RatioJurisSettingsState>, onRequestLocationPermission: () -> Unit = {}, onBack: () -> Unit) {
    val s by settingsFlow.collectAsStateWithLifecycle()
    val ios = LocalRatioJurisIos.current
    val context = LocalContext.current
    val repo: RatioJurisSettingsRepository = context.ratioJurisContainer.settings
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(RatioJurisSettingsPage.ROOT) }
    fun set(block: suspend RatioJurisSettingsRepository.() -> Unit) { scope.launch { repo.block() } }
    val settingsTitle = stringResource(R.string.tab_settings)
    BackHandler(enabled = page != RatioJurisSettingsPage.ROOT) { page = RatioJurisSettingsPage.ROOT }

    Column(Modifier.fillMaxSize().background(ios.groupedBackground)) {
        val title = when (page) {
            RatioJurisSettingsPage.ROOT -> settingsTitle
            RatioJurisSettingsPage.THEME -> stringResource(R.string.settings_theme)
            RatioJurisSettingsPage.DENSITY -> stringResource(R.string.settings_grid_density)
            RatioJurisSettingsPage.SORT -> stringResource(R.string.settings_sort)
            RatioJurisSettingsPage.POLICY -> stringResource(R.string.settings_thumbnail_policy_short)
            RatioJurisSettingsPage.PRIVACY -> stringResource(R.string.settings_privacy)
            RatioJurisSettingsPage.HOW -> stringResource(R.string.settings_how_protection_works)
            RatioJurisSettingsPage.LICENSE -> stringResource(R.string.settings_license)
        }
        if (page == RatioJurisSettingsPage.ROOT) {
            RatioJurisNavBar(title, backLabel = stringResource(R.string.back), onBack = onBack)
        } else {
            RatioJurisNavBar(title, backLabel = settingsTitle, onBack = { page = RatioJurisSettingsPage.ROOT })
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            when (page) {
                RatioJurisSettingsPage.ROOT -> {
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.settings_appearance),
                        rows = listOf(
                            { RatioJurisRow(stringResource(R.string.settings_theme), value = stringResource(themeLabel(s.theme)), leading = { RatioJurisIconTile(Icons.Rounded.BrightnessMedium, Color(0xFF5856D6)) }) { page = RatioJurisSettingsPage.THEME } },
                            { RatioJurisRow(stringResource(R.string.settings_grid_density), value = stringResource(densityLabel(s.gridDensity)), leading = { RatioJurisIconTile(Icons.Rounded.GridView, Color(0xFF007AFF)) }) { page = RatioJurisSettingsPage.DENSITY } },
                            { RatioJurisRow(stringResource(R.string.settings_sort), value = stringResource(ratioJurisSortLabel(s.sortOrder)), leading = { RatioJurisIconTile(Icons.Rounded.SwapVert, Color(0xFF34C759)) }) { page = RatioJurisSettingsPage.SORT } },
                            { RatioJurisToggleRow(stringResource(R.string.settings_group_by_date), s.groupByDate, { set { ratioJurisSetGroupByDate(it) } }) },
                        ),
                        footer = stringResource(R.string.settings_group_by_date_hint),
                    )
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.settings_protection),
                        rows = listOf(
                            { RatioJurisRow(stringResource(R.string.settings_thumbnail_policy_short), value = stringResource(ratioJurisPolicyLabel(s.thumbnailPolicy)), leading = { RatioJurisIconTile(Icons.Rounded.Lock, Color(0xFFFF9500)) }) { page = RatioJurisSettingsPage.POLICY } },
                            { RatioJurisToggleRow(stringResource(R.string.settings_show_protected_thumbs), s.showProtectedThumbnails, { set { ratioJurisSetShowProtectedThumbnails(it) } }, stringResource(R.string.settings_show_protected_thumbs_hint)) },
                            { RatioJurisToggleRow(stringResource(R.string.settings_confirm_password), s.confirmPassword, { set { ratioJurisSetConfirmPassword(it) } }, stringResource(R.string.settings_confirm_password_hint)) },
                            { RatioJurisToggleRow(stringResource(R.string.settings_remove_originals), s.removeOriginals, { set { ratioJurisSetRemoveOriginals(it) } }, stringResource(R.string.settings_remove_originals_hint)) },
                        ),
                    )
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.settings_secure_viewing),
                        rows = listOf(
                            { RatioJurisToggleRow(stringResource(R.string.settings_secure_screen), s.secureScreen, { set { ratioJurisSetSecureScreen(it) } }, stringResource(R.string.settings_secure_screen_hint)) },
                            { RatioJurisToggleRow(stringResource(R.string.settings_relock), s.relockOnBackground, { set { ratioJurisSetRelockOnBackground(it) } }, stringResource(R.string.settings_relock_hint)) },
                        ),
                    )
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.library),
                        rows = listOf({ RatioJurisToggleRow(stringResource(R.string.settings_show_archived), s.showArchived, { set { ratioJurisSetShowArchived(it) } }) }),
                        footer = stringResource(R.string.settings_show_archived_hint),
                    )
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.bin),
                        rows = listOf({
                            if (android.os.Build.VERSION.SDK_INT >= 30) RatioJurisToggleRow(stringResource(R.string.settings_use_bin), s.useBin, { set { ratioJurisSetUseBin(it) } })
                            else RatioJurisRow(stringResource(R.string.settings_use_bin), value = stringResource(R.string.unavailable), chevron = false)
                        }),
                        footer = stringResource(if (android.os.Build.VERSION.SDK_INT >= 30) R.string.settings_use_bin_hint else R.string.bin_unsupported),
                    )
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.settings_location_maps),
                        rows = listOf(
                            {
                                RatioJurisToggleRow(stringResource(R.string.settings_show_location), s.showLocation, { on ->
                                    set { ratioJurisSetShowLocation(on) }
                                    if (on && !context.ratioJurisContainer.location.ratioJurisHasPermission()) onRequestLocationPermission()
                                    if (!on) scope.launch { context.ratioJurisContainer.location.ratioJurisClear() }
                                })
                            },
                            { RatioJurisToggleRow(stringResource(R.string.settings_maps), s.mapsEnabled, { set { ratioJurisSetMapsEnabled(it) } }) },
                            { RatioJurisRow(stringResource(R.string.settings_clear_map_cache), tinted = true, chevron = false) { context.ratioJurisContainer.tiles.ratioJurisClear() } },
                        ),
                        footer = stringResource(R.string.settings_maps_hint),
                    )
                    RatioJurisGroupedSection(
                        header = stringResource(R.string.settings_about),
                        rows = listOf(
                            { RatioJurisRow(stringResource(R.string.settings_privacy), leading = { RatioJurisIconTile(Icons.Rounded.PrivacyTip, Color(0xFF007AFF)) }) { page = RatioJurisSettingsPage.PRIVACY } },
                            { RatioJurisRow(stringResource(R.string.settings_how_protection_works), leading = { RatioJurisIconTile(Icons.Rounded.Shield, Color(0xFF8E8E93)) }) { page = RatioJurisSettingsPage.HOW } },
                            {
                                RatioJurisRow(stringResource(R.string.settings_source), value = "GitHub", leading = { RatioJurisIconTile(Icons.Rounded.Code, Color(0xFF1C1C1E)) }) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, RatioJurisPhtxFormat.RATIOJURIS_PROJECT_URL.toUri())) }
                                }
                            },
                            { RatioJurisRow(stringResource(R.string.settings_license), value = "GPL-3.0+", leading = { RatioJurisIconTile(Icons.Rounded.Info, Color(0xFF8E8E93)) }) { page = RatioJurisSettingsPage.LICENSE } },
                            { RatioJurisRow(stringResource(R.string.settings_version), value = "v${BuildConfig.VERSION_NAME}", chevron = false) },
                        ),
                        footer = stringResource(R.string.settings_developer_footer),
                    )
                }
                RatioJurisSettingsPage.THEME -> Choices(RatioJurisThemeMode.entries, s.theme, { stringResource(themeLabel(it)) }, null) { set { ratioJurisSetTheme(it) } }
                RatioJurisSettingsPage.DENSITY -> Choices(RatioJurisGridDensity.entries, s.gridDensity, { stringResource(densityLabel(it)) }, null, stringResource(R.string.settings_density_footer)) { set { ratioJurisSetGridDensity(it) } }
                RatioJurisSettingsPage.SORT -> Choices(RatioJurisSortOrder.entries, s.sortOrder, { stringResource(ratioJurisSortLabel(it)) }, null) { set { ratioJurisSetSortOrder(it) } }
                RatioJurisSettingsPage.POLICY -> Choices(RatioJurisThumbnailPolicy.entries, s.thumbnailPolicy, { stringResource(ratioJurisPolicyLabel(it)) }, { stringResource(ratioJurisPolicyHint(it)) }, stringResource(R.string.settings_policy_footer)) { set { ratioJurisSetThumbnailPolicy(it) } }
                RatioJurisSettingsPage.PRIVACY -> TextPage(stringResource(R.string.privacy_text))
                RatioJurisSettingsPage.HOW -> {
                    val kdf = RatioJurisKdfParams.DEFAULT
                    TextPage(stringResource(R.string.how_text, kdf.memoryKiB / 1024, kdf.iterations))
                }
                RatioJurisSettingsPage.LICENSE -> TextPage(stringResource(R.string.license_text))
            }
        }
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, hint: (@Composable (T) -> String)?, footer: String? = null, onSelect: (T) -> Unit) {
    RatioJurisGroupedSection(
        footer = footer,
        rows = options.map { o -> @Composable { RatioJurisCheckRow(label(o), hint?.invoke(o), o == selected) { onSelect(o) } } },
    )
}

@Composable
private fun TextPage(text: String) {
    val ios = LocalRatioJurisIos.current
    Spacer(Modifier.height(22.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = ios.label,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.cell).padding(16.dp),
    )
}

private fun themeLabel(t: RatioJurisThemeMode) = when (t) {
    RatioJurisThemeMode.SYSTEM -> R.string.theme_system
    RatioJurisThemeMode.LIGHT -> R.string.theme_light
    RatioJurisThemeMode.DARK -> R.string.theme_dark
}

private fun densityLabel(d: RatioJurisGridDensity) = when (d) {
    RatioJurisGridDensity.COMPACT -> R.string.density_compact
    RatioJurisGridDensity.COMFORTABLE -> R.string.density_comfortable
    RatioJurisGridDensity.LARGE -> R.string.density_large
}
