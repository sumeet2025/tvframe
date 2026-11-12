package com.nostr.tvframe.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PagedCollectorTest {

    /** Fake search: [total] results "<cat>-<n>", pages of [pageSize]. */
    private fun fakeSearch(total: Map<String, Int>, pageSize: Int = 5, calls: MutableList<String>? = null) =
        { category: String, offset: Int ->
            calls?.add("$category@$offset")
            val count = total.getValue(category)
            val items = (offset until minOf(offset + pageSize, count)).map { "$category-$it" }
            val next = if (offset + pageSize < count) offset + pageSize else null
            SearchPage(items, next)
        }

    @Test
    fun pagesUntilTargetIsReached() = runBlocking {
        val calls = mutableListOf<String>()
        // Only even numbers are "wanted", so each page of 5 yields 2-3 items.
        val result = PagedCollector.collect(
            categories = listOf("a"),
            target = 6,
            maxPagesPerCategory = 10,
            startOffset = { 0 },
            fetchPage = { c, o -> fakeSearch(mapOf("a" to 100), calls = calls)(c, o) },
            isWanted = { it.substringAfter('-').toInt() % 2 == 0 },
            key = { it }
        )
        assertEquals(listOf("a-0", "a-2", "a-4", "a-6", "a-8", "a-10"), result.picked)
        assertEquals(listOf("a@0", "a@5", "a@10"), calls)
        // a-12 (on the page at offset 10) was wanted but not used, so resume there.
        assertEquals(10, result.resumeOffsets["a"])
    }

    @Test
    fun mixesCategoriesAndStopsWhenExhausted() = runBlocking {
        val result = PagedCollector.collect(
            categories = listOf("a", "b"),
            target = 50,
            maxPagesPerCategory = 10,
            startOffset = { 0 },
            fetchPage = { c, o -> fakeSearch(mapOf("a" to 3, "b" to 2))(c, o) },
            isWanted = { true },
            key = { it }
        )
        assertEquals(listOf("a-0", "b-0", "a-1", "b-1", "a-2"), result.picked)
        // Both searches ran out: start over next time.
        assertEquals(mapOf("a" to 0, "b" to 0), result.resumeOffsets)
    }

    @Test
    fun respectsPageCap() = runBlocking {
        val result = PagedCollector.collect(
            categories = listOf("a"),
            target = 10,
            maxPagesPerCategory = 2,
            startOffset = { 0 },
            fetchPage = { c, o -> fakeSearch(mapOf("a" to 100))(c, o) },
            isWanted = { false },
            key = { it }
        )
        assertEquals(0, result.picked.size)
        assertEquals(10, result.scanned)
        assertEquals(10, result.resumeOffsets["a"])
    }

    @Test
    fun resumesFromStartOffsetAndSkipsUnwanted() = runBlocking {
        val seen = setOf("a-5", "a-6")
        val result = PagedCollector.collect(
            categories = listOf("a"),
            target = 2,
            maxPagesPerCategory = 5,
            startOffset = { 5 },
            fetchPage = { c, o -> fakeSearch(mapOf("a" to 100))(c, o) },
            isWanted = { it !in seen },
            key = { it }
        )
        assertEquals(listOf("a-7", "a-8"), result.picked)
        assertEquals(5, result.resumeOffsets["a"]) // a-9 on the same page is still unused
    }

    @Test
    fun wrapsAroundOnceWhenResumingNearTheEnd() = runBlocking {
        val calls = mutableListOf<String>()
        val result = PagedCollector.collect(
            categories = listOf("a"),
            target = 4,
            maxPagesPerCategory = 10,
            startOffset = { 10 },
            fetchPage = { c, o -> fakeSearch(mapOf("a" to 12), calls = calls)(c, o) },
            isWanted = { it.substringAfter('-').toInt() in setOf(1, 3, 11) },
            key = { it }
        )
        assertEquals(listOf("a-11", "a-1", "a-3"), result.picked)
        // 10 -> end -> wrap to 0 -> 5 -> stops before passing the original start (10)
        assertEquals(listOf("a@10", "a@0", "a@5"), calls)
        assertEquals(0, result.resumeOffsets["a"])
    }
}
