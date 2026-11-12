package com.nostr.tvframe.data.sync

/**
 * Decides whether opening the app or starting the screensaver should start a sync by itself.
 *
 * - Never synced before: yes (first run).
 * - Last sync more than [minIntervalMs] ago: yes (same rhythm as the 6-hourly background sync).
 * - Gallery empty: yes, unless I cleared it myself after the last sync. A clear is deliberate,
 *   so the gallery stays as I left it until I press Sync or the regular schedule comes round.
 * - Otherwise: no.
 */
object AutoSyncPolicy {
    fun isDue(
        nowEpochMs: Long,
        lastSyncEpochMs: Long?,
        lastClearEpochMs: Long?,
        galleryEmpty: Boolean,
        minIntervalMs: Long
    ): Boolean {
        if (lastSyncEpochMs == null) return true
        if (nowEpochMs - lastSyncEpochMs > minIntervalMs) return true
        if (!galleryEmpty) return false
        val clearedSinceLastSync = lastClearEpochMs != null && lastClearEpochMs >= lastSyncEpochMs
        return !clearedSinceLastSync
    }
}
