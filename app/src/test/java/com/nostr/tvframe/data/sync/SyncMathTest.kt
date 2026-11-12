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
}
