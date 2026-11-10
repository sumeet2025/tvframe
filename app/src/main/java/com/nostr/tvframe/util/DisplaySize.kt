package com.nostr.tvframe.util

import android.content.Context
import kotlin.math.max
import kotlin.math.min

/**
 * Landscape screen size in pixels, used to cap image decode sizes so 4K sources are never
 * decoded at full resolution (a 6000x4000 photo is ~96 MB as an ARGB bitmap).
 */
object DisplaySize {

    fun screenPx(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val w = max(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        val h = min(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        return w to h
    }

    /** Screen size, but never larger than [maxW]x[maxH]. */
    fun cappedPx(context: Context, maxW: Int = 1920, maxH: Int = 1080): Pair<Int, Int> {
        val (w, h) = screenPx(context)
        return w.coerceAtMost(maxW) to h.coerceAtMost(maxH)
    }
}
