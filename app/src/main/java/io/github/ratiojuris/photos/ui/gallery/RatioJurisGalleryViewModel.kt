// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.ui.gallery

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratiojuris.photos.R
import io.github.ratiojuris.photos.data.RatioJurisAlbum
import io.github.ratiojuris.photos.data.RatioJurisGalleryItem
import io.github.ratiojuris.photos.data.RatioJurisGridDensity
import io.github.ratiojuris.photos.data.RatioJurisMediaAccess
import io.github.ratiojuris.photos.data.RatioJurisPhoto
import io.github.ratiojuris.photos.data.RatioJurisProtectedPhoto
import io.github.ratiojuris.photos.data.RatioJurisSettingsState
import io.github.ratiojuris.photos.data.RatioJurisSortOrder
import io.github.ratiojuris.photos.ratioJurisContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** PHOTOS = unprotected still photos, VIDEOS = videos, PROTECTED = protected photos. */
enum class RatioJurisFilter { ALL, PHOTOS, VIDEOS, PROTECTED }

@Immutable
data class RatioJurisLibrary(
    val loading: Boolean = true,
    val access: RatioJurisMediaAccess = RatioJurisMediaAccess.DENIED,
    val photos: List<RatioJurisPhoto> = emptyList(),
    val protected: List<RatioJurisProtectedPhoto> = emptyList(),
    val albums: List<RatioJurisAlbum> = emptyList(),
    val archived: Set<String> = emptySet(),
) {
    fun ratioJurisVisible(item: RatioJurisGalleryItem, showArchived: Boolean) = showArchived || item.key !in archived
}

/** A row of a grid: a full-width date header or a tile. */
@Immutable
sealed interface RatioJurisGridEntry {
    val key: String
    data class Header(override val key: String, val label: String, val itemKeys: List<String>) : RatioJurisGridEntry
    data class Tile(val item: RatioJurisGalleryItem) : RatioJurisGridEntry {
        override val key: String get() = item.key
    }
}

/** A month or year card in the Library's Years / Months views. */
@Immutable
data class RatioJurisPeriod(
    val key: String,
    val title: String,
    val subtitle: String,
    val count: Int,
    val cover: RatioJurisGalleryItem,
    /** Key of the first item of this period in the All Photos order; used to jump there. */
    val anchorKey: String,
)

/** A virtual collection derived from real MediaStore data (iOS "Media Types"). */
@Immutable
data class RatioJurisCollection(val id: String, val titleRes: Int, val items: List<RatioJurisPhoto>)

@OptIn(ExperimentalCoroutinesApi::class)
class RatioJurisGalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.ratioJurisContainer
    private val refresh = MutableStateFlow(0)

    val settings: StateFlow<RatioJurisSettingsState> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, RatioJurisSettingsState())

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(RatioJurisFilter.ALL)
    val selection = MutableStateFlow<Set<String>>(emptySet())

    private val rawLibrary = refresh.flatMapLatest {
        container.media.ratioJurisChanges(container.media.imagesCollection, container.media.videosCollection, container.vault.downloadsCollection)
            .mapLatest {
                val access = container.media.ratioJurisCurrentAccess()
                val protected = container.vault.ratioJurisLoadProtected()
                // Carrier images are real JPEGs in MediaStore; show them once, as protected photos.
                val carrierIds = protected.asSequence().filter { it.imageBacked }.map { it.id }.toHashSet()
                val photos = container.media.ratioJurisLoadPhotos().filter { it.id !in carrierIds }
                RatioJurisLibrary(false, access, photos, protected)
            }
    }

    val library: StateFlow<RatioJurisLibrary> = combine(rawLibrary, container.settings.archived) { lib, archived ->
        // Albums never show archived items (like iOS Hidden).
        lib.copy(archived = archived, albums = container.media.ratioJurisAlbums(lib.photos.filter { it.key !in archived }))
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatioJurisLibrary())

    /** System Bin contents (Android 11+). */
    val bin: StateFlow<List<io.github.ratiojuris.photos.data.RatioJurisBinItem>> = refresh.flatMapLatest {
        container.media.ratioJurisChanges(container.media.imagesCollection, container.media.videosCollection)
            .mapLatest { container.media.ratioJurisLoadBin() }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** media id -> location, filled in the background by the on-device Places index. */
    val places = MutableStateFlow<Map<Long, io.github.ratiojuris.photos.data.RatioJurisLatLon>>(emptyMap())

    /** Keys chosen from a Places cluster; shown as the "rj:place" collection. */
    val placeSelection = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            combine(library, settings) { lib, s -> if (s.showLocation && !lib.loading) lib.photos else null }
                .collectLatest { photos ->
                    if (photos == null) {
                        places.value = emptyMap()
                    } else {
                        places.value = container.location.ratioJurisIndex(photos) { partial -> places.value = partial }
                    }
                }
        }
    }

    fun ratioJurisSetArchived(keys: Collection<String>, archived: Boolean) = viewModelScope.launch {
        container.settings.ratioJurisSetArchived(keys, archived)
    }

    /** Library "All Photos": filter + sort (+ optional day headings). */
    val timeline: StateFlow<List<RatioJurisGridEntry>> =
        combine(library, settings, filter) { lib, s, f ->
            ratioJurisBuildGrid(ratioJurisSort(ratioJurisFiltered(lib, f).filter { lib.ratioJurisVisible(it, s.showArchived) }, s.sortOrder), s)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Search tab results, newest first, never filtered by the Library filter. */
    val searchResults: StateFlow<List<RatioJurisGridEntry>> =
        combine(library, query) { lib, q ->
            if (q.isBlank()) emptyList()
            else ratioJurisSearch((lib.photos + lib.protected).filter { it.key !in lib.archived }, q).sortedByDescending { it.dateMillis }.map { RatioJurisGridEntry.Tile(it) }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val months: StateFlow<List<RatioJurisPeriod>> = combine(library, filter) { lib, f -> ratioJurisPeriods(ratioJurisFiltered(lib, f).filter { it.key !in lib.archived }, years = false) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val years: StateFlow<List<RatioJurisPeriod>> = combine(library, filter) { lib, f -> ratioJurisPeriods(ratioJurisFiltered(lib, f).filter { it.key !in lib.archived }, years = true) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<RatioJurisCollection>> = library.map { full ->
        val lib = full.copy(photos = full.photos.filter { it.key !in full.archived })
        listOf(
            RatioJurisCollection(COLLECTION_VIDEOS, R.string.collection_videos, lib.photos.filter { it.isVideo }),
            RatioJurisCollection(COLLECTION_SCREENSHOTS, R.string.collection_screenshots, lib.photos.filter { (it.relativePath ?: "").contains("Screenshot", ignoreCase = true) }),
            RatioJurisCollection(COLLECTION_ANIMATED, R.string.collection_animated, lib.photos.filter { it.mimeType == "image/gif" }),
            RatioJurisCollection(COLLECTION_RAW, R.string.collection_raw, lib.photos.filter { it.mimeType in RAW_TYPES || it.displayName.endsWith(".dng", true) }),
        ).filter { it.items.isNotEmpty() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ratioJurisRefresh() = refresh.update { it + 1 }

    // ------------------------------------------------------------ selection

    /** iOS-style explicit selection mode ("Select" / "Cancel"). */
    val selecting = MutableStateFlow(false)

    fun ratioJurisStartSelecting(initialKey: String? = null) {
        selecting.value = true
        if (initialKey != null) selection.update { it + initialKey }
    }

    fun ratioJurisToggle(key: String) {
        selecting.value = true
        selection.update { if (key in it) it - key else it + key }
    }
    fun ratioJurisSelectAll(keys: Collection<String>) = selection.update { it + keys }
    fun ratioJurisToggleGroup(keys: List<String>) = selection.update { cur ->
        if (keys.all { it in cur }) cur - keys.toSet() else cur + keys
    }
    fun ratioJurisClearSelection() {
        selection.value = emptySet()
        selecting.value = false
    }

    fun ratioJurisSelectedItems(): List<RatioJurisGalleryItem> {
        val keys = selection.value
        val lib = library.value
        return (lib.photos + lib.protected).filter { it.key in keys }
    }

    fun ratioJurisItem(key: String): RatioJurisGalleryItem? {
        val lib = library.value
        return lib.photos.firstOrNull { it.key == key } ?: lib.protected.firstOrNull { it.key == key }
    }

    // ------------------------------------------------------------ collections, albums, viewer sources

    /** Grid for an album id: a MediaStore bucket id, a media-type collection id, or the protected collection. */
    fun ratioJurisCollectionGrid(id: String): List<RatioJurisGridEntry> {
        val s = settings.value
        val lib = library.value
        val items: List<RatioJurisGalleryItem> = when (id) {
            COLLECTION_ARCHIVE -> (lib.photos + lib.protected).filter { it.key in lib.archived }
            COLLECTION_PLACE -> placeSelection.value.let { keys -> (lib.photos + lib.protected).filter { it.key in keys } }
            COLLECTION_PROTECTED -> lib.protected.filter { it.key !in lib.archived }
            COLLECTION_VIDEOS, COLLECTION_SCREENSHOTS, COLLECTION_ANIMATED, COLLECTION_RAW -> collections.value.firstOrNull { it.id == id }?.items ?: emptyList()
            else -> lib.photos.filter { it.bucketId == id && it.key !in lib.archived }
        }
        return ratioJurisBuildGrid(ratioJurisSort(items, s.sortOrder), s.copy(groupByDate = s.groupByDate && id != COLLECTION_PROTECTED))
    }

    fun ratioJurisCollectionTitle(id: String): String {
        val ctx = getApplication<Application>()
        return when (id) {
            COLLECTION_PROTECTED -> ctx.getString(R.string.tab_protected)
            COLLECTION_ARCHIVE -> ctx.getString(R.string.archive)
            COLLECTION_PLACE -> ctx.getString(R.string.places)
            COLLECTION_VIDEOS -> ctx.getString(R.string.collection_videos)
            COLLECTION_SCREENSHOTS -> ctx.getString(R.string.collection_screenshots)
            COLLECTION_ANIMATED -> ctx.getString(R.string.collection_animated)
            COLLECTION_RAW -> ctx.getString(R.string.collection_raw)
            else -> library.value.albums.firstOrNull { it.bucketId == id }?.name ?: ""
        }
    }

    /** Ordered items for the viewer pager. [source] is "timeline", "search" or "album:<id>". */
    fun ratioJurisViewerItems(source: String): List<RatioJurisGalleryItem> {
        val grid = when {
            source == SOURCE_SEARCH -> searchResults.value
            source.startsWith(SOURCE_ALBUM_PREFIX) -> ratioJurisCollectionGrid(source.removePrefix(SOURCE_ALBUM_PREFIX))
            else -> timeline.value.ifEmpty {
                val lib = library.value
                ratioJurisBuildGrid(ratioJurisSort(lib.photos + lib.protected, settings.value.sortOrder), settings.value)
            }
        }
        return grid.filterIsInstance<RatioJurisGridEntry.Tile>().map { it.item }
    }

    fun ratioJurisSetSort(order: RatioJurisSortOrder) = viewModelScope.launch { container.settings.ratioJurisSetSortOrder(order) }
    fun ratioJurisSetDensity(d: RatioJurisGridDensity) = viewModelScope.launch { container.settings.ratioJurisSetGridDensity(d) }

    // ------------------------------------------------------------ pure helpers

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private fun ratioJurisFiltered(lib: RatioJurisLibrary, f: RatioJurisFilter): List<RatioJurisGalleryItem> = when (f) {
        RatioJurisFilter.ALL -> lib.photos + lib.protected
        RatioJurisFilter.PHOTOS -> lib.photos.filter { !it.isVideo }
        RatioJurisFilter.VIDEOS -> lib.photos.filter { it.isVideo }
        RatioJurisFilter.PROTECTED -> lib.protected
    }

    private fun ratioJurisSearch(items: List<RatioJurisGalleryItem>, query: String): List<RatioJurisGalleryItem> {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) return items
        val protectedWord = getApplication<Application>().getString(R.string.tab_protected).lowercase()
        return items.filter { item ->
            item.displayName.lowercase().contains(q) ||
                (item.albumName?.lowercase()?.contains(q) == true) ||
                monthFormat.format(Date(item.dateMillis)).lowercase().contains(q) ||
                (item is RatioJurisProtectedPhoto && protectedWord.startsWith(q))
        }
    }

    private fun ratioJurisSort(items: List<RatioJurisGalleryItem>, order: RatioJurisSortOrder) = when (order) {
        RatioJurisSortOrder.DATE_NEWEST -> items.sortedByDescending { it.dateMillis }
        RatioJurisSortOrder.DATE_OLDEST -> items.sortedBy { it.dateMillis }
        RatioJurisSortOrder.NAME -> items.sortedBy { it.displayName.lowercase() }
        RatioJurisSortOrder.SIZE -> items.sortedByDescending { it.sizeBytes }
    }

    private fun ratioJurisPeriods(items: List<RatioJurisGalleryItem>, years: Boolean): List<RatioJurisPeriod> {
        if (items.isEmpty()) return emptyList()
        val ctx = getApplication<Application>()
        val cal = Calendar.getInstance()
        val sorted = items.sortedBy { it.dateMillis }
        val groups = LinkedHashMap<Int, MutableList<RatioJurisGalleryItem>>()
        for (item in sorted) {
            cal.timeInMillis = item.dateMillis
            val k = if (years) cal.get(Calendar.YEAR) else cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
            groups.getOrPut(k) { ArrayList() } += item
        }
        val monthTitle = SimpleDateFormat("LLLL", Locale.getDefault())
        return groups.map { (k, list) ->
            // Prefer an ordinary photo as the cover so protected previews are not surfaced as key art.
            val cover = list.lastOrNull { it is RatioJurisPhoto && !it.isVideo } ?: list.lastOrNull { it is RatioJurisPhoto } ?: list.last()
            val first = list.first()
            val countText = ctx.resources.getQuantityString(R.plurals.item_count, list.size, list.size)
            if (years) {
                RatioJurisPeriod("y:$k", k.toString(), countText, list.size, cover, first.key)
            } else {
                cal.timeInMillis = first.dateMillis
                RatioJurisPeriod("m:$k", monthTitle.format(Date(first.dateMillis)).replaceFirstChar { it.titlecase(Locale.getDefault()) }, "${cal.get(Calendar.YEAR)} · $countText", list.size, cover, first.key)
            }
        }
    }

    private fun ratioJurisBuildGrid(items: List<RatioJurisGalleryItem>, s: RatioJurisSettingsState): List<RatioJurisGridEntry> {
        val byDate = s.sortOrder == RatioJurisSortOrder.DATE_NEWEST || s.sortOrder == RatioJurisSortOrder.DATE_OLDEST
        if (!s.groupByDate || !byDate) return items.map { RatioJurisGridEntry.Tile(it) }
        val out = ArrayList<RatioJurisGridEntry>(items.size + 64)
        val cal = Calendar.getInstance()
        var currentDay = Long.MIN_VALUE
        var groupKeys = ArrayList<String>()
        var headerIndex = -1
        fun closeGroup() {
            if (headerIndex >= 0) {
                val h = out[headerIndex] as RatioJurisGridEntry.Header
                out[headerIndex] = h.copy(itemKeys = groupKeys)
            }
        }
        for (item in items) {
            cal.timeInMillis = item.dateMillis
            val day = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
            if (day != currentDay) {
                closeGroup()
                currentDay = day
                groupKeys = ArrayList()
                headerIndex = out.size
                out += RatioJurisGridEntry.Header("h:$day", ratioJurisDayLabel(item.dateMillis), emptyList())
            }
            groupKeys += item.key
            out += RatioJurisGridEntry.Tile(item)
        }
        closeGroup()
        return out
    }

    fun ratioJurisDayLabel(millis: Long): String {
        val ctx = getApplication<Application>()
        return when {
            DateUtils.isToday(millis) -> ctx.getString(R.string.today)
            DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> ctx.getString(R.string.yesterday)
            else -> {
                val now = Calendar.getInstance()
                val then = Calendar.getInstance().apply { timeInMillis = millis }
                val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or
                    DateUtils.FORMAT_ABBREV_MONTH or
                    (if (now.get(Calendar.YEAR) != then.get(Calendar.YEAR)) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR)
                DateUtils.formatDateTime(ctx, millis, flags)
            }
        }
    }

    companion object {
        const val SOURCE_TIMELINE = "timeline"
        const val SOURCE_SEARCH = "search"
        const val SOURCE_ALBUM_PREFIX = "album:"
        const val COLLECTION_PROTECTED = "rj:protected"
        const val COLLECTION_ARCHIVE = "rj:archive"
        const val COLLECTION_PLACE = "rj:place"
        const val COLLECTION_VIDEOS = "rj:videos"
        const val COLLECTION_SCREENSHOTS = "rj:screenshots"
        const val COLLECTION_ANIMATED = "rj:animated"
        const val COLLECTION_RAW = "rj:raw"
        private val RAW_TYPES = setOf("image/x-adobe-dng", "image/dng", "image/x-raw")
    }
}
