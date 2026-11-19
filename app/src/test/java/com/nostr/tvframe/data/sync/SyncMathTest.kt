package com.nostr.tvframe.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMathTest {

    @Test
    fun thumbnailIsAtLeastScreenWidth() {
        assertEquals(1920, SyncMath.thumbnailWidthFor(minWidthPx = 1280, screenWidthPx = 1920))
        assertEquals(1280, SyncMath.thumbnailWidthFor(minWidthPx = 1280, screenWidthPx = 1280))
    }

    @Test
    fun thumbnailIsAtLeastMinimumResolution() {
        assertEquals(1920, SyncMath.thumbnailWidthFor(minWidthPx = 1920, screenWidthPx = 1280))
        assertEquals(3840, SyncMath.thumbnailWidthFor(minWidthPx = 2560, screenWidthPx = 1920))
        assertEquals(3840, SyncMath.thumbnailWidthFor(minWidthPx = 3840, screenWidthPx = 1920))
    }

    @Test
    fun thumbnailNeverExceeds4k() {
        assertEquals(3840, SyncMath.thumbnailWidthFor(minWidthPx = 1920, screenWidthPx = 7680))
    }

    @Test
    fun minWidthKeywordIsInclusive() {
        assertEquals("filew:>1919", SyncMath.minWidthKeyword(1920))
    }

    @Test
    fun nextOffsetUsesGsroffsetWhenPresent() {
        assertEquals(70, SyncMath.nextSearchOffset(35, 35, 35, gsroffset = 70, categoriesTruncated = false))
    }

    @Test
    fun nextOffsetContinuesWhenOnlyCategoriesWereCutOff() {
        // Commons sends clcontinue instead of gsroffset; a full page means there's more.
        assertEquals(85, SyncMath.nextSearchOffset(50, 35, 35, gsroffset = null, categoriesTruncated = true))
    }

    @Test
    fun nextOffsetStopsOnLastPage() {
        assertEquals(null, SyncMath.nextSearchOffset(50, 35, 12, gsroffset = null, categoriesTruncated = true))
        assertEquals(null, SyncMath.nextSearchOffset(50, 35, 35, gsroffset = null, categoriesTruncated = false))
    }
}
