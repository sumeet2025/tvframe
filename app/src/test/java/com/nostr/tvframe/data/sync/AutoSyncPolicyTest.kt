package com.nostr.tvframe.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncPolicyTest {
    private val hour = 60L * 60 * 1000
    private val interval = 6 * hour
    private val now = 100 * hour

    private fun due(lastSync: Long?, lastClear: Long?, empty: Boolean) =
        AutoSyncPolicy.isDue(now, lastSync, lastClear, empty, interval)

    @Test fun neverSynced_isDue() = assertTrue(due(null, null, empty = true))

    @Test fun recentSync_withPhotos_notDue() = assertFalse(due(now - hour, null, empty = false))

    @Test fun oldSync_isDue_evenAfterClear() = assertTrue(due(now - 7 * hour, now - hour, empty = true))

    @Test fun emptyGallery_withoutClear_isDue() = assertTrue(due(now - hour, null, empty = true))

    @Test fun emptyGallery_clearedAfterLastSync_notDue() = assertFalse(due(now - 2 * hour, now - hour, empty = true))

    @Test fun emptyGallery_clearedBeforeLastSync_isDue() = assertTrue(due(now - hour, now - 2 * hour, empty = true))

    @Test fun favoritesLeftAfterClear_notDue() = assertFalse(due(now - 2 * hour, now - hour, empty = false))
}
