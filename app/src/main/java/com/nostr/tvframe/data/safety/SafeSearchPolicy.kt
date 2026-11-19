package com.nostr.tvframe.data.safety

import java.text.Normalizer

/**
 * How Custom Search is filtered. The Play build uses [StrictSafeSearchPolicy], the GitHub build
 * [PassthroughSafeSearchPolicy]; the flavor picks one in `FlavorSafeSearch` (src/play, src/github).
 */
interface SafeSearchPolicy {
    /** True when this policy filters anything (used to request categories and show hints). */
    val isStrict: Boolean

    /** False if this search text must not be sent at all. */
    fun isQueryAllowed(query: String): Boolean

    /** The Commons search string to send for this Custom Search text. */
    fun customSearchQuery(userQuery: String): String

    /**
     * Whether a result with these categories (and file title) may be shown. `null` categories
     * means they weren't returned for this result; a strict policy rejects those too.
     */
    fun isResultAllowed(categories: List<String>?, title: String = ""): Boolean

    companion object {
        const val BLOCKED_QUERY_MESSAGE = "That search isn't available. Try another word."
    }
}

/** GitHub build: open search, nothing filtered (same as before 1.4.1). */
object PassthroughSafeSearchPolicy : SafeSearchPolicy {
    override val isStrict = false
    override fun isQueryAllowed(query: String) = true
    override fun customSearchQuery(userQuery: String) = "${userQuery.trim()} filetype:bitmap"
    override fun isResultAllowed(categories: List<String>?, title: String) = true
}

/**
 * Play build: family-safe Custom Search in three layers.
 *  1. Adult words are refused before anything is sent ([isQueryAllowed]).
 *  2. Only curated Commons images are searched: Featured pictures, Quality images, Valued images.
 *  3. Results in any blocked category (nudity, sexual, anatomy/medical, gore, violence) are dropped,
 *     and so are results whose categories are unknown.
 */
object StrictSafeSearchPolicy : SafeSearchPolicy {
    override val isStrict = true

    override fun isQueryAllowed(query: String): Boolean {
        val words = normalizeWords(query)
        if (words.isEmpty()) return true
        if (words.any { it in SafeSearchBlocklists.BLOCKED_WORDS }) return false
        if (words.any { w -> SafeSearchBlocklists.BLOCKED_WORD_PREFIXES.any { w.startsWith(it) } }) return false
        // Words glued together ("sexyphotos", "nudebeach") and phrases ("dead body")
        val glued = words.joinToString("")
        if (SafeSearchBlocklists.BLOCKED_WORD_PREFIXES.any { glued.contains(it) }) return false
        if (GLUED_BLOCKERS.any { glued.contains(it) }) return false
        return SafeSearchBlocklists.BLOCKED_PHRASES.none { phrase -> containsPhrase(words, phrase) }
    }

    override fun customSearchQuery(userQuery: String): String =
        "${sanitizeForCirrus(userQuery)} filetype:bitmap ${SafeSearchBlocklists.CURATED_CATEGORIES_KEYWORD}"

    override fun isResultAllowed(categories: List<String>?, title: String): Boolean {
        if (categories == null) return false
        if (isBlockedName(title.removePrefix("File:"))) return false
        return categories.none { isBlockedName(it.removePrefix("Category:")) }
    }

    /** True if a category name or file title matches [SafeSearchBlocklists.BLOCKED_CATEGORY_PATTERNS]. */
    fun isBlockedName(name: String): Boolean {
        // Leading space so " pattern" means "at the start of a word"; punctuation counts as a break.
        val text = " " + name.replace('_', ' ').lowercase().replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
        return SafeSearchBlocklists.BLOCKED_CATEGORY_PATTERNS.any { text.contains(it) }
    }

    /**
     * Long, unambiguous words that are also caught when glued to other words ("nudebeach").
     * Short ones like "ass" or "tit" aren't, so "classic" or "title" still work.
     */
    private val GLUED_BLOCKERS = listOf(
        "nude", "naked", "porno", "xxx", "nsfw", "boobs", "titties", "penis", "vagina",
        "pussy", "hentai", "topless", "lingerie", "orgasm", "dildo", "milf", "sexy", "onlyfans"
    )

    /**
     * Lowercase, strip accents, undo common letter/number swaps (s3x, p0rn, @ss), split on
     * anything that isn't a letter, and join runs of single letters ("n u d e", "s.e.x").
     */
    internal fun normalizeWords(text: String): List<String> {
        val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val swapped = buildString {
            for (c in folded) {
                append(
                    when (c) {
                        '0' -> 'o'; '1', '!', '|' -> 'i'; '3' -> 'e'; '4', '@' -> 'a'
                        '5', '$' -> 's'; '7' -> 't'; '8' -> 'b'
                        else -> c
                    }
                )
            }
        }
        val raw = swapped.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val words = mutableListOf<String>()
        val singles = StringBuilder()
        for (w in raw) {
            if (w.length == 1) {
                singles.append(w)
            } else {
                if (singles.isNotEmpty()) { words += singles.toString(); singles.clear() }
                words += w
            }
        }
        if (singles.isNotEmpty()) words += singles.toString()
        // Also try each word with repeated letters squeezed ("nuuude" -> "nude", "sexxx" -> "sex")
        return words.flatMap { w ->
            val squeezed = w.replace(Regex("(.)\\1+"), "$1")
            if (squeezed != w) listOf(w, squeezed) else listOf(w)
        }
    }

    private fun containsPhrase(words: List<String>, phrase: List<String>): Boolean {
        if (phrase.size > words.size) return false
        return (0..words.size - phrase.size).any { i -> phrase.indices.all { j -> words[i + j] == phrase[j] } }
    }

    /** Keeps the user's text as plain search words so it can't add or override search keywords. */
    internal fun sanitizeForCirrus(query: String): String =
        query.replace(Regex("[:\"|\\\\]"), " ").replace(Regex("\\s+"), " ").trim()
}
