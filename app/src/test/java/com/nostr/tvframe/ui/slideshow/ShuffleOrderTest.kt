package com.nostr.tvframe.ui.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleOrderTest {

    private val ids = (1..20).map { "p$it" }

    /** Plays [count] slides the way the screensaver does and returns what was shown. */
    private fun play(order: ShuffleOrder, live: List<String>, count: Int): List<String> {
        val queue = order.reconcile(live, null)
        var current: String? = queue.first()
        val shown = mutableListOf(current!!)
        repeat(count - 1) {
            current = order.next(current, live)
            shown += current!!
        }
        return shown
    }

    @Test
    fun everyPhotoShownOnceBeforeAnyRepeat() {
        repeat(50) { seed ->
            val shown = play(ShuffleOrder(Random(seed)), ids, ids.size * 3)
            shown.chunked(ids.size).forEach { cycle ->
                assertEquals(ids.toSet(), cycle.toSet())
                assertEquals(ids.size, cycle.size)
            }
        }
    }

    @Test
    fun reshufflesAfterEachCycle() {
        val order = ShuffleOrder(Random(7))
        val shown = play(order, ids, ids.size * 4)
        assertEquals(4, order.cycleCount)
        val cycles = shown.chunked(ids.size)
        assertTrue("cycles should differ", cycles.distinct().size > 1)
    }

    @Test
    fun noBackToBackRepeatAcrossCycles() {
        repeat(200) { seed ->
            val shown = play(ShuffleOrder(Random(seed)), listOf("a", "b", "c"), 30)
            shown.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun differentStartsGiveDifferentOrders() {
        val first = ShuffleOrder(Random(1)).reconcile(ids, null)
        val second = ShuffleOrder(Random(2)).reconcile(ids, null)
        assertEquals(ids.toSet(), first.toSet())
        assertNotEquals(first, second)
    }

    @Test
    fun photoAddedMidCycleIsShownThisCycle() {
        val order = ShuffleOrder(Random(3))
        var live = ids
        var current: String? = order.reconcile(live, null).first()
        val shown = mutableListOf(current!!)
        repeat(4) { current = order.next(current, live); shown += current!! }
        live = ids + "new"
        order.reconcile(live, current)
        repeat(ids.size + 1 - shown.size) { current = order.next(current, live); shown += current!! }
        assertEquals(live.toSet(), shown.toSet())
        assertEquals(1, order.cycleCount)
    }

    @Test
    fun removedPhotoIsSkipped() {
        val order = ShuffleOrder(Random(4))
        val queue = order.reconcile(ids, null)
        val removed = queue[5]
        val live = ids - removed
        order.reconcile(live, queue[0])
        var current: String? = queue[0]
        val shown = mutableListOf(current!!)
        repeat(live.size - 1) { current = order.next(current, live); shown += current!! }
        assertTrue(removed !in shown)
        assertEquals(live.toSet(), shown.toSet())
    }

    @Test
    fun singlePhotoKeepsShowing() {
        val shown = play(ShuffleOrder(Random(5)), listOf("only"), 5)
        assertEquals(List(5) { "only" }, shown)
    }

    @Test
    fun previousGoesBackWithinCycle() {
        val order = ShuffleOrder(Random(6))
        val queue = order.reconcile(ids, null)
        assertEquals(queue[1], order.previous(queue[2], ids))
        assertEquals(queue[0], order.previous(queue[0], ids))
    }
}
