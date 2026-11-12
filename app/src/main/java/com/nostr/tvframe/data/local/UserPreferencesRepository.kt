package com.nostr.tvframe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nostr.tvframe.domain.model.FontFamilyOption
import com.nostr.tvframe.domain.model.OrientationFilter
import com.nostr.tvframe.domain.model.OverlayAnchor
import com.nostr.tvframe.domain.model.PhotoDisplayMode
import com.nostr.tvframe.domain.model.QrColorTheme
import com.nostr.tvframe.domain.model.ResolutionFloor
import com.nostr.tvframe.domain.model.SmbAuthMode
import com.nostr.tvframe.domain.model.TextColorPreset
import com.nostr.tvframe.domain.model.TextContrastMode
import com.nostr.tvframe.domain.model.TextScaleOption
import com.nostr.tvframe.domain.model.UiScaleOption
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvframe_settings")

/**
 * Manages reactive user preferences via Jetpack DataStore for UI density scale, full-screen fit mode,
 * resolution/orientation filters, overlay positioning, typography, QR styling, and USB/SMB paths.
 */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val UI_SCALE = stringPreferencesKey("ui_scale")
        val ENABLE_WIKIMEDIA = booleanPreferencesKey("enable_wikimedia")
        val ENABLE_USB_SOURCE = booleanPreferencesKey("enable_usb_source")
        val ENABLE_SMB_SOURCE = booleanPreferencesKey("enable_smb_source")
        val ACTIVE_CATEGORY = stringPreferencesKey("active_category")
        val CUSTOM_SEARCH_QUERY = stringPreferencesKey("custom_search_query")
        val ORIENTATION_FILTER = stringPreferencesKey("orientation_filter")
        val RESOLUTION_FLOOR = stringPreferencesKey("resolution_floor")
        val PHOTO_DISPLAY_MODE = stringPreferencesKey("photo_display_mode")
        val FAVORITES_ONLY_SCREENSAVER = booleanPreferencesKey("favorites_only_screensaver")

        val TEXT_ANCHOR = stringPreferencesKey("text_anchor")
        val QR_ANCHOR = stringPreferencesKey("qr_anchor")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val TEXT_COLOR = stringPreferencesKey("text_color")
        val TEXT_CONTRAST_MODE = stringPreferencesKey("text_contrast_mode")

        val SHOW_PHOTOGRAPHER = booleanPreferencesKey("show_photographer")
        val SHOW_LOCATION = booleanPreferencesKey("show_location")
        val SHOW_QR_WIKIMEDIA = booleanPreferencesKey("show_qr_wikimedia")
        val QR_COLOR_THEME = stringPreferencesKey("qr_color_theme")
        val QR_SIZE_DP = intPreferencesKey("qr_size_dp")
        val QR_QUIET_ZONE = intPreferencesKey("qr_quiet_zone")
        val OVERLAY_PADDING_DP = intPreferencesKey("overlay_padding_dp")

        val SLIDE_INTERVAL_SEC = intPreferencesKey("slide_interval_sec")
        val ENABLE_KEN_BURNS = booleanPreferencesKey("enable_ken_burns")
        val ENABLE_AMBIENT_BLUR = booleanPreferencesKey("enable_ambient_blur")
        val ENABLE_OLED_SHIFT = booleanPreferencesKey("enable_oled_shift")

        val USB_DIR_PATH = stringPreferencesKey("usb_dir_path")
        val USB_TREE_URI = stringPreferencesKey("usb_tree_uri")
        val USE_USB_FOR_CACHE = booleanPreferencesKey("use_usb_for_cache")
        val CACHE_QUOTA_MB = intPreferencesKey("cache_quota_mb")

        val SMB_HOST_IP = stringPreferencesKey("smb_host_ip")
        val SMB_SHARE_PATH = stringPreferencesKey("smb_share_path")
        val SMB_AUTH_MODE = stringPreferencesKey("smb_auth_mode")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            uiScale = enumOrDefault(prefs[Keys.UI_SCALE], UiScaleOption.NORMAL),
            enableWikimedia = prefs[Keys.ENABLE_WIKIMEDIA] ?: true,
            enableUsbSource = prefs[Keys.ENABLE_USB_SOURCE] ?: false,
            enableSmbSource = prefs[Keys.ENABLE_SMB_SOURCE] ?: false,
            activeCategory = enumOrDefault(prefs[Keys.ACTIVE_CATEGORY], WikimediaCategoryFilter.PICTURES_OF_THE_DAY),
            customSearchQuery = prefs[Keys.CUSTOM_SEARCH_QUERY] ?: "",
            orientationFilter = enumOrDefault(prefs[Keys.ORIENTATION_FILTER], OrientationFilter.LANDSCAPE_ONLY),
            resolutionFloor = enumOrDefault(prefs[Keys.RESOLUTION_FLOOR], ResolutionFloor.FHD_1080P),
            photoDisplayMode = enumOrDefault(prefs[Keys.PHOTO_DISPLAY_MODE], PhotoDisplayMode.FULLSCREEN_CROP),
            favoritesOnlyInScreensaver = prefs[Keys.FAVORITES_ONLY_SCREENSAVER] ?: false,

            textAnchor = enumOrDefault(prefs[Keys.TEXT_ANCHOR], OverlayAnchor.BOTTOM_LEFT),
            qrAnchor = enumOrDefault(prefs[Keys.QR_ANCHOR], OverlayAnchor.BOTTOM_RIGHT),
            fontFamily = enumOrDefault(prefs[Keys.FONT_FAMILY], FontFamilyOption.SANS_SERIF),
            textScale = enumOrDefault(prefs[Keys.TEXT_SCALE], TextScaleOption.MEDIUM),
            textColor = enumOrDefault(prefs[Keys.TEXT_COLOR], TextColorPreset.PURE_WHITE),
            textContrastMode = enumOrDefault(prefs[Keys.TEXT_CONTRAST_MODE], TextContrastMode.CARD_AND_SHADOW),

            showPhotographerCredit = prefs[Keys.SHOW_PHOTOGRAPHER] ?: true,
            showLocation = prefs[Keys.SHOW_LOCATION] ?: true,
            showQrCodeOnWikimedia = prefs[Keys.SHOW_QR_WIKIMEDIA] ?: true,
            qrColorTheme = enumOrDefault(prefs[Keys.QR_COLOR_THEME], QrColorTheme.DARK_TRANSLUCENT),
            qrSizeDp = prefs[Keys.QR_SIZE_DP] ?: 88,
            qrQuietZoneModules = prefs[Keys.QR_QUIET_ZONE] ?: 2,
            overlayEdgePaddingDp = prefs[Keys.OVERLAY_PADDING_DP] ?: 28,

            slideIntervalSeconds = prefs[Keys.SLIDE_INTERVAL_SEC] ?: 30,
            enableKenBurnsEffect = prefs[Keys.ENABLE_KEN_BURNS] ?: true,
            enableAmbientBlurBackdrop = prefs[Keys.ENABLE_AMBIENT_BLUR] ?: true,
            enableOledBurnInShift = prefs[Keys.ENABLE_OLED_SHIFT] ?: true,

            usbDirectoryPath = prefs[Keys.USB_DIR_PATH] ?: "",
            usbTreeUriString = prefs[Keys.USB_TREE_URI] ?: "",
            useUsbForImageCache = prefs[Keys.USE_USB_FOR_CACHE] ?: true,
            cacheQuotaMb = prefs[Keys.CACHE_QUOTA_MB] ?: 1024,

            smbHostIp = prefs[Keys.SMB_HOST_IP] ?: "",
            smbShareAndPath = prefs[Keys.SMB_SHARE_PATH] ?: "",
            smbAuthMode = enumOrDefault(prefs[Keys.SMB_AUTH_MODE], SmbAuthMode.GUEST_ANONYMOUS)
        )
    }

    suspend fun updateUiScale(uiScale: UiScaleOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UI_SCALE] = uiScale.name
        }
    }

    suspend fun updatePhotoDisplayMode(displayMode: PhotoDisplayMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PHOTO_DISPLAY_MODE] = displayMode.name
        }
    }

    suspend fun updateSources(
        enableWikimedia: Boolean? = null,
        enableUsb: Boolean? = null,
        enableSmb: Boolean? = null,
        favoritesOnlyScreensaver: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            enableWikimedia?.let { prefs[Keys.ENABLE_WIKIMEDIA] = it }
            enableUsb?.let { prefs[Keys.ENABLE_USB_SOURCE] = it }
            enableSmb?.let { prefs[Keys.ENABLE_SMB_SOURCE] = it }
            favoritesOnlyScreensaver?.let { prefs[Keys.FAVORITES_ONLY_SCREENSAVER] = it }
        }
    }

    suspend fun updateWikimediaFilter(
        category: WikimediaCategoryFilter? = null,
        customQuery: String? = null,
        orientation: OrientationFilter? = null,
        resolutionFloor: ResolutionFloor? = null
    ) {
        context.dataStore.edit { prefs ->
            category?.let { prefs[Keys.ACTIVE_CATEGORY] = it.name }
            customQuery?.let { prefs[Keys.CUSTOM_SEARCH_QUERY] = it }
            orientation?.let { prefs[Keys.ORIENTATION_FILTER] = it.name }
            resolutionFloor?.let { prefs[Keys.RESOLUTION_FLOOR] = it.name }
        }
    }

    suspend fun updateOverlayAnchors(textAnchor: OverlayAnchor, qrAnchor: OverlayAnchor) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEXT_ANCHOR] = textAnchor.name
            prefs[Keys.QR_ANCHOR] = qrAnchor.name
        }
    }

    suspend fun updateTypography(
        fontFamily: FontFamilyOption,
        textScale: TextScaleOption,
        textColor: TextColorPreset,
        contrastMode: TextContrastMode
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_FAMILY] = fontFamily.name
            prefs[Keys.TEXT_SCALE] = textScale.name
            prefs[Keys.TEXT_COLOR] = textColor.name
            prefs[Keys.TEXT_CONTRAST_MODE] = contrastMode.name
        }
    }

    suspend fun updateQrAndVisibility(
        showPhotographer: Boolean,
        showLocation: Boolean,
        showQrOnWikimedia: Boolean,
        qrColorTheme: QrColorTheme,
        qrSizeDp: Int,
        qrQuietZone: Int,
        edgePaddingDp: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_PHOTOGRAPHER] = showPhotographer
            prefs[Keys.SHOW_LOCATION] = showLocation
            prefs[Keys.SHOW_QR_WIKIMEDIA] = showQrOnWikimedia
            prefs[Keys.QR_COLOR_THEME] = qrColorTheme.name
            prefs[Keys.QR_SIZE_DP] = qrSizeDp
            prefs[Keys.QR_QUIET_ZONE] = qrQuietZone
            prefs[Keys.OVERLAY_PADDING_DP] = edgePaddingDp
        }
    }

    suspend fun updateSlideshowMotion(
        intervalSeconds: Int,
        enableKenBurns: Boolean,
        enableAmbientBlur: Boolean,
        enableOledShift: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SLIDE_INTERVAL_SEC] = intervalSeconds
            prefs[Keys.ENABLE_KEN_BURNS] = enableKenBurns
            prefs[Keys.ENABLE_AMBIENT_BLUR] = enableAmbientBlur
            prefs[Keys.ENABLE_OLED_SHIFT] = enableOledShift
        }
    }

    suspend fun updateUsbConfiguration(
        directoryPath: String? = null,
        treeUriString: String? = null,
        useUsbForCache: Boolean? = null,
        enableUsbSource: Boolean? = null,
        cacheQuotaMb: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            directoryPath?.let { prefs[Keys.USB_DIR_PATH] = it }
            treeUriString?.let { prefs[Keys.USB_TREE_URI] = it }
            useUsbForCache?.let { prefs[Keys.USE_USB_FOR_CACHE] = it }
            enableUsbSource?.let { prefs[Keys.ENABLE_USB_SOURCE] = it }
            cacheQuotaMb?.let { prefs[Keys.CACHE_QUOTA_MB] = it }
        }
    }

    suspend fun updateSmbConfiguration(
        hostIp: String,
        shareAndPath: String,
        authMode: SmbAuthMode,
        enableSmbSource: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SMB_HOST_IP] = hostIp.trim()
            prefs[Keys.SMB_SHARE_PATH] = shareAndPath.trim().trimStart('/')
            prefs[Keys.SMB_AUTH_MODE] = authMode.name
            prefs[Keys.ENABLE_SMB_SOURCE] = enableSmbSource
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T {
        if (value == null) return default
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
