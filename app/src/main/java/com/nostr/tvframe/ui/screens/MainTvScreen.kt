package com.nostr.tvframe.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nostr.tvframe.BuildConfig
import com.nostr.tvframe.R
import com.nostr.tvframe.domain.model.FontFamilyOption
import com.nostr.tvframe.domain.model.OrientationFilter
import com.nostr.tvframe.domain.model.OverlayAnchor
import com.nostr.tvframe.domain.model.PhotoDisplayMode
import com.nostr.tvframe.domain.model.PhotoItem
import com.nostr.tvframe.domain.model.PhotoSourceType
import com.nostr.tvframe.domain.model.QrColorTheme
import com.nostr.tvframe.domain.model.ResolutionFloor
import com.nostr.tvframe.domain.model.SmbAuthMode
import com.nostr.tvframe.domain.model.TextColorPreset
import com.nostr.tvframe.domain.model.TextContrastMode
import com.nostr.tvframe.domain.model.TextScaleOption
import com.nostr.tvframe.domain.model.UiScaleOption
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.domain.model.WikimediaCategoryFilter
import com.nostr.tvframe.ui.MainViewModel
import com.nostr.tvframe.ui.slideshow.SlideshowRenderer
import com.nostr.tvframe.ui.theme.TvBackgroundDark
import com.nostr.tvframe.ui.theme.TvFavoriteCoral
import com.nostr.tvframe.ui.theme.TvFocusAccent
import com.nostr.tvframe.ui.theme.TvSurfaceCard
import com.nostr.tvframe.ui.theme.TvTextPrimary
import com.nostr.tvframe.ui.theme.TvTextSecondary
import com.nostr.tvframe.ui.theme.TvZapGold
import com.nostr.tvframe.ui.theme.tvFocusableCard
import com.nostr.tvframe.util.QrCodeGenerator

enum class TvNavTab(val title: String) {
    GALLERY("🖼 Gallery & Filters"),
    FAVORITES("♥ Favorites"),
    STORAGE_SMB("💾 USB & SMB NAS"),
    OVERLAY_STUDIO("🎨 Overlay Studio"),
    TV_SETUP("📺 Android TV TV Setup"),
    ABOUT_NOSTR("⚡ About & Zaps")
}

@Composable
fun MainTvScreen(
    viewModel: MainViewModel,
    onRequestUsbSafPicker: () -> Unit,
    onRequestAllFilesPermission: () -> Unit,
    onOpenSystemDreamSettings: () -> Unit
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val playlist by viewModel.activePlaylist.collectAsState()
    val favorites by viewModel.favoritePhotos.collectAsState()
    val selectedGalleryIds by viewModel.selectedGalleryIds.collectAsState()
    val selectedFavIds by viewModel.selectedFavoriteIds.collectAsState()
    val usbVolumes by viewModel.detectedUsbVolumes.collectAsState()
    val cacheUsageMb by viewModel.cacheUsageMb.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusToast by viewModel.statusToast.collectAsState()
    val usbBanner by viewModel.usbBannerMessage.collectAsState()

    var currentTab by remember { mutableStateOf(TvNavTab.GALLERY) }
    var isFullscreenSlideshowActive by remember { mutableStateOf(false) }

    if (isFullscreenSlideshowActive) {
        SlideshowRenderer(
            photos = playlist,
            preferences = prefs,
            isDreamServiceMode = false,
            usbStatusBanner = usbBanner,
            onToggleFavorite = { viewModel.togglePhotoFavorite(it) },
            onPhotoDisplayed = { viewModel.markPhotoViewed(it) },
            onExitOrWakeTv = { isFullscreenSlideshowActive = false }
        )
        return
    }

    // Apply user-configurable UI Scale (Compact 75%, Normal 85%, Large 95%, Extra Large 108%)
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, prefs.uiScale) {
        Density(
            density = baseDensity.density * prefs.uiScale.scaleFactor,
            fontScale = prefs.uiScale.scaleFactor
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TvBackgroundDark)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Top Header Bar with Quick UI Size Button & Start Slideshow Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TVFrame",
                        color = TvTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        color = TvFocusAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        text = "Wikimedia • USB • SMB NAS • Android TV 12",
                        color = TvTextSecondary,
                        fontSize = 13.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick UI / Menu Scale Toggle right in the top bar
                    TvActionButton(
                        label = "🔍 UI Size: ${prefs.uiScale.label}",
                        accentColor = TvZapGold,
                        onClick = { viewModel.cycleUiScale() }
                    )

                    TvActionButton(
                        label = "▶ Start Live Slideshow (${playlist.size})",
                        accentColor = TvFocusAccent,
                        onClick = { isFullscreenSlideshowActive = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // All 6 Navigation Tabs in a balanced Row with inner padding so no pill is ever clipped at the screen edge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                TvNavTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableCard(cornerRadiusDp = 999)
                            .background(
                                if (selected) TvFocusAccent.copy(alpha = 0.24f) else TvSurfaceCard,
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { currentTab = tab }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            color = if (selected) TvFocusAccent else TvTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Optional Status / USB Hot-Plug Toast Banner
            val activeMessage = statusToast ?: usbBanner
            if (!activeMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .border(1.dp, TvFocusAccent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = activeMessage, color = TvTextPrimary, fontSize = 13.sp)
                    TvActionButton(
                        label = "Dismiss",
                        onClick = { viewModel.dismissToast() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    TvNavTab.GALLERY -> GalleryAndFiltersTab(
                        prefs = prefs,
                        playlist = playlist,
                        selectedGalleryIds = selectedGalleryIds,
                        isRefreshing = isRefreshing,
                        onSelectCategory = { cat, customQuery, orientation, resFloor ->
                            viewModel.selectWikimediaCategory(cat, customQuery, orientation, resFloor)
                        },
                        onSelectDisplayMode = { viewModel.updatePhotoDisplayMode(it) },
                        onRefresh = { viewModel.refreshSources() },
                        onToggleFavorite = { viewModel.togglePhotoFavorite(it) },
                        onDeleteSinglePhoto = { viewModel.deleteSinglePhoto(it.id) },
                        onToggleGallerySelect = { viewModel.toggleGallerySelection(it) },
                        onSelectAllGallery = { viewModel.selectAllGalleryPhotos() },
                        onClearGallerySelect = { viewModel.clearGallerySelection() },
                        onDeleteSelectedGallery = { viewModel.deleteSelectedGalleryPhotos() },
                        onDeleteAllNonFavorites = { viewModel.deleteAllNonFavoriteGalleryPhotos() },
                        onLaunchSlideshow = { isFullscreenSlideshowActive = true }
                    )

                    TvNavTab.FAVORITES -> FavoritesTab(
                        favorites = favorites,
                        selectedIds = selectedFavIds,
                        prefs = prefs,
                        onToggleSelect = { viewModel.toggleFavoriteSelection(it) },
                        onSelectAll = { viewModel.selectAllFavorites() },
                        onClearSelection = { viewModel.clearFavoriteSelection() },
                        onDeleteSelected = { deleteDisk -> viewModel.deleteSelectedFavorites(deleteDisk) },
                        onDeleteAll = { deleteDisk -> viewModel.deleteAllFavorites(deleteDisk) },
                        onDeleteSinglePhoto = { viewModel.deleteSinglePhoto(it.id) },
                        onToggleFavoritesOnlyScreensaver = { enabled ->
                            viewModel.updateSourceToggles(favoritesOnlyScreensaver = enabled)
                        },
                        onLaunchSlideshow = { isFullscreenSlideshowActive = true }
                    )

                    TvNavTab.STORAGE_SMB -> StorageAndSmbTab(
                        viewModel = viewModel,
                        prefs = prefs,
                        usbVolumes = usbVolumes.map { "${it.displayName} (${it.freeSpaceMb} MB free) — ${it.rootPath}" to it.rootPath },
                        cacheUsageMb = cacheUsageMb,
                        onRequestUsbSafPicker = onRequestUsbSafPicker,
                        onRequestAllFilesPermission = onRequestAllFilesPermission
                    )

                    TvNavTab.OVERLAY_STUDIO -> OverlayStudioTab(
                        prefs = prefs,
                        viewModel = viewModel
                    )

                    TvNavTab.TV_SETUP -> TvSetupGuideTab(
                        onOpenSystemDreamSettings = onOpenSystemDreamSettings,
                        onLaunchPreview = { isFullscreenSlideshowActive = true }
                    )

                    TvNavTab.ABOUT_NOSTR -> AboutMeNostrZapsTab()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryAndFiltersTab(
    prefs: UserPreferences,
    playlist: List<PhotoItem>,
    selectedGalleryIds: Set<String>,
    isRefreshing: Boolean,
    onSelectCategory: (WikimediaCategoryFilter?, String?, OrientationFilter?, ResolutionFloor?) -> Unit,
    onSelectDisplayMode: (PhotoDisplayMode) -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (PhotoItem) -> Unit,
    onDeleteSinglePhoto: (PhotoItem) -> Unit,
    onToggleGallerySelect: (String) -> Unit,
    onSelectAllGallery: () -> Unit,
    onClearGallerySelect: () -> Unit,
    onDeleteSelectedGallery: () -> Unit,
    onDeleteAllNonFavorites: () -> Unit,
    onLaunchSlideshow: () -> Unit
) {
    var customSearchText by remember(prefs.customSearchQuery) { mutableStateOf(prefs.customSearchQuery) }
    var isSelectionMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Row 1: Wikimedia Topic Presets
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(WikimediaCategoryFilter.entries) { filter ->
                val isSelected = prefs.activeCategory == filter
                TvActionButton(
                    label = if (isSelected) "✓ ${filter.displayName}" else filter.displayName,
                    accentColor = if (isSelected) TvFocusAccent else TvTextSecondary,
                    onClick = { onSelectCategory(filter, customSearchText, null, null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 2: Full-Screen Fit Mode, Resolution (4K / 2K / 1080p), and Aspect Ratio Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Display Mode (Full-Screen Fill vs Fit)
            items(PhotoDisplayMode.entries) { mode ->
                val selected = prefs.photoDisplayMode == mode
                TvActionButton(
                    label = if (selected) "🖥 ✓ ${mode.label}" else "🖥 ${mode.label}",
                    accentColor = if (selected) TvZapGold else TvTextSecondary,
                    onClick = { onSelectDisplayMode(mode) }
                )
            }

            // Resolution Floor (4K / 2K / 1080p / 720p)
            items(ResolutionFloor.entries) { floor ->
                val selected = prefs.resolutionFloor == floor
                TvActionButton(
                    label = if (selected) "📐 ✓ ${floor.label}" else "📐 ${floor.label}",
                    accentColor = if (selected) TvFocusAccent else TvTextSecondary,
                    onClick = { onSelectCategory(null, null, null, floor) }
                )
            }

            // Orientation / Widescreen Filter
            items(OrientationFilter.entries) { orientation ->
                val selected = prefs.orientationFilter == orientation
                TvActionButton(
                    label = if (selected) "↔ ✓ ${orientation.label}" else "↔ ${orientation.label}",
                    accentColor = if (selected) TvFocusAccent else TvTextSecondary,
                    onClick = { onSelectCategory(null, null, orientation, null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 3: Custom Search + Refresh + Gallery Delete Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customSearchText,
                onValueChange = { customSearchText = it },
                label = { Text("Free-Text Search (e.g., Dolomites 4K, Swiss Alps, Kingfisher)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TvFocusAccent,
                    unfocusedBorderColor = Color(0xFF475569)
                ),
                modifier = Modifier.weight(1f)
            )

            TvActionButton(
                label = "🔍 Search",
                accentColor = TvFocusAccent,
                onClick = {
                    onSelectCategory(WikimediaCategoryFilter.CUSTOM_QUERY, customSearchText, null, null)
                }
            )

            TvActionButton(
                label = if (isRefreshing) "Syncing…" else "⟳ Sync New",
                accentColor = TvFocusAccent,
                onClick = onRefresh
            )

            TvActionButton(
                label = if (isSelectionMode) "✓ Select Mode ON" else "☑ Select to Delete",
                accentColor = if (isSelectionMode) TvZapGold else TvTextSecondary,
                onClick = {
                    isSelectionMode = !isSelectionMode
                    if (!isSelectionMode) onClearGallerySelect()
                }
            )

            if (isSelectionMode) {
                TvActionButton(label = "Select All", onClick = onSelectAllGallery)
                if (selectedGalleryIds.isNotEmpty()) {
                    TvActionButton(
                        label = "🗑 Delete Selected (${selectedGalleryIds.size})",
                        accentColor = TvFavoriteCoral,
                        onClick = onDeleteSelectedGallery
                    )
                }
            }

            TvActionButton(
                label = "🗑 Clear Non-Favorites",
                accentColor = TvFavoriteCoral,
                onClick = onDeleteAllNonFavorites
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Downloaded / Active Photos Grid
        if (playlist.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No photos in playlist for this filter. Click '⟳ Sync New' or lower the resolution filter (e.g., from 4K to 2K/1080p).",
                    color = TvTextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlist, key = { it.id }) { photo ->
                    PhotoGridCard(
                        photo = photo,
                        isSelected = photo.id in selectedGalleryIds,
                        onPrimaryClick = {
                            if (isSelectionMode) {
                                onToggleGallerySelect(photo.id)
                            } else {
                                onLaunchSlideshow()
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(photo) },
                        onDeletePhoto = { onDeleteSinglePhoto(photo) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoritesTab(
    favorites: List<PhotoItem>,
    selectedIds: Set<String>,
    prefs: UserPreferences,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: (Boolean) -> Unit,
    onDeleteAll: (Boolean) -> Unit,
    onDeleteSinglePhoto: (PhotoItem) -> Unit,
    onToggleFavoritesOnlyScreensaver: (Boolean) -> Unit,
    onLaunchSlideshow: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header & Wrapped Action Buttons so nothing overflows off the right edge
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = "Protected Favorites (${favorites.size})",
                    color = TvTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Favorited photos are never auto-deleted by cache cleanup. Click any card to select, or use 🗑 on a card.",
                    color = TvTextSecondary,
                    fontSize = 12.sp
                )
            }

            TvActionButton(
                label = if (prefs.favoritesOnlyInScreensaver) "✓ Screensaver: Favorites Only" else "Screensaver: All Sources",
                accentColor = if (prefs.favoritesOnlyInScreensaver) TvFavoriteCoral else TvTextSecondary,
                onClick = { onToggleFavoritesOnlyScreensaver(!prefs.favoritesOnlyInScreensaver) }
            )

            if (favorites.isNotEmpty()) {
                TvActionButton(label = "Select All (${favorites.size})", onClick = onSelectAll)
                if (selectedIds.isNotEmpty()) {
                    TvActionButton(label = "Clear Selection", onClick = onClearSelection)
                    TvActionButton(
                        label = "♡ Unfavorite Selected (${selectedIds.size})",
                        accentColor = TvZapGold,
                        onClick = { onDeleteSelected(false) }
                    )
                    TvActionButton(
                        label = "🗑 Delete Selected (${selectedIds.size})",
                        accentColor = TvFavoriteCoral,
                        onClick = { onDeleteSelected(true) }
                    )
                }
                TvActionButton(
                    label = "🗑 Delete All Favorites",
                    accentColor = TvFavoriteCoral,
                    onClick = { onDeleteAll(true) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No Favorites saved yet. Press Center/OK on your TV remote during any slideshow or click ♥ on any photo in Gallery!",
                    color = TvTextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites, key = { it.id }) { photo ->
                    PhotoGridCard(
                        photo = photo,
                        isSelected = photo.id in selectedIds,
                        onPrimaryClick = { onToggleSelect(photo.id) },
                        onToggleFavorite = { onToggleSelect(photo.id) },
                        onDeletePhoto = { onDeleteSinglePhoto(photo) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageAndSmbTab(
    viewModel: MainViewModel,
    prefs: UserPreferences,
    usbVolumes: List<Pair<String, String>>,
    cacheUsageMb: Long,
    onRequestUsbSafPicker: () -> Unit,
    onRequestAllFilesPermission: () -> Unit
) {
    val savedCreds = remember { viewModel.getSavedSmbCredentials() }
    var smbHost by remember(prefs.smbHostIp) { mutableStateOf(prefs.smbHostIp) }
    var smbShare by remember(prefs.smbShareAndPath) { mutableStateOf(prefs.smbShareAndPath) }
    var smbAuthMode by remember(prefs.smbAuthMode) { mutableStateOf(prefs.smbAuthMode) }
    var smbDomain by remember { mutableStateOf(savedCreds.domain) }
    var smbUser by remember { mutableStateOf(savedCreds.username) }
    var smbPassword by remember { mutableStateOf(savedCreds.password) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: External USB Storage Expansion & Hot-Plug Safety
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(TvSurfaceCard, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "1. External USB Storage & Cache Quota",
                color = TvFocusAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Hot-Plug Protected: Unplugging a USB drive falls back to internal TV storage automatically.\n• QR Code Auto-Hide: QR codes automatically hide on USB and SMB photos.",
                color = TvTextSecondary,
                fontSize = 12.sp
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvActionButton(
                    label = if (prefs.enableUsbSource) "✓ USB Source Enabled" else "Enable USB Source",
                    accentColor = if (prefs.enableUsbSource) TvFocusAccent else TvTextSecondary,
                    onClick = { viewModel.updateSourceToggles(enableUsb = !prefs.enableUsbSource) }
                )
                TvActionButton(
                    label = "Pick USB Folder (SAF)",
                    onClick = onRequestUsbSafPicker
                )
                if (!viewModel.hasAllFilesAccess()) {
                    TvActionButton(
                        label = "Grant All-Files Access",
                        accentColor = TvZapGold,
                        onClick = onRequestAllFilesPermission
                    )
                }
            }

            Text(
                text = "Detected USB Volumes:",
                color = TvTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (usbVolumes.isEmpty()) {
                Text(
                    text = "No external USB drive currently mounted. Using internal TV flash storage.",
                    color = TvTextSecondary,
                    fontSize = 12.sp
                )
            } else {
                usbVolumes.forEach { (label, path) ->
                    TvActionButton(
                        label = if (prefs.usbDirectoryPath == path) "✓ $label" else label,
                        accentColor = if (prefs.usbDirectoryPath == path) TvFocusAccent else TvTextPrimary,
                        onClick = {
                            viewModel.selectUsbVolumePath(path, prefs.useUsbForImageCache, prefs.cacheQuotaMb)
                        }
                    )
                }
            }

            Text(
                text = "Smart LRU Cache: $cacheUsageMb MB / ${prefs.cacheQuotaMb} MB Quota (Favorites Excluded)",
                color = TvTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(512, 1024, 2048, 4096, 8192).forEach { quota ->
                    val selected = prefs.cacheQuotaMb == quota
                    TvActionButton(
                        label = "${quota}MB",
                        accentColor = if (selected) TvFocusAccent else TvTextSecondary,
                        onClick = { viewModel.updateCacheQuota(quota, prefs.useUsbForImageCache) }
                    )
                }
            }
        }

        // Right Column: SMB / NAS Local Network Sync (jcifs-ng)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(TvSurfaceCard, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "2. Local Network Share (SMBv2 / SMBv3 NAS Sync)",
                color = TvFocusAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmbAuthMode.entries.forEach { mode ->
                    val selected = smbAuthMode == mode
                    TvActionButton(
                        label = if (selected) "✓ ${mode.label}" else mode.label,
                        accentColor = if (selected) TvFocusAccent else TvTextSecondary,
                        onClick = { smbAuthMode = mode }
                    )
                }
            }

            OutlinedTextField(
                value = smbHost,
                onValueChange = { smbHost = it },
                label = { Text("NAS / PC Host IP (e.g., 192.168.1.50)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = smbShare,
                onValueChange = { smbShare = it },
                label = { Text("Share Name & Folder Path (e.g., Photos/TVWallpapers)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (smbAuthMode == SmbAuthMode.USER_PASSWORD) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = smbDomain,
                        onValueChange = { smbDomain = it },
                        label = { Text("Domain (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f)
                    )
                    OutlinedTextField(
                        value = smbUser,
                        onValueChange = { smbUser = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }
                OutlinedTextField(
                    value = smbPassword,
                    onValueChange = { smbPassword = it },
                    label = { Text("Password (EncryptedSharedPreferences)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvActionButton(
                    label = "Connect & Sync SMB Share",
                    accentColor = TvFocusAccent,
                    onClick = {
                        viewModel.saveAndTestSmbShare(
                            hostIp = smbHost,
                            shareAndPath = smbShare,
                            authMode = smbAuthMode,
                            domain = smbDomain,
                            username = smbUser,
                            password = smbPassword
                        )
                    }
                )
                TvActionButton(
                    label = if (prefs.enableSmbSource) "✓ SMB Source Active" else "SMB Disabled",
                    accentColor = if (prefs.enableSmbSource) TvZapGold else TvTextSecondary,
                    onClick = { viewModel.updateSourceToggles(enableSmb = !prefs.enableSmbSource) }
                )
            }
        }
    }
}

@Composable
private fun OverlayStudioTab(
    prefs: UserPreferences,
    viewModel: MainViewModel
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left: Granular Controls for UI Scale, Positioning, Typography, QR & Motion
        Column(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .background(TvSurfaceCard, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "App Menu / UI Scale (Shrink or Enlarge Screen Menus & Text)",
                color = TvZapGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UiScaleOption.entries) { scaleOpt ->
                    TvActionButton(
                        label = if (prefs.uiScale == scaleOpt) "✓ ${scaleOpt.label}" else scaleOpt.label,
                        accentColor = if (prefs.uiScale == scaleOpt) TvZapGold else TvTextSecondary,
                        onClick = { viewModel.updateUiScale(scaleOpt) }
                    )
                }
            }

            Text(
                text = "Independent Anchor Positioning",
                color = TvFocusAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text("Text Anchor (Photographer & Location):", color = TvTextSecondary, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OverlayAnchor.entries) { anchor ->
                    TvActionButton(
                        label = if (prefs.textAnchor == anchor) "✓ ${anchor.label}" else anchor.label,
                        accentColor = if (prefs.textAnchor == anchor) TvFocusAccent else TvTextSecondary,
                        onClick = { viewModel.updateAnchors(anchor, prefs.qrAnchor) }
                    )
                }
            }

            Text("QR Code Anchor (Wikimedia only; auto-hidden on USB/SMB):", color = TvTextSecondary, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OverlayAnchor.entries) { anchor ->
                    TvActionButton(
                        label = if (prefs.qrAnchor == anchor) "✓ ${anchor.label}" else anchor.label,
                        accentColor = if (prefs.qrAnchor == anchor) TvFocusAccent else TvTextSecondary,
                        onClick = { viewModel.updateAnchors(prefs.textAnchor, anchor) }
                    )
                }
            }

            Text(
                text = "Overlay Typography, Scale & Contrast",
                color = TvFocusAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FontFamilyOption.entries) { font ->
                    TvActionButton(
                        label = if (prefs.fontFamily == font) "✓ ${font.label}" else font.label,
                        accentColor = if (prefs.fontFamily == font) TvFocusAccent else TvTextSecondary,
                        onClick = {
                            viewModel.updateTypography(font, prefs.textScale, prefs.textColor, prefs.textContrastMode)
                        }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TextScaleOption.entries) { scale ->
                    TvActionButton(
                        label = if (prefs.textScale == scale) "✓ ${scale.label}" else scale.label,
                        accentColor = if (prefs.textScale == scale) TvFocusAccent else TvTextSecondary,
                        onClick = {
                            viewModel.updateTypography(prefs.fontFamily, scale, prefs.textColor, prefs.textContrastMode)
                        }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TextColorPreset.entries) { colorPreset ->
                    TvActionButton(
                        label = if (prefs.textColor == colorPreset) "✓ ${colorPreset.label}" else colorPreset.label,
                        accentColor = if (prefs.textColor == colorPreset) TvFocusAccent else TvTextSecondary,
                        onClick = {
                            viewModel.updateTypography(prefs.fontFamily, prefs.textScale, colorPreset, prefs.textContrastMode)
                        }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TextContrastMode.entries) { mode ->
                    TvActionButton(
                        label = if (prefs.textContrastMode == mode) "✓ ${mode.label}" else mode.label,
                        accentColor = if (prefs.textContrastMode == mode) TvFocusAccent else TvTextSecondary,
                        onClick = {
                            viewModel.updateTypography(prefs.fontFamily, prefs.textScale, prefs.textColor, mode)
                        }
                    )
                }
            }

            Text(
                text = "QR Theme, Visibility & Slideshow Motion",
                color = TvFocusAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvActionButton(
                    label = if (prefs.showLocation) "✓ Show Location" else "Hide Location",
                    accentColor = if (prefs.showLocation) TvFocusAccent else TvTextSecondary,
                    onClick = {
                        viewModel.updateQrAndVisibility(
                            showPhotographer = prefs.showPhotographerCredit,
                            showLocation = !prefs.showLocation,
                            showQrOnWikimedia = prefs.showQrCodeOnWikimedia,
                            qrColorTheme = prefs.qrColorTheme,
                            qrSizeDp = prefs.qrSizeDp,
                            qrQuietZone = prefs.qrQuietZoneModules,
                            edgePaddingDp = prefs.overlayEdgePaddingDp
                        )
                    }
                )
                TvActionButton(
                    label = if (prefs.showPhotographerCredit) "✓ Show Photographer" else "Hide Photographer",
                    accentColor = if (prefs.showPhotographerCredit) TvFocusAccent else TvTextSecondary,
                    onClick = {
                        viewModel.updateQrAndVisibility(
                            showPhotographer = !prefs.showPhotographerCredit,
                            showLocation = prefs.showLocation,
                            showQrOnWikimedia = prefs.showQrCodeOnWikimedia,
                            qrColorTheme = prefs.qrColorTheme,
                            qrSizeDp = prefs.qrSizeDp,
                            qrQuietZone = prefs.qrQuietZoneModules,
                            edgePaddingDp = prefs.overlayEdgePaddingDp
                        )
                    }
                )
                TvActionButton(
                    label = if (prefs.showQrCodeOnWikimedia) "✓ QR on Wikimedia" else "QR Disabled",
                    accentColor = if (prefs.showQrCodeOnWikimedia) TvFocusAccent else TvTextSecondary,
                    onClick = {
                        viewModel.updateQrAndVisibility(
                            showPhotographer = prefs.showPhotographerCredit,
                            showLocation = prefs.showLocation,
                            showQrOnWikimedia = !prefs.showQrCodeOnWikimedia,
                            qrColorTheme = prefs.qrColorTheme,
                            qrSizeDp = prefs.qrSizeDp,
                            qrQuietZone = prefs.qrQuietZoneModules,
                            edgePaddingDp = prefs.overlayEdgePaddingDp
                        )
                    }
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QrColorTheme.entries) { theme ->
                    TvActionButton(
                        label = if (prefs.qrColorTheme == theme) "✓ ${theme.label}" else theme.label,
                        accentColor = if (prefs.qrColorTheme == theme) TvFocusAccent else TvTextSecondary,
                        onClick = {
                            viewModel.updateQrAndVisibility(
                                showPhotographer = prefs.showPhotographerCredit,
                                showLocation = prefs.showLocation,
                                showQrOnWikimedia = prefs.showQrCodeOnWikimedia,
                                qrColorTheme = theme,
                                qrSizeDp = prefs.qrSizeDp,
                                qrQuietZone = prefs.qrQuietZoneModules,
                                edgePaddingDp = prefs.overlayEdgePaddingDp
                            )
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 30, 60, 300).forEach { sec ->
                    val label = if (sec >= 60) "${sec / 60}m Interval" else "${sec}s Interval"
                    TvActionButton(
                        label = if (prefs.slideIntervalSeconds == sec) "✓ $label" else label,
                        accentColor = if (prefs.slideIntervalSeconds == sec) TvFocusAccent else TvTextSecondary,
                        onClick = {
                            viewModel.updateSlideshowMotion(
                                intervalSeconds = sec,
                                enableKenBurns = prefs.enableKenBurnsEffect,
                                enableAmbientBlur = prefs.enableAmbientBlurBackdrop,
                                enableOledShift = prefs.enableOledBurnInShift
                            )
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvActionButton(
                    label = if (prefs.enableKenBurnsEffect) "✓ Ken Burns Pan/Zoom" else "Ken Burns Off",
                    accentColor = if (prefs.enableKenBurnsEffect) TvFocusAccent else TvTextSecondary,
                    onClick = {
                        viewModel.updateSlideshowMotion(
                            intervalSeconds = prefs.slideIntervalSeconds,
                            enableKenBurns = !prefs.enableKenBurnsEffect,
                            enableAmbientBlur = prefs.enableAmbientBlurBackdrop,
                            enableOledShift = prefs.enableOledBurnInShift
                        )
                    }
                )
                TvActionButton(
                    label = if (prefs.enableOledBurnInShift) "✓ OLED Burn-In Shift" else "OLED Shift Off",
                    accentColor = if (prefs.enableOledBurnInShift) TvFocusAccent else TvTextSecondary,
                    onClick = {
                        viewModel.updateSlideshowMotion(
                            intervalSeconds = prefs.slideIntervalSeconds,
                            enableKenBurns = prefs.enableKenBurnsEffect,
                            enableAmbientBlur = prefs.enableAmbientBlurBackdrop,
                            enableOledShift = !prefs.enableOledBurnInShift
                        )
                    }
                )
            }
        }

        // Right: Live Interactive 16:9 TV Screen Simulator
        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .background(TvSurfaceCard, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Live 16:9 Overlay Anchor Preview",
                color = TvTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Text: ${prefs.textAnchor.label}  • QR: ${prefs.qrAnchor.label}\n• Fit: ${prefs.photoDisplayMode.label}",
                color = TvTextSecondary,
                fontSize = 12.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, TvFocusAccent, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Click Back to wake up TV",
                    color = TvFocusAccent,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color(0xCC090D16), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )

                Text(
                    text = "📍 Matterhorn, Zermatt, Switzerland\n📷 Wikimedia Photographer",
                    color = Color(prefs.textColor.argbHex),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(
                            when (prefs.textAnchor) {
                                OverlayAnchor.TOP_LEFT -> Alignment.TopStart
                                OverlayAnchor.TOP_RIGHT -> Alignment.TopEnd
                                OverlayAnchor.BOTTOM_LEFT -> Alignment.BottomStart
                                OverlayAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
                                OverlayAnchor.CENTER_BOTTOM -> Alignment.BottomCenter
                            }
                        )
                        .background(Color(0xAA090D16), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                )

                if (prefs.showQrCodeOnWikimedia) {
                    Box(
                        modifier = Modifier
                            .align(
                                when (prefs.qrAnchor) {
                                    OverlayAnchor.TOP_LEFT -> Alignment.TopStart
                                    OverlayAnchor.TOP_RIGHT -> Alignment.TopEnd
                                    OverlayAnchor.BOTTOM_LEFT -> Alignment.BottomStart
                                    OverlayAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
                                    OverlayAnchor.CENTER_BOTTOM -> Alignment.BottomCenter
                                }
                            )
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .padding(5.dp)
                    ) {
                        Text("QR", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Scrollable Setup & Remote Control Guide for Android TV 12.
 */
@Composable
private fun TvSetupGuideTab(
    onOpenSystemDreamSettings: () -> Unit,
    onLaunchPreview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(TvSurfaceCard, RoundedCornerShape(14.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Android TV 12 Screensaver Setup",
                color = TvFocusAccent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1. Click 'Open Android TV Screensaver Settings' below (or go to Android TV Settings → System → Ambient mode / Screensaver).\n" +
                    "2. Select 'TVFrame Photo Screensaver' as your active screensaver.\n" +
                    "3. Set 'When to start' to 5, 10, or 15 minutes of inactivity.\n" +
                    "4. Note for Android TV Retail/Ambient Mode: You can also bind TVFrame directly via ADB (`adb shell settings put secure screensaver_components com.nostr.tvframe/.service.PhotoDreamService`).",
                color = TvTextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(
                    label = "⚙ Open Android TV Screensaver Settings",
                    accentColor = TvFocusAccent,
                    onClick = onOpenSystemDreamSettings
                )
                TvActionButton(
                    label = "▶ Test Interactive Screensaver Now",
                    accentColor = TvZapGold,
                    onClick = onLaunchPreview
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(TvSurfaceCard, RoundedCornerShape(14.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Interactive Remote Controls During Screensaver",
                color = TvZapGold,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "While the screensaver or slideshow is running, your TV remote stays interactive:\n\n" +
                    "• ◀ / ▶ (D-Pad Left / Right): Skip to Previous or Next photo immediately.\n" +
                    "• ● / ▲ (D-Pad Center / Up): Add or remove the current photo from Favorites ♥.\n" +
                    "• ▼ (D-Pad Down / Info): Toggle the Photographer/Location & QR overlay on or off.\n" +
                    "• ↩ BACK Button: Wakes up the TV / exits the screensaver immediately.\n\n" +
                    "Whenever you press a D-Pad button to skip photos or favorite an item, an on-screen reminder ('Click Back to wake up TV') appears at the top of the screen.",
                color = TvTextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * About Me & Nostr Zaps Screen featuring Primal (`primal.net/s`), full NPUB,
 * Lightning Address (`s@primal.net`), and Version/Build info, all fitting cleanly without clipping.
 */
@Composable
private fun AboutMeNostrZapsTab() {
    val npub = stringResource(R.string.nostr_npub)
    val primalUrl = stringResource(R.string.nostr_primal_url)
    val primalDisplay = stringResource(R.string.nostr_primal_display)
    val lightningAddress = stringResource(R.string.nostr_lightning_address)

    val primalQrBitmap = remember(primalUrl) {
        QrCodeGenerator.generateQrBitmap(
            content = primalUrl,
            sizePx = 360,
            quietZoneModules = 2,
            foregroundColorArgb = 0xFFFFFFFF.toInt(),
            backgroundColorArgb = 0xFF0F172A.toInt()
        )
    }

    val zapQrBitmap = remember(lightningAddress) {
        QrCodeGenerator.generateQrBitmap(
            content = "lightning:$lightningAddress",
            sizePx = 360,
            quietZoneModules = 2,
            foregroundColorArgb = 0xFFFDE047.toInt(),
            backgroundColorArgb = 0xFF0F172A.toInt()
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TvSurfaceCard, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Bio, Primal Profile, NPUB, and Version Info (scrollable so nothing is ever clipped)
        Column(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "⚡ Enjoying TVFrame? Send a Zap on Nostr!",
                    color = TvZapGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = "TVFrame is built for big-screen photography lovers. Follow me on Nostr via Primal or send a Lightning Zap if you enjoy the app!",
                color = TvTextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D16), RoundedCornerShape(10.dp))
                    .border(1.dp, TvFocusAccent, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🌐 Nostr Profile on Primal:  $primalDisplay  ($primalUrl)",
                    color = TvFocusAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "⚡ Lightning Zap Address:  $lightningAddress",
                    color = TvZapGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "🔑 Nostr Public Key (NPUB):",
                    color = TvTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = npub,
                    color = TvTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            // Version & Technical Build Metadata Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ℹ Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    color = TvTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Package: ${BuildConfig.APPLICATION_ID} • Android TV 12 (API 31+)",
                    color = TvTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Right Column: Side-by-Side QR Codes (Primal Profile + Instant Lightning Zap)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (primalQrBitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
                        .border(2.dp, TvFocusAccent, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = primalQrBitmap,
                        contentDescription = "Scan for Primal Profile",
                        modifier = Modifier.size(140.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Follow on Primal",
                        color = TvFocusAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = primalDisplay,
                        color = TvTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (zapQrBitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
                        .border(2.dp, TvZapGold, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = zapQrBitmap,
                        contentDescription = "Scan to Send Lightning Zap",
                        modifier = Modifier.size(140.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚡ Send a Zap",
                        color = TvZapGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lightningAddress,
                        color = TvTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Photo Grid Card used in both Gallery & Filters and Favorites.
 * Includes direct on-card buttons for Favorite (♥) and Delete (🗑), plus resolution badge.
 */
@Composable
private fun PhotoGridCard(
    photo: PhotoItem,
    isSelected: Boolean,
    onPrimaryClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeletePhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .tvFocusableCard(cornerRadiusDp = 10)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPrimaryClick() }
    ) {
        AsyncImage(
            model = photo.resolveDisplayModel(),
            contentDescription = photo.locationName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top badge row showing Source/Resolution + Favorite (♥) + Delete (🗑)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val resLabel = if (photo.width >= 3840) "4K" else if (photo.width >= 2560) "2K" else "HD"
            val badgeText = when (photo.sourceType) {
                PhotoSourceType.WIKIMEDIA -> "WIKI • $resLabel"
                PhotoSourceType.USB -> "USB"
                PhotoSourceType.SMB -> "SMB"
            }
            Text(
                text = if (isSelected) "✓ SELECTED" else badgeText,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        if (isSelected) TvFocusAccent else Color(0xCC090D16),
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (photo.isFavorite) "♥" else "♡",
                    color = if (photo.isFavorite) TvFavoriteCoral else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xCC090D16), RoundedCornerShape(6.dp))
                        .clickable { onToggleFavorite() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = "🗑",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0xCC7F1D1D), RoundedCornerShape(6.dp))
                        .clickable { onDeletePhoto() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Bottom caption showing Location + Photographer only
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0xCC090D16))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = photo.locationName.ifBlank { "Wikimedia Commons" },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = photo.photographerName,
                color = TvTextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvActionButton(
    label: String,
    accentColor: Color = TvTextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .tvFocusableCard(cornerRadiusDp = 8)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
