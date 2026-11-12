package com.nostr.tvframe.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class CachePruningTest {

    private val manyRemotePhotos = (1..500).map { CachePruning.Candidate("w$it", null) }

    @Test
    fun underQuotaDeletesNothingEvenWithManyPhotos() {
        // The old code trimmed the gallery to 300 photos here.
        assertEquals(emptyList<String>(), CachePruning.pickVictims(manyRemotePhotos, 100, 1_000))
    }

    @Test
    fun overQuotaNeverDeletesPhotosWithoutAFile() {
        // The old code deleted all of these rows (freeing nothing) and emptied the gallery.
        assertEquals(emptyList<String>(), CachePruning.pickVictims(manyRemotePhotos, 2_000, 1_000))
    }

    @Test
    fun overQuotaDeletesOldestFilesUntilUnderTarget() {
        val candidates = listOf(
            CachePruning.Candidate("old-remote", null),
            CachePruning.Candidate("old-smb", 400),
            CachePruning.Candidate("mid-smb", 400),
            CachePruning.Candidate("new-smb", 400)
        )
        assertEquals(listOf("old-smb", "mid-smb"), CachePruning.pickVictims(candidates, 1_700, 1_000))
    }
}
