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
     * Frees space when the cache directory is above 85% of the user's quota by deleting the
     * least-recently-viewed non-favorite photos that have a file TVFrame copied into its cache
     * (network-share copies). Favorites are never touched.
     *
     * Under the quota nothing is deleted: the old version also trimmed the gallery to 300 photos,
     * and when over quota it deleted gallery rows that had no file at all (Wikimedia photos live in
     * Coil's own size-limited disk cache), which freed nothing and could empty the whole gallery.
     */
    suspend fun enforceStorageQuota() = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val maxQuotaBytes = prefs.cacheQuotaMb.toLong() * 1024L * 1024L
        val targetThresholdBytes = (maxQuotaBytes * 0.85).toLong()

        val activeDir = resolveActiveCacheDirectory()
        val currentBytes = computeDirectorySizeBytes(activeDir)
        if (currentBytes <= targetThresholdBytes) return@withContext

        val candidates = photoDao.getNonFavoritePhotosOrderedByLru()
            .filterNot { it.isFavorite } // double-check the favorite safeguard
            .map { entity ->
                // Only files managed inside our cache directory, never original user USB photos
                val managedFile = entity.localCachedFilePath
                    ?.takeIf { it.contains("tvframe_") }
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                CachePruning.Candidate(entity.id, managedFile?.length())
            }
        val victims = CachePruning.pickVictims(candidates, currentBytes, targetThresholdBytes)
        if (victims.isEmpty()) return@withContext

        val byId = photoDao.getPhotosByIds(victims).associateBy { it.id }
        val deleted = victims.filter { id ->
            val path = byId[id]?.localCachedFilePath ?: return@filter false
            runCatching { File(path).delete() }.getOrDefault(false)
        }
        if (deleted.isNotEmpty()) {
            photoDao.deletePhotosByIds(deleted)
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
