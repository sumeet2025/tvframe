package com.nostr.tvframe.data.safety

/**
 * Every blocklist the Play build's family-safe Custom Search uses, kept in one file so they're
 * easy to review and extend. The GitHub build doesn't use them (open search).
 *
 * Matching rules:
 *  - [BLOCKED_CATEGORY_PATTERNS]: case-insensitive substring of a Commons category name (or of
 *    the file title). A pattern starting with a space only matches at the start of a word, so
 *    " porn" doesn't hit "Topornya" and " gore" doesn't hit "Gregorek".
 *  - [BLOCKED_WORDS]: whole word after normalizing (lowercase, accents and common
 *    letter/number swaps removed, e.g. "S3x" or "n.u.d.e").
 *  - [BLOCKED_WORD_PREFIXES]: any word starting with this.
 *  - [BLOCKED_PHRASES]: these words next to each other.
 *
 * Patterns are chosen to avoid harmless names: e.g. "breasts" (not "breast", which would hit
 * birds like "Red-breasted merganser"), "sexual" (not "sex", which would hit Sussex/Essex), and
 * no "death" (Death Valley) or "blood" (blood moon).
 */
object SafeSearchBlocklists {

    /** Curated Commons sets the Play build searches in: Featured pictures, Quality images, Valued images. */
    const val CURATED_CATEGORIES_KEYWORD =
        "incategory:Featured_pictures_on_Wikimedia_Commons|Quality_images|Valued_images"

    val BLOCKED_CATEGORY_PATTERNS: List<String> = listOf(
        // Nudity and sexual content
        " nude", "nudity", "naked", "nakedness", "topless", "bottomless", "unclothed",
        "breasts", "nipple", "areola", "buttocks", "genital", "penis", "vagina", "vulva",
        "testicle", "scrotum", "pubic", "sexual", "sexuality", "sex act", "sex toy", "sex work",
        "erotic", "erotica", " porn", "pornograph", "obscen", "fetish", "bdsm", "bondage",
        "lingerie", "underwear", "brassiere", "bikini", "swimsuit", "striptease", "stripper",
        "burlesque", "pin-up", "pinup", "playboy", "intercourse", "masturbat", "orgasm",
        "prostitut", "brothel", "kama sutra", "phallic", "phallus",
        // Medical / anatomy
        "anatomy", "anatomical", "medical", "surgery", "surgical", "autopsy", "dissection",
        "pathology", "disease", "diseases", "wound", "injuries", "injury", "skin condition",
        "dermatolog", "gynecolog", "gynaecolog", "urolog", "childbirth", "obstetric",
        "medical illustration", "x-ray", "radiograph",
        // Gore, violence, death
        " gore", "corpse", "corpses", "cadaver", "dead bodies", "dead body", "dead people",
        "human remains", "mummies", "skeletons of humans", "violence", "violent", "torture",
        "execution", "executions", "massacre", "genocide", "atrocit", "war crime", "lynching",
        "mutilat", "decapitat", "beheading", "suicide", "murder", "homicide", "crime scene",
        "graphic content", "animal cruelty", "slaughter", "hunting trophies",
        // Drugs
        "drug use", "illegal drugs", "cannabis use", "smoking cannabis"
    )

    val BLOCKED_WORDS: Set<String> = setOf(
        "sex", "sexy", "sexual", "sexually", "sexting", "nude", "nudes", "nudity", "nudist",
        "nudism", "naked", "noods", "nsfw", "nsfl", "xxx", "porn", "porno", "pron", "erotic",
        "erotica", "boob", "boobs", "boobies", "tit", "tits", "titty", "titties", "breast",
        "breasts", "nipple", "nipples", "areola", "penis", "dick", "dicks", "cock", "cocks",
        "vagina", "vulva", "pussy", "clit", "clitoris", "genital", "genitals", "genitalia",
        "testicle", "testicles", "scrotum", "ass", "arse", "butt", "butts",
        "buttocks", "anal", "anus", "booty", "twerk", "orgasm", "orgy", "cum", "semen",
        "horny", "slut", "whore", "hooker", "milf", "dildo", "vibrator",
        "fetish", "kink", "kinky", "bdsm", "bondage", "lingerie", "panties", "thong",
        "underwear", "bra", "bikini", "topless", "bottomless", "stripper", "striptease",
        "hentai", "playboy", "onlyfans", "camgirl", "intercourse", "fuck", "fucking",
        "shit", "bitch", "gore", "gory", "corpse", "corpses", "cadaver", "cadavers",
        "autopsy", "dissection", "beheading", "beheaded", "decapitation", "decapitated",
        "execution", "suicide", "torture", "murder", "massacre", "genocide", "lynching",
        "mutilation", "dismembered", "gun", "guns", "rifle", "drugs", "cocaine", "heroin",
        "meth", "rape", "incest", "pedo", "paedo", "lolita", "loli", "shota"
    )

    val BLOCKED_WORD_PREFIXES: List<String> = listOf(
        "porn", "pornog", "masturb", "erotic", "genital", "fetish", "prostitut", "nudit",
        "sexual", "orgasm", "pedophil", "paedophil"
    )

    val BLOCKED_PHRASES: List<List<String>> = listOf(
        listOf("dead", "body"), listOf("dead", "bodies"), listOf("strip", "club"),
        listOf("sex", "toy"), listOf("blow", "job"), listOf("hand", "job"),
        listOf("bare", "chest"), listOf("bare", "skin"), listOf("in", "the", "nude"),
        listOf("human", "remains"), listOf("crime", "scene"), listOf("red", "light", "district")
    )
}
