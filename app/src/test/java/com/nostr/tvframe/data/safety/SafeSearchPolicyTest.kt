package com.nostr.tvframe.data.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeSearchPolicyTest {

    private val strict = StrictSafeSearchPolicy
    private val open = PassthroughSafeSearchPolicy

    // --- Word blocklist ---

    @Test
    fun everydaySearchesAreAllowed() {
        listOf(
            "beach", "statue", "art", "sunset", "Swiss Alps", "Kingfisher", "Dolomites",
            "classic cars", "title page", "Sussex", "Essex coast", "red-breasted merganser",
            "blood moon", "Death Valley", "butterfly", "Grassland", "Scunthorpe", "nudibranch",
            "cocktail", "Hancock tower", "Christmas balls", "assembly hall", "Massachusetts"
        ).forEach { assertTrue("should allow: $it", strict.isQueryAllowed(it)) }
    }

    @Test
    fun adultWordsAreRefused() {
        listOf("nude", "Naked beach", "sexy", "porn", "pornography", "NSFW", "xxx", "boobs",
            "erotic art", "topless", "lingerie", "hentai", "gore", "corpse", "beheading")
            .forEach { assertFalse("should refuse: $it", strict.isQueryAllowed(it)) }
    }

    @Test
    fun commonVariantsAreRefused() {
        listOf("s3x", "S E X", "n.u.d.e", "p0rn", "NUUUDE", "sexxx", "Nudé", "pr0n", "n00ds",
            "nudebeach", "sexyphotos", "masturbation", "fetishes", "dead body", "strip club")
            .forEach { assertFalse("should refuse: $it", strict.isQueryAllowed(it)) }
    }

    @Test
    fun emptyQueryIsNotRefused() {
        assertTrue(strict.isQueryAllowed("   "))
    }

    @Test
    fun githubBuildAllowsEverything() {
        assertTrue(open.isQueryAllowed("nude"))
        assertTrue(open.isResultAllowed(listOf("Category:Nude women"), "File:x.jpg"))
        assertTrue(open.isResultAllowed(null, ""))
        assertEquals("nude filetype:bitmap", open.customSearchQuery(" nude "))
    }

    // --- Curated query ---

    @Test
    fun strictQueryIsLimitedToCuratedImages() {
        val q = strict.customSearchQuery("Swiss Alps")
        assertTrue(q.startsWith("Swiss Alps filetype:bitmap "))
        assertTrue(q.endsWith(SafeSearchBlocklists.CURATED_CATEGORIES_KEYWORD))
    }

    @Test
    fun userTextCannotAddSearchKeywords() {
        val q = strict.customSearchQuery("beach -incategory:\"Quality images\" | insource:x")
        assertFalse(q.substringBefore(" filetype:").contains(":"))
        assertFalse(q.substringBefore(" filetype:").contains("\""))
        assertFalse(q.substringBefore(" filetype:").contains("|"))
    }

    // --- Category filter ---

    @Test
    fun blockedCategoriesDropTheResult() {
        listOf(
            "Category:Nude women in art", "Category:Naked people", "Category:Female breasts",
            "Category:Erotic photography", "Category:Human anatomy", "Category:Corpses",
            "Category:Violence in art", "Category:Topless women", "Category:Pornography",
            "Category:Medical illustrations", "Category:Executions in art", "Category:Bikinis"
        ).forEach { cat ->
            assertFalse("should drop: $cat", strict.isResultAllowed(listOf("Category:Beaches", cat), "File:a.jpg"))
        }
    }

    @Test
    fun harmlessCategoriesAreKept() {
        val cats = listOf(
            "Category:Beaches of Portugal", "Category:Red-breasted mergansers", "Category:Sussex",
            "Category:Death Valley National Park", "Category:Topornya", "Category:Gregorek",
            "Category:Denuded hills", "Category:Republic squares", "Category:Sunsets in Spain"
        )
        assertTrue(strict.isResultAllowed(cats, "File:Sunset at the beach.jpg"))
    }

    @Test
    fun blockedTitleDropsTheResult() {
        assertFalse(strict.isResultAllowed(listOf("Category:Louvre"), "File:Naked woman-Louvre.jpg"))
    }

    @Test
    fun unknownCategoriesAreDroppedInStrictMode() {
        assertFalse(strict.isResultAllowed(null, "File:Beach.jpg"))
        assertTrue(strict.isResultAllowed(emptyList(), "File:Beach.jpg"))
    }

    @Test
    fun matchingIgnoresCaseAndUnderscores() {
        assertTrue(strict.isBlockedName("NUDE_Sculptures"))
        assertTrue(strict.isBlockedName("Dead_bodies_in_art"))
        assertFalse(strict.isBlockedName("Mountains_of_Switzerland"))
    }
}
