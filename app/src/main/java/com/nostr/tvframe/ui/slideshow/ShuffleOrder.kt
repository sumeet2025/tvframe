package com.nostr.tvframe.ui.slideshow

import kotlin.random.Random

/**
 * Shuffled play order for "Favorites (Random)" in the screensaver.
 *
 * A new random order ("cycle") is made when the screensaver starts. Every photo is shown once
 * before any photo repeats; when the cycle ends a fresh shuffle starts, and its first photo is
 * never the one just shown (unless there is only one photo). Photos added mid-cycle are slotted
 * in at a random spot that is still to come, so they're shown this cycle; removed photos are
 * skipped. Everything is tracked by photo id.
 */
class ShuffleOrder(private val random: Random = Random.Default) {
    private val cycle = ArrayList<String>()
    private val known = HashSet<String>()

    /** Number of shuffles made so far (1 after the first [reconcile] with photos). */
    var cycleCount: Int = 0
        private set

    /**
     * Merges the latest playlist into the current cycle and returns the live ids in cycle order.
     * [currentId] is the photo on screen, used to place new photos after it.
     */
    fun reconcile(incoming: List<String>, currentId: String?): List<String> {
        if (cycle.isEmpty()) {
            if (incoming.isNotEmpty()) startCycle(incoming.distinct(), avoidFirst = null)
        } else {
            val currentPos = currentId?.let { cycle.indexOf(it) } ?: -1
            for (id in incoming) {
                if (known.add(id)) {
                    // Random position strictly after the current photo (end of cycle allowed).
                    val from = currentPos + 1
                    val at = from + random.nextInt(cycle.size - from + 1)
                    cycle.add(at, id)
                }
            }
        }
        val present = incoming.toHashSet()
        return cycle.filter { it in present }
    }

    /** Next photo after [currentId]; starts a new shuffle once every live photo has been shown. */
    fun next(currentId: String?, live: List<String>): String? {
        if (live.isEmpty()) return null
        val liveSet = live.toHashSet()
        val pos = currentId?.let { cycle.indexOf(it) } ?: -1
        for (i in pos + 1 until cycle.size) {
            if (cycle[i] in liveSet) return cycle[i]
        }
        startCycle(live.distinct(), avoidFirst = currentId)
        return cycle.first()
    }

    /** Previous photo in this cycle (D-pad Left); stays put at the start of a cycle. */
    fun previous(currentId: String?, live: List<String>): String? {
        if (live.isEmpty()) return null
        val liveSet = live.toHashSet()
        val pos = currentId?.let { cycle.indexOf(it) } ?: return live.first()
        for (i in pos - 1 downTo 0) {
            if (cycle[i] in liveSet) return cycle[i]
        }
        return currentId.takeIf { it in liveSet } ?: next(null, live)
    }

    /** Photo to show when [missingId] was removed: the next live photo after it in this cycle. */
    fun successorOf(missingId: String?, live: List<String>): String? {
        if (live.isEmpty()) return null
        val liveSet = live.toHashSet()
        val pos = missingId?.let { cycle.indexOf(it) } ?: -1
        for (i in pos + 1 until cycle.size) {
            if (cycle[i] in liveSet) return cycle[i]
        }
        return cycle.firstOrNull { it in liveSet } ?: live.first()
    }

    private fun startCycle(ids: List<String>, avoidFirst: String?) {
        val shuffled = ids.shuffled(random).toMutableList()
        if (avoidFirst != null && shuffled.size > 1 && shuffled[0] == avoidFirst) {
            val swapWith = 1 + random.nextInt(shuffled.size - 1)
            shuffled[0] = shuffled[swapWith]
            shuffled[swapWith] = avoidFirst
        }
        cycle.clear()
        cycle.addAll(shuffled)
        known.clear()
        known.addAll(shuffled)
        cycleCount++
    }
}
