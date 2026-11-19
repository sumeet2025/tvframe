package com.nostr.tvframe.util

import com.nostr.tvframe.domain.model.WikimediaCategoryFilter.CUSTOM_QUERY
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter.MOUNTAINS
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter.PICTURES_OF_THE_DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreFlavorTest {

    @Test
    fun playBuildHidesCustomSearch() {
        assertFalse(CUSTOM_QUERY in StoreFlavor.availableCategories(allowCustomSearch = false))
        assertTrue(MOUNTAINS in StoreFlavor.availableCategories(allowCustomSearch = false))
    }

    @Test
    fun githubBuildKeepsCustomSearch() {
        assertTrue(CUSTOM_QUERY in StoreFlavor.availableCategories(allowCustomSearch = true))
    }

    @Test
    fun carriedOverCustomSearchIsDroppedInPlayBuild() {
        assertEquals(
            setOf(MOUNTAINS),
            StoreFlavor.sanitizeCategories(setOf(MOUNTAINS, CUSTOM_QUERY), allowCustomSearch = false)
        )
    }

    @Test
    fun onlyCustomSearchFallsBackToPicturesOfTheDay() {
        assertEquals(
            setOf(PICTURES_OF_THE_DAY),
            StoreFlavor.sanitizeCategories(setOf(CUSTOM_QUERY), allowCustomSearch = false)
        )
    }

    @Test
    fun githubBuildLeavesSelectionAlone() {
        assertEquals(
            setOf(MOUNTAINS, CUSTOM_QUERY),
            StoreFlavor.sanitizeCategories(setOf(MOUNTAINS, CUSTOM_QUERY), allowCustomSearch = true)
        )
    }
}
