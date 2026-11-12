package com.nostr.tvframe.data.storage

/** Pure decision logic for [SmartCacheManager.enforceStorageQuota] (unit-tested). */
object CachePruning {

    /**
     * A non-favorite photo, oldest-viewed first. [managedFileBytes] is the size of the file
     * TVFrame copied into its cache for it, or null if it has none (deleting it frees nothing).
     */
    data class Candidate(val id: String, val managedFileBytes: Long?)

    /**
     * Picks photos to delete, oldest first, until usage drops to [targetBytes]. Only photos with
     * a managed file are picked. Returns nothing when already at or under the target.
     */
    fun pickVictims(candidates: List<Candidate>, currentBytes: Long, targetBytes: Long): List<String> {
        var bytes = currentBytes
        val victims = mutableListOf<String>()
        for (candidate in candidates) {
            if (bytes <= targetBytes) break
            val size = candidate.managedFileBytes ?: continue
            victims += candidate.id
            bytes -= size
        }
        return victims
    }
}
