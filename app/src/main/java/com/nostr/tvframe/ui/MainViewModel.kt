package com.nostr.tvframe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nostr.tvframe.TVFrameApp
import com.nostr.tvframe.data.local.SmbCredentials
import com.nostr.tvframe.data.storage.UsbVolumeInfo
import com.nostr.tvframe.domain.model.FontFamilyOption
import com.nostr.tvframe.domain.model.OrientationFilter
import com.nostr.tvframe.domain.model.OverlayAnchor
import com.nostr.tvframe.domain.model.PhotoDisplayMode
import com.nostr.tvframe.domain.model.PhotoItem
import com.nostr.tvframe.domain.model.QrColorTheme
import com.nostr.tvframe.domain.model.ResolutionFloor
import com.nostr.tvframe.domain.model.SmbAuthMode
import com.nostr.tvframe.domain.model.TextColorPreset
import com.nostr.tvframe.domain.model.TextContrastMode
import com.nostr.tvframe.domain.model.TextScaleOption
import com.nostr.tvframe.domain.model.UiScaleOption
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel driving the Android TV Leanback Compose UI, UI scale control,
 * full-screen & 4K filters, Gallery & Favorites deletion, USB/SMB management, and Overlay Studio.
 */
class MainViewModel : ViewModel() {

    private val app = TVFrameApp.instance
    private val repository = app.photoRepository
    private val prefsRepo = app.preferencesRepository
    private val usbSource = app.usbStorageDataSource
    private val smbSource = app.smbNetworkDataSource
    private val smbCredentialStore = app.smbCredentialStore
    private val cacheManager = app.smartCacheManager

    val userPreferences: StateFlow<UserPreferences> = prefsRepo.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val activePlaylist: StateFlow<List<PhotoItem>> = repository.activePlaylistFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePhotos: StateFlow<List<PhotoItem>> = repository.favoritePhotosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usbBannerMessage: StateFlow<String?> = usbSource.statusBannerMessage

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _detectedUsbVolumes = MutableStateFlow<List<UsbVolumeInfo>>(emptyList())
    val detectedUsbVolumes: StateFlow<List<UsbVolumeInfo>> = _detectedUsbVolumes.asStateFlow()

    private val _cacheUsageMb = MutableStateFlow(0L)
    val cacheUsageMb: StateFlow<Long> = _cacheUsageMb.asStateFlow()

    // Multi-selection set for Gallery bulk deletion
    private val _selectedGalleryIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedGalleryIds: StateFlow<Set<String>> = _selectedGalleryIds.asStateFlow()

    // Multi-selection set for Favorites bulk deletion
    private val _selectedFavoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFavoriteIds: StateFlow<Set<String>> = _selectedFavoriteIds.asStateFlow()

    init {
        refreshUsbAndCacheMetrics()
        refreshSources()
    }

    fun updateUiScale(uiScale: UiScaleOption) {
        viewModelScope.launch {
            prefsRepo.updateUiScale(uiScale)
        }
    }

    fun cycleUiScale() {
        val entries = UiScaleOption.entries
        val current = userPreferences.value.uiScale
        val next = entries[(entries.indexOf(current) + 1) % entries.size]
        updateUiScale(next)
    }

    fun updatePhotoDisplayMode(displayMode: PhotoDisplayMode) {
        viewModelScope.launch {
            prefsRepo.updatePhotoDisplayMode(displayMode)
        }
    }

    fun refreshSources() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refreshEnabledSources()
            _isRefreshing.value = false
            refreshUsbAndCacheMetrics()
            result.onSuccess { count ->
                if (count > 0) {
                    _statusToast.value = "Synced $count matching photos into active playlist."
                }
            }.onFailure { err ->
                _statusToast.value = "Offline fallback active (${err.localizedMessage ?: "Network unavailable"})."
            }
        }
    }

    fun refreshUsbAndCacheMetrics() {
        viewModelScope.launch {
            _detectedUsbVolumes.value = usbSource.detectMountedUsbVolumes()
            _cacheUsageMb.value = cacheManager.calculateCurrentCacheUsageMb()
        }
    }

    fun hasAllFilesAccess(): Boolean = usbSource.hasAllFilesAccessPermission()

    fun selectWikimediaCategory(
        category: WikimediaCategoryFilter? = null,
        customQuery: String? = null,
        orientation: OrientationFilter? = null,
        resolutionFloor: ResolutionFloor? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateWikimediaFilter(category, customQuery, orientation, resolutionFloor)
            refreshSources()
        }
    }

    fun togglePhotoFavorite(photo: PhotoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(photo.id)
        }
    }

    fun markPhotoViewed(photo: PhotoItem) {
        viewModelScope.launch {
            repository.markPhotoViewed(photo.id)
        }
    }

    // Gallery Photo Deletion (Single, Multi-Select, and Clear All Non-Favorites)
    fun deleteSinglePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhotosFromGallery(listOf(photoId))
            _selectedGalleryIds.value = _selectedGalleryIds.value - photoId
            _selectedFavoriteIds.value = _selectedFavoriteIds.value - photoId
            _statusToast.value = "Deleted photo from TVFrame."
            refreshUsbAndCacheMetrics()
        }
    }

    fun toggleGallerySelection(photoId: String) {
        val current = _selectedGalleryIds.value.toMutableSet()
        if (!current.add(photoId)) {
            current.remove(photoId)
        }
        _selectedGalleryIds.value = current
    }

    fun selectAllGalleryPhotos() {
        _selectedGalleryIds.value = activePlaylist.value.map { it.id }.toSet()
    }

    fun clearGallerySelection() {
        _selectedGalleryIds.value = emptySet()
    }

    fun deleteSelectedGalleryPhotos() {
        viewModelScope.launch {
            val ids = _selectedGalleryIds.value.toList()
            repository.deletePhotosFromGallery(ids)
            _selectedGalleryIds.value = emptySet()
            _statusToast.value = "Deleted ${ids.size} photo(s) from Gallery."
            refreshUsbAndCacheMetrics()
        }
    }

    fun deleteAllNonFavoriteGalleryPhotos() {
        viewModelScope.launch {
            repository.deleteAllNonFavoritePhotos()
            _selectedGalleryIds.value = emptySet()
            _statusToast.value = "Cleared all non-favorite downloaded photos."
            refreshUsbAndCacheMetrics()
        }
    }

    // Favorites Multi-Select & Bulk Delete
    fun toggleFavoriteSelection(photoId: String) {
        val current = _selectedFavoriteIds.value.toMutableSet()
        if (!current.add(photoId)) {
            current.remove(photoId)
        }
        _selectedFavoriteIds.value = current
    }

    fun selectAllFavorites() {
        _selectedFavoriteIds.value = favoritePhotos.value.map { it.id }.toSet()
    }

    fun clearFavoriteSelection() {
        _selectedFavoriteIds.value = emptySet()
    }

    fun deleteSelectedFavorites(deleteFromDiskToo: Boolean) {
        viewModelScope.launch {
            val ids = _selectedFavoriteIds.value.toList()
            repository.deleteSelectedFavorites(ids, deleteFromDiskToo)
            _selectedFavoriteIds.value = emptySet()
            _statusToast.value = if (deleteFromDiskToo) {
                "Deleted ${ids.size} favorite photo(s) from disk."
            } else {
                "Unfavorited ${ids.size} photo(s)."
            }
            refreshUsbAndCacheMetrics()
        }
    }

    fun deleteAllFavorites(deleteFromDiskToo: Boolean) {
        viewModelScope.launch {
            repository.deleteAllFavorites(deleteFromDiskToo)
            _selectedFavoriteIds.value = emptySet()
            _statusToast.value = "Cleared all Favorites."
            refreshUsbAndCacheMetrics()
        }
    }

    // Overlay & Motion Customization
    fun updateAnchors(textAnchor: OverlayAnchor, qrAnchor: OverlayAnchor) {
        viewModelScope.launch {
            prefsRepo.updateOverlayAnchors(textAnchor, qrAnchor)
        }
    }

    fun updateTypography(
        fontFamily: FontFamilyOption,
        textScale: TextScaleOption,
        textColor: TextColorPreset,
        contrastMode: TextContrastMode
    ) {
        viewModelScope.launch {
            prefsRepo.updateTypography(fontFamily, textScale, textColor, contrastMode)
        }
    }

    fun updateQrAndVisibility(
        showPhotographer: Boolean,
        showLocation: Boolean,
        showQrOnWikimedia: Boolean,
        qrColorTheme: QrColorTheme,
        qrSizeDp: Int,
        qrQuietZone: Int,
        edgePaddingDp: Int
    ) {
        viewModelScope.launch {
            prefsRepo.updateQrAndVisibility(
                showPhotographer = showPhotographer,
                showLocation = showLocation,
                showQrOnWikimedia = showQrOnWikimedia,
                qrColorTheme = qrColorTheme,
                qrSizeDp = qrSizeDp,
                qrQuietZone = qrQuietZone,
                edgePaddingDp = edgePaddingDp
            )
        }
    }

    fun updateSlideshowMotion(
        intervalSeconds: Int,
        enableKenBurns: Boolean,
        enableAmbientBlur: Boolean,
        enableOledShift: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.updateSlideshowMotion(
                intervalSeconds = intervalSeconds,
                enableKenBurns = enableKenBurns,
                enableAmbientBlur = enableAmbientBlur,
                enableOledShift = enableOledShift
            )
        }
    }

    fun updateSourceToggles(
        enableWikimedia: Boolean? = null,
        enableUsb: Boolean? = null,
        enableSmb: Boolean? = null,
        favoritesOnlyScreensaver: Boolean? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateSources(enableWikimedia, enableUsb, enableSmb, favoritesOnlyScreensaver)
            refreshSources()
        }
    }

    // USB Configuration
    fun selectUsbVolumePath(path: String, useForCache: Boolean, quotaMb: Int) {
        viewModelScope.launch {
            prefsRepo.updateUsbConfiguration(
                directoryPath = path,
                useUsbForCache = useForCache,
                enableUsbSource = true,
                cacheQuotaMb = quotaMb
            )
            _statusToast.value = "Selected USB path: $path"
            refreshSources()
        }
    }

    fun onUsbSafTreeSelected(uriString: String) {
        viewModelScope.launch {
            prefsRepo.updateUsbConfiguration(
                treeUriString = uriString,
                enableUsbSource = true
            )
            _statusToast.value = "Granted USB folder access via Android TV SAF."
            refreshSources()
        }
    }

    fun updateCacheQuota(quotaMb: Int, useUsbForCache: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateUsbConfiguration(
                useUsbForCache = useUsbForCache,
                cacheQuotaMb = quotaMb
            )
            cacheManager.enforceStorageQuota()
            refreshUsbAndCacheMetrics()
        }
    }

    // SMB / NAS Configuration
    fun getSavedSmbCredentials(): SmbCredentials = smbCredentialStore.getCredentials()

    fun saveAndTestSmbShare(
        hostIp: String,
        shareAndPath: String,
        authMode: SmbAuthMode,
        domain: String,
        username: String,
        password: String
    ) {
        viewModelScope.launch {
            if (authMode == SmbAuthMode.USER_PASSWORD) {
                smbCredentialStore.saveCredentials(domain, username, password)
            }
            prefsRepo.updateSmbConfiguration(
                hostIp = hostIp,
                shareAndPath = shareAndPath,
                authMode = authMode,
                enableSmbSource = true
            )
            _statusToast.value = "Testing SMB connection to smb://$hostIp/$shareAndPath…"
            val testResult = smbSource.testSmbConnection(hostIp, shareAndPath, authMode)
            testResult.onSuccess { count ->
                _statusToast.value = "SMB Connected! Found $count photos in root. Syncing…"
                refreshSources()
            }.onFailure { err ->
                _statusToast.value = "SMB Error: ${err.localizedMessage ?: "Check IP, Share name, or credentials"}"
            }
        }
    }

    fun dismissToast() {
        _statusToast.value = null
    }
}
