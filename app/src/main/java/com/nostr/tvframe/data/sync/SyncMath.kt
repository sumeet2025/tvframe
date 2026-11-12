package com.nostr.tvframe.data.sync

/**
 * Small pure helpers for building Wikimedia Commons requests (unit-tested).
 */
object SyncMath {

    /**
     * Thumbnail widths Wikimedia serves from its standard, pre-rendered set. Asking for other
     * widths makes Commons render a new thumbnail on demand, which is slower and rate-limited.
     */
    private val STANDARD_THUMB_WIDTHS = intArrayOf(1280, 1920, 3840)

    /**
     * Width to request for downloaded photos: at least the minimum resolution the user picked and
     * at least the TV's screen width, rounded up to a standard Commons size (max 3840).
     * Commons returns the original file when it is smaller than the requested width.
     */
    fun thumbnailWidthFor(minWidthPx: Int, screenWidthPx: Int): Int {
        val wanted = maxOf(minWidthPx, screenWidthPx)
        return STANDARD_THUMB_WIDTHS.firstOrNull { it >= wanted } ?: STANDARD_THUMB_WIDTHS.last()
    }

    /** CirrusSearch keyword limiting results to images at least [minWidthPx] wide. */
    fun minWidthKeyword(minWidthPx: Int): String = "filew:>${minWidthPx - 1}"
}
