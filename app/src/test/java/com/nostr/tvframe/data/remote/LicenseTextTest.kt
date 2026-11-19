package com.nostr.tvframe.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseTextTest {

    @Test
    fun plainLicenseIsKept() {
        assertEquals("CC BY-SA 4.0", LicenseText.clean("CC BY-SA 4.0"))
    }

    @Test
    fun blankOrMissingBecomesEmpty() {
        assertEquals("", LicenseText.clean(null))
        assertEquals("", LicenseText.clean("   "))
    }

    @Test
    fun markupAndEntitiesAreStripped() {
        assertEquals("Public domain", LicenseText.clean("<span>Public&nbsp;domain</span>"))
        assertEquals("GFDL & CC BY 3.0", LicenseText.clean("GFDL &amp; CC BY 3.0"))
    }

    @Test
    fun longValuesAreShortened() {
        val result = LicenseText.clean("A".repeat(100))
        assertTrue(result.length <= 40)
        assertTrue(result.endsWith("…"))
    }
}
