package com.nostr.tvframe.data.sync

/** One page of search results. [nextOffset] is null when there are no more results. */
data class SearchPage<T>(val items: List<T>, val nextOffset: Int?)

/** Result of [PagedCollector.collect]. */
data class CollectResult<C, T>(
    /** Picked items, mixed round-robin across categories, at most the requested count. */
    val picked: List<T>,
    /**
     * Where the next sync should continue each category's search so nothing wanted is skipped:
     * the page holding the first wanted-but-unused result, the next page if everything was used,
     * or 0 when the search ran out (start over to pick up newly added photos).
     */
    val resumeOffsets: Map<C, Int>,
    /** How many search results were looked at in total. */
    val scanned: Int,
    /** How many of those passed [PagedCollector.collect]'s `isWanted` check (before the limit). */
    val wantedFound: Int
)

/**
 * Pages through several searches round-robin until enough wanted results are found.
 * A search that resumes part-way through wraps around to the top once when it hits the end.
 */
object PagedCollector {

    private class Found<T>(val item: T, val pageOffset: Int)

    /**
     * @param target how many items to pick in total.
     * @param maxPagesPerCategory safety cap so one sync can't page forever through a huge search.
     * @param startOffset where each category's search starts (see [CollectResult.resumeOffsets]).
     * @param fetchPage loads one page of a category's results at an offset.
     * @param isWanted filters out results that don't match, are already in the gallery or were seen.
     * @param onPage called after every page with the running number of results scanned.
     */
    suspend fun <C, T, K> collect(
        categories: List<C>,
        target: Int,
        maxPagesPerCategory: Int,
        startOffset: (C) -> Int,
        fetchPage: suspend (category: C, offset: Int) -> SearchPage<T>,
        isWanted: (T) -> Boolean,
        key: (T) -> K,
        onPage: (scanned: Int) -> Unit = {}
    ): CollectResult<C, T> {
        val found = categories.associateWith { mutableListOf<Found<T>>() }
        val keysPerCategory = categories.associateWith { HashSet<K>() }
        val starts = categories.associateWith { startOffset(it).coerceAtLeast(0) }
        val nextOffset = categories.associateWith { starts.getValue(it) as Int? }.toMutableMap()
        val wrapped = categories.associateWith { false }.toMutableMap()
        val pagesFetched = categories.associateWith { 0 }.toMutableMap()
        var scanned = 0

        fun pickedSoFar(): List<T> =
            CategoryMix.interleaveDistinct(categories.map { c -> found.getValue(c).map { it.item } }, target, key)

        while (target > 0) {
            val active = categories.filter { nextOffset[it] != null && pagesFetched.getValue(it) < maxPagesPerCategory }
            if (active.isEmpty()) break
            for (category in active) {
                val offset = nextOffset[category] ?: continue
                val page = fetchPage(category, offset)
                pagesFetched[category] = pagesFetched.getValue(category) + 1
                scanned += page.items.size
                for (item in page.items) {
                    if (isWanted(item) && keysPerCategory.getValue(category).add(key(item))) {
                        found.getValue(category).add(Found(item, offset))
                    }
                }
                // A page with no items can't advance; treat it as the end to avoid looping.
                var next = page.nextOffset?.takeIf { page.items.isNotEmpty() && it > offset }
                val start = starts.getValue(category)
                if (next == null && !wrapped.getValue(category) && start > 0) {
                    // Reached the end after resuming mid-way: wrap once to the top so results
                    // before the resume point (or newly added photos) still get a chance.
                    next = 0
                    wrapped[category] = true
                } else if (next != null && wrapped.getValue(category) && next >= start) {
                    next = null // back where this sync started: everything has been looked at
                }
                nextOffset[category] = next
                onPage(scanned)
            }
            if (pickedSoFar().size >= target) break
        }

        val picked = pickedSoFar()
        val pickedKeys = picked.map(key).toHashSet()
        val resume = categories.associateWith { category ->
            val firstUnused = found.getValue(category).firstOrNull { key(it.item) !in pickedKeys }
            firstUnused?.pageOffset ?: nextOffset[category] ?: 0
        }
        return CollectResult(
            picked = picked,
            resumeOffsets = resume,
            scanned = scanned,
            wantedFound = found.values.sumOf { it.size }
        )
    }
}
