package com.nostr.tvframe.data.storage

import android.content.Context
import com.nostr.tvframe.data.local.PhotoDao
import com.nostr.tvframe.data.local.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enforces configurable storage quotas with automatic Least-Recently-Used (LRU) pruning.
 *
 * Key Guarantees:
 * 1. If an external USB drive is mounted and enabled for caching, uses the USB cache directory
 *    to bypass internal Android TV flash limits.
 * 2. If the USB drive is unplugged at any time, seamlessly falls back to `context.cacheDir`.
 * 3. Favorited items (`isFavorite == true`) are NEVER auto-deleted during cache cleanup routines.
 */
class SmartCacheManager(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val usbStorageDataSource: UsbStorageDataSource
) {

    /**
     * Resolves the active cache directory (USB drive if enabled & mounted, otherwise internal TV flash).
     */
    fun resolveActiveCacheDirectory(): File {
        val prefs = runBlocking { preferencesRepository.userPreferencesFlow.first() }
        if (prefs.useUsbForImageCache) {
            val usbCache = usbStorageDataSource.getUsbWritableCacheDirectory()
            if (usbCache != null) {
                return usbCache
            }
        }
        val internalDir = File(context.cacheDir, "tvframe_internal_cache")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Calculates current temporary cache usage in megabytes.
     */
    suspend fun calculateCurrentCacheUsageMb(): Long = withContext(Dispatchers.IO) {
        val activeDir = resolveActiveCacheDirectory()
        computeDirectorySizeBytes(activeDir) / (1024L * 1024L)
    }

    /**
     * Prunes the oldest-accessed non-favorited cached photos and files until disk usage
     * drops below 85% of the user's configured storage quota.
     * Favorited photos are strictly exempt from pruning.
     */
    suspend fun enforceStorageQuota() = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val maxQuotaBytes = prefs.cacheQuotaMb.toLong() * 1024L * 1024L
        val targetThresholdBytes = (maxQuotaBytes * 0.85).toLong()

        val activeDir = resolveActiveCacheDirectory()
        var currentBytes = computeDirectorySizeBytes(activeDir)

        val lruNonFavorites = photoDao.getNonFavoritePhotosOrderedByLru()
        val idsToRemoveFromDb = mutableListOf<String>()

        for (candidate in lruNonFavorites) {
            if (currentBytes <= targetThresholdBytes && lruNonFavorites.size - idsToRemoveFromDb.size <= 300) {
                break
            }
            // Double check favorite safeguard
            if (candidate.isFavorite) continue

            candidate.localCachedFilePath?.let { path ->
                // Only delete files managed inside our cache directory, never original user USB photos
                if (path.contains("tvframe_")) {
                    val f = File(path)
                    if (f.exists()) {
                        val len = f.length()
                        if (f.delete()) {
                            currentBytes = (currentBytes - len).coerceAtLeast(0L)
                        }
                    }
                }
            }
            idsToRemoveFromDb.add(candidate.id)
        }

        if (idsToRemoveFromDb.isNotEmpty()) {
            photoDao.deletePhotosByIds(idsToRemoveFromDb)
        }
    }

    private fun computeDirectorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            total += if (f.isDirectory) computeDirectorySizeBytes(f) else f.length()
        }
        return total
    }
}
