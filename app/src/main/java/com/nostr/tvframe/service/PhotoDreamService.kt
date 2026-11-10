package com.nostr.tvframe.service

import android.service.dreams.DreamService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nostr.tvframe.TVFrameApp
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.ui.slideshow.SlideshowRenderer
import com.nostr.tvframe.ui.theme.TVFrameTheme
import kotlinx.coroutines.launch

/**
 * Native Android TV Idle Screensaver (`DreamService`) implementation.
 *
 * Configured with `isInteractive = true` so users can press D-Pad Left/Right to skip photos
 * or D-Pad Center to mark Favorites while seeing the `"Click Back to wake up TV"` HUD prompt,
 * and press Back to call `finish()` and wake up the TV immediately.
 */
class PhotoDreamService :
    DreamService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Allow D-Pad skipping & favoriting; Back button wakes up the TV
        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        val app = TVFrameApp.instance

        val composeView = ComposeView(this).apply {
            // The dream window must give the Compose host view focus, otherwise D-pad keys
            // never reach the slideshow's key handler.
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(this@PhotoDreamService)
            setViewTreeViewModelStoreOwner(this@PhotoDreamService)
            setViewTreeSavedStateRegistryOwner(this@PhotoDreamService)

            setContent {
                val prefs by app.preferencesRepository.userPreferencesFlow
                    .collectAsState(initial = UserPreferences())
                val playlist by app.photoRepository.screensaverPlaylistFlow
                    .collectAsState(initial = emptyList())
                val usbBanner by app.usbStorageDataSource.statusBannerMessage
                    .collectAsState()

                TVFrameTheme {
                    SlideshowRenderer(
                        photos = playlist,
                        preferences = prefs,
                        isDreamServiceMode = true,
                        usbStatusBanner = usbBanner,
                        onToggleFavorite = { photo ->
                            app.applicationScope.launch {
                                app.photoRepository.toggleFavorite(photo.id)
                            }
                        },
                        onPhotoDisplayed = { photo ->
                            app.applicationScope.launch {
                                app.photoRepository.markPhotoViewed(photo.id)
                            }
                        },
                        onExitOrWakeTv = {
                            // Wakes up the TV when the user clicks Back
                            finish()
                        }
                    )
                }
            }
        }

        setContentView(composeView)
        composeView.requestFocus()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        TVFrameApp.instance.applicationScope.launch {
            TVFrameApp.instance.photoRepository.refreshEnabledSources()
        }
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDetachedFromWindow()
    }
}
