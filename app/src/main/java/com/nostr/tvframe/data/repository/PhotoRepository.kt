package com.nostr.tvframe.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nostr.tvframe.data.local.PhotoDao
import com.nostr.tvframe.data.local.PhotoEntity
import com.nostr.tvframe.data.local.UserPreferencesRepository
import com.nostr.tvframe.data.remote.WikimediaApiService
import com.nostr.tvframe.data.remote.WikimediaClient
import com.nostr.tvframe.data.storage.SmartCacheManager
import com.nostr.tvframe.data.storage.SmbNetworkDataSource
import com.nostr.tvframe.data.storage.UsbStorageDataSource
import com.nostr.tvframe.domain.model.OrientationFilter
import com.nostr.tvframe.domain.model.PhotoItem
import com.nostr.tvframe.domain.model.PhotoSourceType
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter
import com.nostr.tvframe.data.sync.PagedCollector
import com.nostr.tvframe.data.sync.SearchPage
import com.nostr.tvframe.data.sync.SyncMath
import com.nostr.tvframe.data.sync.SyncPhase
import com.nostr.tvframe.data.sync.SyncReport
import com.nostr.tvframe.data.sync.SyncStatusTracker
import com.nostr.tvframe.data.sync.SyncTrigger
import com.nostr.tvframe.util.DisplaySize
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single Source of Truth coordinating Wikimedia Commons API, External USB Mass Storage,
 * Local SMB Network Shares, Offline Fallback, Gallery Deletion, and the Favorites Engine.
 */
class PhotoRepository(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val wikimediaApi: WikimediaApiService,
    private val usbStorageDataSource: UsbStorageDataSource,
    private val smbNetworkDataSource: SmbNetworkDataSource,
    private val smartCacheManager: SmartCacheManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncStatusTracker: SyncStatusTracker
) {

    private val syncMutex = Mutex()

    private companion object {
        /** Results per Commons search request. */
        const val PAGE_SIZE = 50

        /** Upper bound on search pages per category in one sync (8 × 50 = 400 results). */
        const val MAX_PAGES_PER_CATEGORY = 8
    }

    /**
     * Reactive stream of active photos for the in-app gallery and slideshow, matching the user's
     * enabled sources, resolution floor, and aspect ratio / orientation rules.
     * The "Screensaver: Favorites Only" setting does NOT apply here.
     */
    val activePlaylistFlow: Flow<List<PhotoItem>> = playlistFlow(forScreensaver = false)

    /**
     * Playlist for the system screensaver (PhotoDreamService). Same as [activePlaylistFlow], except
     * that "Screensaver: Favorites Only" restricts it to favorites (falling back to the normal
     * playlist when there are no favorites yet, so the screensaver never goes blank).
     */
    val screensaverPlaylistFlow: Flow<List<PhotoItem>> = playlistFlow(forScreensaver = true)

    private fun playlistFlow(forScreensaver: Boolean): Flow<List<PhotoItem>> = combine(
        photoDao.observeAllPhotos(),
        preferencesRepository.userPreferencesFlow
    ) { entities, prefs ->
        if (forScreensaver && prefs.favoritesOnlyInScreensaver) {
            val favorites = entities.filter { it.isFavorite }
            if (favorites.isNotEmpty()) {
                return@combine favorites.map { it.toDomain() }
            }
        }
        val filtered = entities.filter { entity ->
            val sourceMatches = when (entity.sourceType) {
                PhotoSourceType.WIKIMEDIA.name -> prefs.enableWikimedia
                PhotoSourceType.USB.name -> prefs.enableUsbSource
                PhotoSourceType.SMB.name -> prefs.enableSmbSource
                else -> true
            }
            val orientationMatches = when (prefs.orientationFilter) {
                OrientationFilter.WIDESCREEN_16_9 -> {
                    entity.width > 0 && entity.height > 0 &&
                        (entity.width.toFloat() / entity.height.toFloat()) >= 1.50f
                }
                OrientationFilter.LANDSCAPE_ONLY -> entity.width >= entity.height
                OrientationFilter.PORTRAIT_ONLY -> entity.height > entity.width
                OrientationFilter.ALL -> true
            }
            val resolutionMatches = if (entity.sourceType == PhotoSourceType.WIKIMEDIA.name) {
                entity.width >= prefs.resolutionFloor.minWidthPx
            } else {
                true
            }
            sourceMatches && orientationMatches && resolutionMatches
        }

        // Offline / empty fallback: return favorited or any cached items so the screensaver never goes blank
        val finalEntities = filtered.ifEmpty {
            entities.filter { it.isFavorite }.ifEmpty { entities }
        }
        finalEntities.map { it.toDomain() }
    }

    /**
     * Reactive stream of all user-favorited photos (protected from LRU cache eviction).
     */
    val favoritePhotosFlow: Flow<List<PhotoItem>> = photoDao.observeFavoritePhotos()
        .map { list -> list.map { it.toDomain() } }

    /**
     * Refreshes all currently enabled sources (Wikimedia Commons, USB Storage, SMB NAS) and reports
     * progress through [syncStatusTracker]. New Wikimedia photos are downloaded into the image
     * disk cache one by one ("Downloading X of N") and appear in the gallery as each one lands.
     * Runs are serialized: a second caller waits for the running sync to finish.
     */
    suspend fun refreshEnabledSources(trigger: SyncTrigger = SyncTrigger.MANUAL): Result<SyncReport> =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                val queryLabel = describeActiveQuery(prefs)
                syncStatusTracker.start(trigger, queryLabel)
                val result = runCatching { runSync(prefs, queryLabel) }
                result.fold(
                    onSuccess = { report ->
                        syncStatusTracker.finish(trigger, report.newPhotos, report.message, isError = false)
                    },
                    onFailure = { err ->
                        if (err is CancellationException) {
                            syncStatusTracker.update { it.copy(phase = SyncPhase.IDLE, message = "Sync cancelled.") }
                            throw err
                        }
                        val msg = "Sync failed: ${err.localizedMessage ?: err.javaClass.simpleName}. " +
                            "Showing photos already on this TV."
                        syncStatusTracker.finish(trigger, 0, msg, isError = true)
                    }
                )
                result
            }
        }

    private suspend fun runSync(prefs: UserPreferences, queryLabel: String): SyncReport {
        val existingFavoritesIds = photoDao.getAllFavoritesSync().map { it.id }.toSet()
        var newPhotos = 0
        var wikimediaMatched = 0
        var wikimediaShortfall = false
        var wikimediaOffline = false
        val categories = searchableCategories(prefs)
        val customSearchMissing = categories.isEmpty() &&
            WikimediaCategoryFilter.CUSTOM_QUERY in prefs.activeCategories

        // 1. Wikimedia Commons: one search per selected category, mixed together
        if (prefs.enableWikimedia && categories.isNotEmpty()) {
            if (!isNetworkAvailable()) {
                wikimediaOffline = true
            } else {
                val (screenWidthPx, _) = DisplaySize.screenPx(context)
                val thumbWidth = SyncMath.thumbnailWidthFor(prefs.resolutionFloor.minWidthPx, screenWidthPx)
                val alreadyStored = HashSet<String>()
                val target = prefs.photosPerSync
                val collected = PagedCollector.collect(
                    categories = categories,
                    target = target,
                    maxPagesPerCategory = MAX_PAGES_PER_CATEGORY,
                    startOffset = { 0 },
                    fetchPage = { category, offset ->
                        val response = wikimediaApi.searchCommonsImages(
                            searchQuery = buildWikimediaQuery(category, prefs),
                            limit = PAGE_SIZE,
                            offset = offset.takeIf { it > 0 },
                            thumbnailWidth = thumbWidth
                        )
                        val entities = response.query?.pages?.values.orEmpty()
                            .sortedBy { it.index } // keep Commons' relevance order
                            .mapNotNull { page -> WikimediaClient.mapDtoToEntity(page, category.name) }
                            .filter { entity -> matchesResolutionAndOrientation(entity, prefs) }
                        alreadyStored += existingIds(entities.map { it.id })
                        SearchPage(entities, response.continuation?.gsroffset)
                    },
                    // Only photos that aren't in the gallery yet count toward the target.
                    isWanted = { entity -> entity.id !in alreadyStored },
                    // The same photo can show up under two categories; ids are "wikimedia_<pageid>".
                    key = { it.id },
                    onPage = { scanned ->
                        syncStatusTracker.update {
                            it.copy(message = "Looking for new photos… checked $scanned results")
                        }
                    }
                )
                wikimediaMatched = collected.wantedFound + alreadyStored.size
                val fresh = collected.picked.map { entity ->
                    entity.copy(isFavorite = entity.id in existingFavoritesIds)
                }
                wikimediaShortfall = fresh.size < target
                newPhotos += downloadAndStore(fresh)
            }
        }

        // 2. USB drive
        if (prefs.enableUsbSource) {
            syncStatusTracker.update {
                it.copy(phase = SyncPhase.SCANNING_LOCAL, message = "Scanning USB drive…")
            }
            if (usbStorageDataSource.verifyUsbMountOrFallback()) {
                val usbPhotos = usbStorageDataSource.scanUsbPhotos().map { entity ->
                    entity.copy(isFavorite = entity.id in existingFavoritesIds)
                }
                val before = existingIds(usbPhotos.map { it.id })
                upsertPreservingLocalState(usbPhotos)
                newPhotos += usbPhotos.count { it.id !in before }
            }
        }

        // 3. SMB / NAS share
        if (prefs.enableSmbSource && prefs.smbHostIp.isNotBlank() && isNetworkAvailable()) {
            syncStatusTracker.update {
                it.copy(phase = SyncPhase.SCANNING_LOCAL, message = "Copying photos from the network share…")
            }
            val activeCacheDir = smartCacheManager.resolveActiveCacheDirectory()
            val smbPhotos = smbNetworkDataSource.syncSmbSharePhotos(
                hostIp = prefs.smbHostIp,
                shareAndPath = prefs.smbShareAndPath,
                authMode = prefs.smbAuthMode,
                targetCacheDir = activeCacheDir
            ).map { entity ->
                entity.copy(isFavorite = entity.id in existingFavoritesIds)
            }
            val before = existingIds(smbPhotos.map { it.id })
            upsertPreservingLocalState(smbPhotos)
            newPhotos += smbPhotos.count { it.id !in before }
        }

        // 4. Enforce LRU storage quota while protecting Favorites
        smartCacheManager.enforceStorageQuota()

        val message = when {
            !prefs.enableWikimedia && !prefs.enableUsbSource && !prefs.enableSmbSource ->
                "Nothing to sync: every photo source is turned off."
            prefs.enableWikimedia && customSearchMissing && newPhotos == 0 ->
                "Only Custom Search is selected and the search box is empty. Type what you want to see and press Search."
            wikimediaOffline && newPhotos == 0 ->
                "No internet connection. Showing photos already on this TV."
            newPhotos > 0 ->
                "Added $newPhotos new photo${if (newPhotos == 1) "" else "s"}." +
                    if (wikimediaShortfall) " That's every new match found this time." else ""
            prefs.enableWikimedia && wikimediaMatched == 0 ->
                "No photos matched $queryLabel at ${prefs.resolutionFloor.label} or higher, " +
                    "${prefs.orientationFilter.label}. Try another category or a lower resolution."
            else ->
                "No new photos. Everything that matched is already in your gallery. " +
                    "Try another category or a different minimum resolution."
        }
        return SyncReport(newPhotos = newPhotos, queryLabel = queryLabel, message = message)
    }

    /**
     * Downloads each new photo into Coil's disk cache (so it shows instantly and works offline)
     * and inserts it into the gallery right away, updating "Downloading X of N" as it goes.
     * A photo whose download fails is still added; it will load on demand later.
     */
    private suspend fun downloadAndStore(fresh: List<PhotoEntity>): Int {
        if (fresh.isEmpty()) return 0
        val total = fresh.size
        val imageLoader = context.imageLoader
        val (decodeW, decodeH) = DisplaySize.cappedPx(context)
        fresh.forEachIndexed { index, entity ->
            syncStatusTracker.update {
                it.copy(
                    phase = SyncPhase.DOWNLOADING,
                    progressDone = index,
                    progressTotal = total,
                    message = "Downloading ${index + 1} of $total"
                )
            }
            val request = ImageRequest.Builder(context)
                .data(entity.imageUrlOrUri)
                .size(decodeW, decodeH)
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            runCatching { imageLoader.execute(request) }
            upsertPreservingLocalState(listOf(entity))
        }
        syncStatusTracker.update { it.copy(progressDone = total, progressTotal = total) }
        return total
    }

    private suspend fun existingIds(ids: List<String>): Set<String> =
        ids.distinct().chunked(500).flatMap { photoDao.getPhotosByIds(it) }.map { it.id }.toSet()

    private fun matchesResolutionAndOrientation(entity: PhotoEntity, prefs: UserPreferences): Boolean {
        val meetsRes = entity.width >= prefs.resolutionFloor.minWidthPx
        val meetsAspect = when (prefs.orientationFilter) {
            OrientationFilter.WIDESCREEN_16_9 ->
                entity.height > 0 && (entity.width.toFloat() / entity.height.toFloat()) >= 1.50f
            OrientationFilter.LANDSCAPE_ONLY -> entity.width >= entity.height
            OrientationFilter.PORTRAIT_ONLY -> entity.height > entity.width
            OrientationFilter.ALL -> true
        }
        return meetsRes && meetsAspect
    }

    /**
     * Human-readable name of what the next sync will search for, e.g. "Landscapes + Birds" or
     * "Mountains + “Swiss Alps”".
     */
    fun describeActiveQuery(prefs: UserPreferences): String {
        val parts = WikimediaCategoryFilter.entries
            .filter { it in prefs.activeCategories }
            .mapNotNull { category ->
                if (category == WikimediaCategoryFilter.CUSTOM_QUERY) {
                    prefs.customSearchQuery.trim().takeIf { it.isNotEmpty() }?.let { "\u201C$it\u201D" }
                } else {
                    category.displayName
                }
            }
        return parts.joinToString(" + ").ifEmpty { "Custom Search (type what you want to see)" }
    }

    /** Selected categories that can actually be searched (Custom Search needs some text). */
    private fun searchableCategories(prefs: UserPreferences): List<WikimediaCategoryFilter> =
        WikimediaCategoryFilter.entries.filter { category ->
            category in prefs.activeCategories &&
                (category != WikimediaCategoryFilter.CUSTOM_QUERY || prefs.customSearchQuery.isNotBlank())
        }

    /**
     * Toggles the Favorite status of a photo.
     */
    suspend fun toggleFavorite(photoId: String): Boolean = withContext(Dispatchers.IO) {
        val current = photoDao.getPhotoById(photoId) ?: return@withContext false
        val nextFavorite = !current.isFavorite
        photoDao.updateFavoriteState(photoId, nextFavorite)
        nextFavorite
    }

    /**
     * Deletes one or more photos directly from the Gallery & local cache disk.
     */
    suspend fun deletePhotosFromGallery(photoIds: List<String>) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        val entities = photoDao.getPhotosByIds(photoIds)
        for (entity in entities) {
            entity.localCachedFilePath?.let { path ->
                if (path.contains("tvframe_")) {
                    runCatching { File(path).delete() }
                }
            }
        }
        photoDao.deletePhotosByIds(photoIds)
    }

    /**
     * Deletes all non-favorited downloaded photos from the Gallery & local cache so the user
     * can start fresh with a new category or resolution filter.
     */
    suspend fun deleteAllNonFavoritePhotos() = withContext(Dispatchers.IO) {
        val nonFavs = photoDao.getNonFavoritePhotosOrderedByLru()
        for (entity in nonFavs) {
            entity.localCachedFilePath?.let { path ->
                if (path.contains("tvframe_")) {
                    runCatching { File(path).delete() }
                }
            }
        }
        photoDao.deleteAllNonFavoritePhotos()
    }

    /**
     * Removes multiple selected photos from Favorites (or deletes them from the database/disk).
     */
    suspend fun deleteSelectedFavorites(photoIds: List<String>, deleteFromCacheToo: Boolean) =
        withContext(Dispatchers.IO) {
            if (photoIds.isEmpty()) return@withContext
            if (deleteFromCacheToo) {
                deletePhotosFromGallery(photoIds)
            } else {
                photoDao.unmarkFavoritesByIds(photoIds)
            }
        }

    /**
     * Clears or permanently deletes ALL favorited items when requested by the user.
     */
    suspend fun deleteAllFavorites(deleteFromCacheToo: Boolean) = withContext(Dispatchers.IO) {
        if (deleteFromCacheToo) {
            photoDao.deleteAllFavoritesPermanently()
        } else {
            photoDao.unmarkAllFavorites()
        }
    }

    /**
     * Inserts/updates refreshed rows without clobbering local state. A plain REPLACE upsert would
     * reset createdAt/lastAccessed (breaking stable ordering and LRU), drop the cached file path,
     * and could undo a favorite toggled while the refresh was running.
     */
    private suspend fun upsertPreservingLocalState(incoming: List<PhotoEntity>) {
        if (incoming.isEmpty()) return
        val existingById = incoming.map { it.id }
            .distinct()
            .chunked(500) // stay well under SQLite's bound-variable limit
            .flatMap { photoDao.getPhotosByIds(it) }
            .associateBy { it.id }
        val merged = incoming.map { fresh ->
            val old = existingById[fresh.id] ?: return@map fresh
            fresh.copy(
                isFavorite = old.isFavorite,
                createdAtEpochMs = old.createdAtEpochMs,
                lastAccessedEpochMs = old.lastAccessedEpochMs,
                localCachedFilePath = fresh.localCachedFilePath ?: old.localCachedFilePath,
                fileSizeBytes = if (fresh.fileSizeBytes > 0L) fresh.fileSizeBytes else old.fileSizeBytes
            )
        }
        photoDao.upsertPhotos(merged)
    }

    suspend fun markPhotoViewed(photoId: String) = withContext(Dispatchers.IO) {
        photoDao.touchPhotoAccessTime(photoId)
    }

    /**
     * Commons search query for one category, with the minimum resolution applied server-side
     * (`filew:>N`) so results below the floor don't use up the page.
     */
    private fun buildWikimediaQuery(category: WikimediaCategoryFilter, prefs: UserPreferences): String {
        val base = if (category == WikimediaCategoryFilter.CUSTOM_QUERY) {
            "${prefs.customSearchQuery.trim()} filetype:bitmap"
        } else {
            category.commonsQuery
        }
        return "$base ${SyncMath.minWidthKeyword(prefs.resolutionFloor.minWidthPx)}"
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
