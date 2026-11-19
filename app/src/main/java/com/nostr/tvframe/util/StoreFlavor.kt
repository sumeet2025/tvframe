package com.nostr.tvframe.util

import com.nostr.tvframe.BuildConfig
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter

/**
 * Features that differ between the GitHub APK and the Google Play build.
 * The values come from the `github` / `play` product flavors in app/build.gradle.kts.
 */
object StoreFlavor {
    /** GitHub build only: offer the "Grant All-Files Access" button for USB drives. */
    val allowAllFilesAccess: Boolean = BuildConfig.ALLOW_ALL_FILES_ACCESS

    /** GitHub build only: show the Lightning zap address and QR code in About. */
    val showLightningZap: Boolean = BuildConfig.SHOW_LIGHTNING_ZAP

    /** GitHub build only: free-text "Custom Search" on Wikimedia Commons. */
    val allowCustomSearch: Boolean = BuildConfig.ALLOW_CUSTOM_SEARCH

    /** The Wikimedia categories this build offers. */
    val availableCategories: List<WikimediaCategoryFilter> =
        availableCategories(allowCustomSearch)

    fun availableCategories(allowCustomSearch: Boolean): List<WikimediaCategoryFilter> =
        WikimediaCategoryFilter.entries.filter {
            allowCustomSearch || it != WikimediaCategoryFilter.CUSTOM_QUERY
        }

    /**
     * Drops categories this build doesn't offer (for example a Custom Search selection carried
     * over from the GitHub APK) and falls back to Pictures of the Day if nothing is left.
     */
    fun sanitizeCategories(
        selected: Set<WikimediaCategoryFilter>,
        allowCustomSearch: Boolean = this.allowCustomSearch
    ): Set<WikimediaCategoryFilter> {
        val allowed = availableCategories(allowCustomSearch)
        val kept = selected.filter { it in allowed }.toSet()
        return kept.ifEmpty { setOf(WikimediaCategoryFilter.PICTURES_OF_THE_DAY) }
    }
}
