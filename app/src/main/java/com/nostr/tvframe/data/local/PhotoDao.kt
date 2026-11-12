package com.nostr.tvframe.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object supporting reactive gallery streams, DreamService playlists,
 * Favorites persistence, and deletion of downloaded/cached photos from both Gallery and Favorites.
 */
@Dao
interface PhotoDao {

    /**
     * Ordered by stable keys only. Never order by lastAccessedEpochMs here: the slideshow touches
     * that column on every slide, which used to reshuffle the list underneath it.
     */
    @Query("SELECT * FROM photos ORDER BY isFavorite DESC, createdAtEpochMs DESC, id ASC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE sourceType IN (:allowedSources) ORDER BY lastAccessedEpochMs DESC")
    fun observePhotosBySources(allowedSources: List<String>): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isFavorite = 1 ORDER BY createdAtEpochMs DESC, id ASC")
    fun observeFavoritePhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun countPhotos(): Int

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id IN (:photoIds)")
    suspend fun getPhotosByIds(photoIds: List<String>): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE isFavorite = 1")
    suspend fun getAllFavoritesSync(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE sourceType IN (:allowedSources) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSlideshowBatch(allowedSources: List<String>, limit: Int = 100): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Query("UPDATE photos SET isFavorite = :isFavorite, lastAccessedEpochMs = :timestamp WHERE id = :photoId")
    suspend fun updateFavoriteState(
        photoId: String,
        isFavorite: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE photos SET lastAccessedEpochMs = :timestamp WHERE id = :photoId")
    suspend fun touchPhotoAccessTime(photoId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE photos SET isFavorite = 0 WHERE id IN (:photoIds)")
    suspend fun unmarkFavoritesByIds(photoIds: List<String>)

    @Query("DELETE FROM photos WHERE id IN (:photoIds)")
    suspend fun deletePhotosByIds(photoIds: List<String>)

    @Query("UPDATE photos SET isFavorite = 0 WHERE isFavorite = 1")
    suspend fun unmarkAllFavorites()

    @Query("DELETE FROM photos WHERE isFavorite = 1")
    suspend fun deleteAllFavoritesPermanently()

    @Query("DELETE FROM photos WHERE isFavorite = 0")
    suspend fun deleteAllNonFavoritePhotos()

    /**
     * Returns non-favorited cached photos ordered from oldest accessed to newest (LRU)
     * so SmartCacheManager can evict temporary files when storage quota is exceeded.
     */
    @Query("SELECT * FROM photos WHERE isFavorite = 0 ORDER BY lastAccessedEpochMs ASC")
    suspend fun getNonFavoritePhotosOrderedByLru(): List<PhotoEntity>

    /**
     * Removes unmounted non-favorited USB items when a USB drive is hot-unplugged.
     */
    @Query("DELETE FROM photos WHERE sourceType = 'USB' AND isFavorite = 0")
    suspend fun removeTemporaryUsbEntries()
}
