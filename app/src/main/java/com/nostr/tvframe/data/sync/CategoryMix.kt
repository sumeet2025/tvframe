package com.nostr.tvframe.data.sync

/**
 * Combines search results from several categories into one download list.
 */
object CategoryMix {

    /**
     * Takes items round-robin from each group (1st of every group, then 2nd of every group, …)
     * so every selected category is represented, drops duplicates by [key] (the same photo can be
     * in two categories), and stops at [limit].
     */
    fun <T, K> interleaveDistinct(groups: List<List<T>>, limit: Int, key: (T) -> K): List<T> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<K>()
        val out = ArrayList<T>()
        val longest = groups.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (group in groups) {
                if (i >= group.size) continue
                val item = group[i]
                if (seen.add(key(item))) {
                    out.add(item)
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }
}
