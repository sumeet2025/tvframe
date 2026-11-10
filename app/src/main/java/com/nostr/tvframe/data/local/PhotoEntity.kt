package com.nostr.tvframe.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nostr.tvframe.domain.model.PhotoItem
import com.nostr.tvframe.domain.model.PhotoSourceType

/**
 * Room Database Entity persisting photo metadata, local cache file reference,
 * and user Favorite state. Favorited rows are strictly protected from LRU pruning.
 */
@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastAccessedEpochMs"])
    ]
)
data class PhotoEntity(
    @PrimaryKey
    val id: String,
    val sourceType: String,
    val imageUrlOrUri: String,
    val localCachedFilePath: String?,
    val photographerName: String,
    val locationName: String,
    val commonsPageUrl: String?,
    val categoryKey: String,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastAccessedEpochMs: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long = 0L
) {
    fun toDomain(): PhotoItem = PhotoItem(
        id = id,
        sourceType = runCatching { PhotoSourceType.valueOf(sourceType) }
            .getOrDefault(PhotoSourceType.WIKIMEDIA),
        imageUrlOrUri = imageUrlOrUri,
        localCachedFilePath = localCachedFilePath,
        photographerName = photographerName,
        locationName = locationName,
        commonsPageUrl = commonsPageUrl,
        width = width,
        height = height,
        isFavorite = isFavorite,
        lastAccessedEpochMs = lastAccessedEpochMs,
        fileSizeBytes = fileSizeBytes
    )
}
