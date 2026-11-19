package com.nostr.tvframe.data.remote

/** Cleans the `LicenseShortName` value from Commons extmetadata for display. */
object LicenseText {
    private const val MAX_LENGTH = 40

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val text = raw
            .replace(Regex("<[^>]*>"), " ")   // the field is usually plain text, but strip any markup
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH - 1).trimEnd() + "…"
    }
}
