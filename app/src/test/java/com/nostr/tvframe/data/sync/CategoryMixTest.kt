package com.nostr.tvframe.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMixTest {

    @Test
    fun takesRoundRobinFromEveryCategory() {
        val result = CategoryMix.interleaveDistinct(
            listOf(listOf("a1", "a2", "a3"), listOf("b1", "b2"), listOf("c1")),
            limit = 10
        ) { it }
        assertEquals(listOf("a1", "b1", "c1", "a2", "b2", "a3"), result)
    }

    @Test
    fun dropsPhotosThatAppearInTwoCategories() {
        val result = CategoryMix.interleaveDistinct(
            listOf(listOf("x", "a2"), listOf("x", "b2")),
            limit = 10
        ) { it }
        assertEquals(listOf("x", "a2", "b2"), result)
    }

    @Test
    fun stopsAtLimit() {
        val result = CategoryMix.interleaveDistinct(
            listOf(listOf(1, 2, 3), listOf(4, 5, 6)),
            limit = 3
        ) { it }
        assertEquals(listOf(1, 4, 2), result)
    }

    @Test
    fun handlesEmptyInput() {
        assertEquals(emptyList<Int>(), CategoryMix.interleaveDistinct(emptyList<List<Int>>(), 5) { it })
        assertEquals(emptyList<Int>(), CategoryMix.interleaveDistinct(listOf(listOf(1)), 0) { it })
    }
}
