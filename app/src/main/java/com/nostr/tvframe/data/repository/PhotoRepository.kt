package com.nostr.tvframe.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nostr.tvframe.data.local.PhotoDao
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
import kotlinx.coroutines.Dispatchers
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
    private val preferencesRepository: UserPreferencesRepository
) {

    /**
     * Reactive stream of active photos matching the user's enabled sources, resolution floor,
     * and aspect ratio / orientation rules.
     */
    val activePlaylistFlow: Flow<List<PhotoItem>> = combine(
        photoDao.observeAllPhotos(),
        preferencesRepository.userPreferencesFlow
    ) { entities, prefs ->
        val filtered = entities.filter { entity ->
            if (prefs.favoritesOnlyInScreensaver) {
                return@filter entity.isFavorite
            }
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
     * Refreshes all currently enabled sources (Wikimedia Commons, USB Storage, SMB NAS).
     */
    suspend fun refreshEnabledSources(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val existingFavoritesIds = photoDao.getAllFavoritesSync().map { it.id }.toSet()
            var totalUpserted = 0

            // 1. Fetch Wikimedia Commons if enabled and network is available
            if (prefs.enableWikimedia && isNetworkAvailable()) {
                val query = buildEffectiveWikimediaQuery(prefs)
                val response = wikimediaApi.searchCommonsImages(
                    searchQuery = query,
                    limit = 50,
                    thumbnailWidth = prefs.resolutionFloor.minWidthPx.coerceAtLeast(1920)
                )
                val pages = response.query?.pages?.values.orEmpty()
                val mapped = pages.mapNotNull { page ->
                    WikimediaClient.mapDtoToEntity(page, prefs.activeCategory.name)
                }.filter { entity ->
                    val meetsRes = entity.width >= prefs.resolutionFloor.minWidthPx
                    val meetsAspect = when (prefs.orientationFilter) {
                        OrientationFilter.WIDESCREEN_16_9 ->
                            entity.height > 0 && (entity.width.toFloat() / entity.height.toFloat()) >= 1.50f
                        OrientationFilter.LANDSCAPE_ONLY -> entity.width >= entity.height
                        OrientationFilter.PORTRAIT_ONLY -> entity.height > entity.width
                        OrientationFilter.ALL -> true
                    }
                    meetsRes && meetsAspect
                }.map { entity ->
                    entity.copy(isFavorite = entity.id in existingFavoritesIds)
                }

                if (mapped.isNotEmpty()) {
                    photoDao.upsertPhotos(mapped)
                    totalUpserted += mapped.size
                }
            }

            // 2. Scan External USB Drive if enabled and mounted
            if (prefs.enableUsbSource && usbStorageDataSource.verifyUsbMountOrFallback()) {
                val usbPhotos = usbStorageDataSource.scanUsbPhotos().map { entity ->
                    entity.copy(isFavorite = entity.id in existingFavoritesIds)
                }
                if (usbPhotos.isNotEmpty()) {
                    photoDao.upsertPhotos(usbPhotos)
                    totalUpserted += usbPhotos.size
                }
            }

            // 3. Sync SMB / NAS Network Share if enabled
            if (prefs.enableSmbSource && prefs.smbHostIp.isNotBlank() && isNetworkAvailable()) {
                val activeCacheDir = smartCacheManager.resolveActiveCacheDirectory()
                val smbPhotos = smbNetworkDataSource.syncSmbSharePhotos(
                    hostIp = prefs.smbHostIp,
                    shareAndPath = prefs.smbShareAndPath,
                    authMode = prefs.smbAuthMode,
                    targetCacheDir = activeCacheDir
                ).map { entity ->
                    entity.copy(isFavorite = entity.id in existingFavoritesIds)
                }
                if (smbPhotos.isNotEmpty()) {
                    photoDao.upsertPhotos(smbPhotos)
                    totalUpserted += smbPhotos.size
                }
            }

            // 4. Enforce LRU storage quota while protecting Favorites
            smartCacheManager.enforceStorageQuota()
            totalUpserted
        }
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

    suspend fun markPhotoViewed(photoId: String) = withContext(Dispatchers.IO) {
        photoDao.touchPhotoAccessTime(photoId)
    }

    private fun buildEffectiveWikimediaQuery(prefs: UserPreferences): String {
        return if (prefs.activeCategory == WikimediaCategoryFilter.CUSTOM_QUERY) {
            val userText = prefs.customSearchQuery.trim().ifBlank { "Nature landscape" }
            "$userText filetype:bitmap"
        } else {
            prefs.activeCategory.commonsQuery
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
