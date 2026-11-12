package com.nostr.tvframe.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PhotoEntity::class, SeenPhotoEntity::class, SyncCursorEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao

    abstract fun seenHistoryDao(): SeenHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 (app 1.3.0): adds the seen-photo history and per-search sync cursors.
         * Photos already downloaded from Wikimedia count as seen, so the first sync after the
         * update doesn't bring them back. Existing photos and favorites are untouched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `seen_photos` " +
                        "(`id` TEXT NOT NULL, `firstSeenEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_cursors` " +
                        "(`queryKey` TEXT NOT NULL, `nextOffset` INTEGER NOT NULL, PRIMARY KEY(`queryKey`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `seen_photos` (`id`, `firstSeenEpochMs`) " +
                        "SELECT `id`, `createdAtEpochMs` FROM `photos` WHERE `sourceType` = 'WIKIMEDIA'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // No destructive fallback: a missing migration must fail loudly in testing rather
                // than silently wiping favorites on users' TVs.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tvframe.db"
                )
                    .enableMultiInstanceInvalidation()
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
