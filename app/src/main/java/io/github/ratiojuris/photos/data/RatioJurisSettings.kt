// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class RatioJurisThemeMode { SYSTEM, LIGHT, DARK }

/** What goes into the public thumbnail slot of a new protected photo. */
enum class RatioJurisThumbnailPolicy(val wireName: String) {
    STANDARD("standard"),
    REDUCED("reduced"),
    NEUTRAL("neutral"),
    NONE("none");

    companion object {
        fun ratioJurisFromWire(value: String?) = entries.firstOrNull { it.wireName == value }
    }
}

enum class RatioJurisSortOrder { DATE_NEWEST, DATE_OLDEST, NAME, SIZE }

/** Columns on a phone-width screen (iOS-style 3 / 5 / 7); wider screens scale proportionally. */
enum class RatioJurisGridDensity(val columns: Int) { LARGE(3), COMFORTABLE(5), COMPACT(7) }

data class RatioJurisSettingsState(
    val theme: RatioJurisThemeMode = RatioJurisThemeMode.SYSTEM,
    val thumbnailPolicy: RatioJurisThumbnailPolicy = RatioJurisThumbnailPolicy.STANDARD,
    val showProtectedThumbnails: Boolean = true,
    val confirmPassword: Boolean = true,
    val secureScreen: Boolean = true,
    val relockOnBackground: Boolean = true,
    val removeOriginals: Boolean = true,
    /** iOS-style: oldest at the top, the library opens scrolled to the newest photo at the bottom. */
    val sortOrder: RatioJurisSortOrder = RatioJurisSortOrder.DATE_OLDEST,
    val gridDensity: RatioJurisGridDensity = RatioJurisGridDensity.COMFORTABLE,
    val groupByDate: Boolean = false,
    /** Archived items are hidden from Library, albums and search unless this is on. */
    val showArchived: Boolean = false,
    /** Android 11+: deleted items go to the system Bin (kept ~30 days) instead of being erased at once. */
    val useBin: Boolean = true,
    /** Read GPS from photos (needs the "media location" permission). Local only. */
    val showLocation: Boolean = true,
    /** Download map images from OpenStreetMap. Off until the user agrees: the tile server learns the area viewed. */
    val mapsEnabled: Boolean = false,
)

private val Context.ratioJurisDataStore by preferencesDataStore(name = "photos_settings")

class RatioJurisSettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val THUMB_POLICY = stringPreferencesKey("thumbnail_policy")
        val SHOW_PROTECTED_THUMBS = booleanPreferencesKey("show_protected_thumbnails")
        val CONFIRM_PASSWORD = booleanPreferencesKey("confirm_password")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val RELOCK = booleanPreferencesKey("relock_on_background")
        val REMOVE_ORIGINALS = booleanPreferencesKey("remove_originals")
        val SORT = stringPreferencesKey("sort_order")
        val DENSITY = stringPreferencesKey("grid_density")
        val GROUP = booleanPreferencesKey("group_by_date")
        val SHOW_ARCHIVED = booleanPreferencesKey("show_archived")
        val USE_BIN = booleanPreferencesKey("use_bin")
        val SHOW_LOCATION = booleanPreferencesKey("show_location")
        val MAPS = booleanPreferencesKey("maps_enabled")
        val ARCHIVED = stringSetPreferencesKey("archived_keys")
    }

    val settings: Flow<RatioJurisSettingsState> = context.ratioJurisDataStore.data.map { p -> p.ratioJurisToState() }

    private fun Preferences.ratioJurisToState(): RatioJurisSettingsState {
        val d = RatioJurisSettingsState()
        return RatioJurisSettingsState(
            theme = enumOr(this[Keys.THEME], d.theme),
            thumbnailPolicy = enumOr(this[Keys.THUMB_POLICY], d.thumbnailPolicy),
            showProtectedThumbnails = this[Keys.SHOW_PROTECTED_THUMBS] ?: d.showProtectedThumbnails,
            confirmPassword = this[Keys.CONFIRM_PASSWORD] ?: d.confirmPassword,
            secureScreen = this[Keys.SECURE_SCREEN] ?: d.secureScreen,
            relockOnBackground = this[Keys.RELOCK] ?: d.relockOnBackground,
            removeOriginals = this[Keys.REMOVE_ORIGINALS] ?: d.removeOriginals,
            sortOrder = enumOr(this[Keys.SORT], d.sortOrder),
            gridDensity = enumOr(this[Keys.DENSITY], d.gridDensity),
            groupByDate = this[Keys.GROUP] ?: d.groupByDate,
            showArchived = this[Keys.SHOW_ARCHIVED] ?: d.showArchived,
            useBin = this[Keys.USE_BIN] ?: d.useBin,
            showLocation = this[Keys.SHOW_LOCATION] ?: d.showLocation,
            mapsEnabled = this[Keys.MAPS] ?: d.mapsEnabled,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    suspend fun ratioJurisSetTheme(v: RatioJurisThemeMode) = put(Keys.THEME, v.name)
    suspend fun ratioJurisSetThumbnailPolicy(v: RatioJurisThumbnailPolicy) = put(Keys.THUMB_POLICY, v.name)
    suspend fun ratioJurisSetShowProtectedThumbnails(v: Boolean) = put(Keys.SHOW_PROTECTED_THUMBS, v)
    suspend fun ratioJurisSetConfirmPassword(v: Boolean) = put(Keys.CONFIRM_PASSWORD, v)
    suspend fun ratioJurisSetSecureScreen(v: Boolean) = put(Keys.SECURE_SCREEN, v)
    suspend fun ratioJurisSetRelockOnBackground(v: Boolean) = put(Keys.RELOCK, v)
    suspend fun ratioJurisSetRemoveOriginals(v: Boolean) = put(Keys.REMOVE_ORIGINALS, v)
    suspend fun ratioJurisSetSortOrder(v: RatioJurisSortOrder) = put(Keys.SORT, v.name)
    suspend fun ratioJurisSetGridDensity(v: RatioJurisGridDensity) = put(Keys.DENSITY, v.name)
    suspend fun ratioJurisSetGroupByDate(v: Boolean) = put(Keys.GROUP, v)
    suspend fun ratioJurisSetShowArchived(v: Boolean) = put(Keys.SHOW_ARCHIVED, v)
    suspend fun ratioJurisSetUseBin(v: Boolean) = put(Keys.USE_BIN, v)
    suspend fun ratioJurisSetShowLocation(v: Boolean) = put(Keys.SHOW_LOCATION, v)
    suspend fun ratioJurisSetMapsEnabled(v: Boolean) = put(Keys.MAPS, v)

    /** Keys ("m:<id>", "p:<id>") of archived items. Stored locally; files are never moved. */
    val archived: Flow<Set<String>> = context.ratioJurisDataStore.data.map { it[Keys.ARCHIVED] ?: emptySet() }

    suspend fun ratioJurisSetArchived(keys: Collection<String>, archived: Boolean) {
        context.ratioJurisDataStore.edit { p ->
            val cur = p[Keys.ARCHIVED] ?: emptySet()
            p[Keys.ARCHIVED] = if (archived) cur + keys else cur - keys.toSet()
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.ratioJurisDataStore.edit { it[key] = value }
    }
}
