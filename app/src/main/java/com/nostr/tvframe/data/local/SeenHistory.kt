package com.nostr.tvframe.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Every Wikimedia photo a sync has ever downloaded. Syncs skip these, so you keep getting new
 * photos instead of the same ones again. Not touched by "Clear Non-Favorites" or deleting photos;
 * only "Reset seen history" empties it.
 */
@Entity(tableName = "seen_photos")
data class SeenPhotoEntity(
    @PrimaryKey val id: String,
    val firstSeenEpochMs: Long = System.currentTimeMillis()
)

/**
 * Where each search (keyed by its full Commons query) should continue on the next sync, so
 * syncs move forward through the results instead of starting from the top every time.
 */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val queryKey: String,
    val nextOffset: Int
)

@Dao
interface SeenHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(rows: List<SeenPhotoEntity>)

    @Query("SELECT id FROM seen_photos WHERE id IN (:ids)")
    suspend fun seenAmong(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM seen_photos")
    fun observeSeenCount(): Flow<Int>

    @Query("DELETE FROM seen_photos")
    suspend fun clearSeen()

    @Query("SELECT * FROM sync_cursors WHERE queryKey IN (:keys)")
    suspend fun cursorsFor(keys: List<String>): List<SyncCursorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursors(rows: List<SyncCursorEntity>)

    @Query("DELETE FROM sync_cursors")
    suspend fun clearCursors()
}
