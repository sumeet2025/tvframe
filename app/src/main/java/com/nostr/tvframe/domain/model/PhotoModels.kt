package com.nostr.tvframe.domain.model

/**
 * Origin source of a photo item.
 * Note: QR codes are rendered ONLY for [WIKIMEDIA] items and auto-hidden for [USB] and [SMB].
 */
enum class PhotoSourceType {
    WIKIMEDIA,
    USB,
    SMB
}

/**
 * Global UI Scale / Menu Density so the user can shrink or enlarge all menus, text, and boxes on screen.
 */
enum class UiScaleOption(val label: String, val scaleFactor: Float) {
    COMPACT("Compact (75%)", 0.75f),
    NORMAL("Normal (85%)", 0.85f),
    LARGE("Large (95%)", 0.95f),
    EXTRA_LARGE("Extra Large (108%)", 1.08f)
}

/**
 * Controls how photos fill the 16:9 TV screen during slideshows and screensaver mode.
 */
enum class PhotoDisplayMode(val label: String) {
    FULLSCREEN_CROP("Full-Screen Fill (No Margins)"),
    FIT_WITH_BLUR("Fit Entire Photo (Blurred Sides)"),
    FIT_BLACK_BARS("Fit Entire Photo (Black Bars)")
}

/**
 * Curated Wikimedia Commons category presets plus Picture of the Day and custom search query.
 */
enum class WikimediaCategoryFilter(val displayName: String, val commonsQuery: String) {
    PICTURES_OF_THE_DAY("Pictures of the Day", "incategory:\"Pictures_of_the_day\" filetype:bitmap"),
    MOUNTAINS("Mountains", "incategory:\"Featured_pictures_of_mountains\" OR incategory:\"Quality_images_of_mountains\" filetype:bitmap"),
    RAIN("Rain & Storms", "incategory:\"Quality_images_of_rain\" OR incategory:\"Rain_in_art\" OR \"rain landscape\" filetype:bitmap"),
    BIRDS("Birds", "incategory:\"Featured_pictures_of_birds\" filetype:bitmap"),
    LAKES("Lakes & Reflections", "incategory:\"Featured_pictures_of_lakes\" OR incategory:\"Quality_images_of_lakes\" filetype:bitmap"),
    LANDSCAPES("Landscapes", "incategory:\"Featured_pictures_of_landscapes\" filetype:bitmap"),
    NIGHT_SKY("Night Sky & Aurora", "incategory:\"Featured_pictures_of_astronomy\" OR incategory:\"Quality_images_of_auroras\" filetype:bitmap"),
    ARCHITECTURE("Architecture", "incategory:\"Featured_pictures_of_architecture\" filetype:bitmap"),
    CUSTOM_QUERY("Custom Search", "")
}

/**
 * Independent screen anchor positions for the Metadata Text Card and QR Code.
 */
enum class OverlayAnchor(val label: String) {
    TOP_LEFT("Top-Left"),
    TOP_RIGHT("Top-Right"),
    BOTTOM_LEFT("Bottom-Left"),
    BOTTOM_RIGHT("Bottom-Right"),
    CENTER_BOTTOM("Center-Bottom")
}

/**
 * Typography choices optimized for 10-foot Android TV viewing.
 */
enum class FontFamilyOption(val label: String) {
    SYSTEM_DEFAULT("System Default"),
    SANS_SERIF("Clean Sans-Serif"),
    SERIF("Editorial Serif"),
    MONOSPACE("Technical Monospace"),
    CURSIVE_DISPLAY("Gallery Display")
}

/**
 * Font size scales tuned for 10-foot TV viewing distance.
 */
enum class TextScaleOption(
    val label: String,
    val primarySp: Int,
    val secondarySp: Int
) {
    SMALL("Small", 14, 11),
    MEDIUM("Medium", 18, 14),
    LARGE("Large", 22, 17),
    EXTRA_LARGE("Extra Large", 28, 21)
}

/**
 * Text color presets for metadata overlays.
 */
enum class TextColorPreset(val label: String, val argbHex: Long) {
    PURE_WHITE("Pure White", 0xFFFFFFFF),
    WARM_IVORY("Warm Ivory", 0xFFFFF8E7),
    SKY_CYAN("Sky Cyan", 0xFF7DD3FC),
    AMBER_GOLD("Amber Gold", 0xFFFDE047),
    MINT_GREEN("Mint Green", 0xFF86EFAC)
}

/**
 * Contrast backing mode behind overlay text.
 */
enum class TextContrastMode(val label: String) {
    SEMI_TRANSPARENT_CARD("Semi-Transparent Card"),
    DROP_SHADOW_ONLY("Drop Shadow Only"),
    CARD_AND_SHADOW("Card + Drop Shadow")
}

/**
 * Color themes for dynamic ZXing QR Code rendering.
 */
enum class QrColorTheme(
    val label: String,
    val foregroundArgb: Int,
    val backgroundArgb: Int
) {
    CLASSIC_WHITE("Classic Black on White", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    DARK_TRANSLUCENT("White on Translucent Black", 0xFFFFFFFF.toInt(), 0xCC0F172A.toInt()),
    HIGH_CONTRAST_INVERTED("High-Contrast Inverted", 0xFFFDE047.toInt(), 0xEE000000.toInt()),
    SUBTLE_SLATE("Ice Blue on Deep Slate", 0xFF38BDF8.toInt(), 0xDD090D16.toInt())
}

/**
 * Aspect ratio / orientation filter for TV display.
 * Defaults to WIDESCREEN_16_9 / LANDSCAPE_ONLY so photos fill the TV cleanly.
 */
enum class OrientationFilter(val label: String) {
    WIDESCREEN_16_9("Widescreen Only (16:9 / 16:10)"),
    LANDSCAPE_ONLY("All Landscape (Horizontal)"),
    PORTRAIT_ONLY("Portrait Only"),
    ALL("All Orientations")
}

/**
 * Minimum resolution floor for fetched Wikimedia images.
 */
enum class ResolutionFloor(val label: String, val minWidthPx: Int) {
    HD_720P("720p HD (1280px+)", 1280),
    FHD_1080P("1080p Full HD (1920px+)", 1920),
    QHD_2K("2K QHD (2560px+)", 2560),
    UHD_4K("4K UHD (3840px+)", 3840)
}

/**
 * SMB authentication mode for local network NAS shares.
 */
enum class SmbAuthMode(val label: String) {
    GUEST_ANONYMOUS("Guest / Anonymous"),
    USER_PASSWORD("Username & Password")
}

/**
 * Comprehensive user settings controlling UI density, data sources, slideshow motion, and overlay appearance.
 */
data class UserPreferences(
    // Global Menu / UI Density Scale
    val uiScale: UiScaleOption = UiScaleOption.NORMAL,

    // Source, Resolution & Full-Screen Fit Filtering
    val enableWikimedia: Boolean = true,
    val enableUsbSource: Boolean = false,
    val enableSmbSource: Boolean = false,
    val activeCategory: WikimediaCategoryFilter = WikimediaCategoryFilter.PICTURES_OF_THE_DAY,
    val customSearchQuery: String = "Alpine mountains sunset",
    val orientationFilter: OrientationFilter = OrientationFilter.LANDSCAPE_ONLY,
    val resolutionFloor: ResolutionFloor = ResolutionFloor.FHD_1080P,
    val photoDisplayMode: PhotoDisplayMode = PhotoDisplayMode.FULLSCREEN_CROP,
    val favoritesOnlyInScreensaver: Boolean = false,

    // Independent Anchoring
    val textAnchor: OverlayAnchor = OverlayAnchor.BOTTOM_LEFT,
    val qrAnchor: OverlayAnchor = OverlayAnchor.BOTTOM_RIGHT,

    // Typography & Contrast
    val fontFamily: FontFamilyOption = FontFamilyOption.SANS_SERIF,
    val textScale: TextScaleOption = TextScaleOption.MEDIUM,
    val textColor: TextColorPreset = TextColorPreset.PURE_WHITE,
    val textContrastMode: TextContrastMode = TextContrastMode.CARD_AND_SHADOW,

    // QR Code Styling & Visibility
    val showPhotographerCredit: Boolean = true,
    val showLocation: Boolean = true,
    val showQrCodeOnWikimedia: Boolean = true,
    val qrColorTheme: QrColorTheme = QrColorTheme.DARK_TRANSLUCENT,
    val qrSizeDp: Int = 88,
    val qrQuietZoneModules: Int = 2,
    val overlayEdgePaddingDp: Int = 28,

    // Motion, Ambience & Burn-In Safeguards
    val slideIntervalSeconds: Int = 30,
    val enableKenBurnsEffect: Boolean = true,
    val enableAmbientBlurBackdrop: Boolean = true,
    val enableOledBurnInShift: Boolean = true,

    // USB & Cache Quotas
    val usbDirectoryPath: String = "",
    val usbTreeUriString: String = "",
    val useUsbForImageCache: Boolean = true,
    val cacheQuotaMb: Int = 1024,

    // SMB Network Configuration
    val smbHostIp: String = "",
    val smbShareAndPath: String = "",
    val smbAuthMode: SmbAuthMode = SmbAuthMode.GUEST_ANONYMOUS
)

/**
 * Unified domain representation of a photo across Wikimedia Commons, USB Storage, and SMB NAS.
 */
data class PhotoItem(
    val id: String,
    val sourceType: PhotoSourceType,
    val imageUrlOrUri: String,
    val localCachedFilePath: String?,
    val photographerName: String,
    val locationName: String,
    val commonsPageUrl: String?,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean,
    val lastAccessedEpochMs: Long,
    val fileSizeBytes: Long
) {
    /**
     * QR Code is shown ONLY when globally enabled AND the photo originates from Wikimedia Commons
     * with a valid Commons page URL. Local USB and SMB files always auto-hide the QR code.
     */
    fun shouldRenderQrCode(prefs: UserPreferences): Boolean {
        return prefs.showQrCodeOnWikimedia &&
            sourceType == PhotoSourceType.WIKIMEDIA &&
            !commonsPageUrl.isNullOrBlank()
    }

    /**
     * Resolves the best display model for Coil (prefers local cached file if present and readable).
     */
    fun resolveDisplayModel(): Any {
        if (!localCachedFilePath.isNullOrBlank()) {
            val file = java.io.File(localCachedFilePath)
            if (file.exists() && file.canRead()) {
                return file
            }
        }
        return imageUrlOrUri
    }
}
