package com.nostr.tvframe.ui.slideshow

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.nostr.tvframe.domain.model.OverlayAnchor
import com.nostr.tvframe.domain.model.PhotoDisplayMode
import com.nostr.tvframe.domain.model.PhotoItem
import com.nostr.tvframe.domain.model.PhotoSourceType
import com.nostr.tvframe.domain.model.TextContrastMode
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.ui.theme.TvFocusAccent
import com.nostr.tvframe.ui.theme.TvZapGold
import com.nostr.tvframe.ui.theme.toComposeFontFamily
import com.nostr.tvframe.util.DisplaySize
import com.nostr.tvframe.util.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The slideshow's play order: a snapshot of photo ids captured when the slideshow starts.
 *
 * Later playlist emissions never reorder what is already queued: ids that disappear are skipped,
 * newly-arrived ids are appended at the end. Favoriting a photo, the per-slide "last viewed"
 * update, or a background refresh therefore can't make the show skip, repeat, or jump.
 */
private class SlideOrder {
    /** Every id ever seen, in play order (kept so we can find a deleted photo's successor). */
    private val allIds = ArrayList<String>()
    private val known = HashSet<String>()

    fun reconcile(incoming: List<String>): List<String> {
        for (id in incoming) {
            if (known.add(id)) allIds.add(id)
        }
        val present = incoming.toHashSet()
        return allIds.filter { it in present }
    }

    /** Photo to show when [missingId] was removed: the next surviving photo after it. */
    fun successorOf(missingId: String?, live: List<String>): String? {
        if (live.isEmpty()) return null
        val start = if (missingId == null) -1 else allIds.indexOf(missingId)
        if (start < 0) return live.first()
        val liveSet = live.toHashSet()
        for (i in 1..allIds.size) {
            val candidate = allIds[(start + i) % allIds.size]
            if (candidate in liveSet) return candidate
        }
        return live.first()
    }
}

/** Crossfade target whose identity is the photo id only (ignores favorite/last-viewed changes). */
private class SlideFrame(val photo: PhotoItem) {
    override fun equals(other: Any?): Boolean = other is SlideFrame && other.photo.id == photo.id
    override fun hashCode(): Int = photo.id.hashCode()
}

/**
 * Shared Fullscreen Slideshow & Android TV DreamService Renderer.
 *
 * Defaults to [PhotoDisplayMode.FULLSCREEN_CROP] (`ContentScale.Crop`) so photos fill 100%
 * of the 16:9 TV screen edge-to-edge with zero side margins.
 *
 * The current photo is tracked by id against a stable [SlideOrder] snapshot, never by list index.
 */
@Composable
fun SlideshowRenderer(
    photos: List<PhotoItem>,
    preferences: UserPreferences,
    isDreamServiceMode: Boolean,
    usbStatusBanner: String? = null,
    onToggleFavorite: (PhotoItem) -> Unit,
    onPhotoDisplayed: (PhotoItem) -> Unit,
    onExitOrWakeTv: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var overlayTemporarilyHidden by remember { mutableStateOf(false) }

    var dpadFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var dpadTriggerTick by remember { mutableIntStateOf(0) }

    var burnInOffsetX by remember { mutableIntStateOf(0) }
    var burnInOffsetY by remember { mutableIntStateOf(0) }

    // ---- Stable play order, tracked by photo id ----
    val slideOrder = remember { SlideOrder() }
    val order = remember(photos) { slideOrder.reconcile(photos.map { it.id }) }
    val photosById = remember(photos) { photos.associateBy { it.id } }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = selectedId
    val currentId: String? = when {
        order.isEmpty() -> null
        selected != null && photosById.containsKey(selected) -> selected
        else -> slideOrder.successorOf(selected, order) // first start, or current photo was deleted
    }
    val currentPhoto: PhotoItem? = currentId?.let { photosById[it] }

    val latestOrder by rememberUpdatedState(order)
    val latestCurrentId by rememberUpdatedState(currentId)
    val latestOnPhotoDisplayed by rememberUpdatedState(onPhotoDisplayed)
    val latestOnExit by rememberUpdatedState(onExitOrWakeTv)

    fun step(delta: Int) {
        val ids = latestOrder
        if (ids.isEmpty()) return
        val pos = ids.indexOf(latestCurrentId).coerceAtLeast(0)
        selectedId = ids[Math.floorMod(pos + delta, ids.size)]
    }

    // Back exits the in-app slideshow even if the key never reaches our focus target.
    // (In the screensaver, DreamService itself turns Back into wakeUp()/finish().)
    if (!isDreamServiceMode) {
        BackHandler { latestOnExit() }
    }

    // Re-request focus whenever we switch between the empty state and real content, so the
    // D-pad keeps working when photos arrive after the slideshow/screensaver has started.
    LaunchedEffect(currentPhoto != null) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(currentId, preferences.slideIntervalSeconds) {
        val photo = currentId?.let { photosById[it] } ?: return@LaunchedEffect
        latestOnPhotoDisplayed(photo)
        val intervalMs = (preferences.slideIntervalSeconds.coerceAtLeast(5)) * 1000L
        while (true) {
            delay(intervalMs)
            step(1)
        }
    }

    LaunchedEffect(preferences.enableOledBurnInShift) {
        if (!preferences.enableOledBurnInShift) {
            burnInOffsetX = 0
            burnInOffsetY = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(120_000L)
            burnInOffsetX = Random.nextInt(-9, 10)
            burnInOffsetY = Random.nextInt(-9, 10)
        }
    }

    LaunchedEffect(dpadTriggerTick) {
        if (dpadFeedbackMessage != null) {
            delay(4500L)
            dpadFeedbackMessage = null
        }
    }

    // Decode at most screen size (4K sources would otherwise be decoded at full resolution).
    val (screenW, screenH) = remember(context) { DisplaySize.screenPx(context) }

    // The focusable, key-handling container ALWAYS exists (empty state included).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onKeyEvent false
                }
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        latestOnExit()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (currentPhoto != null) {
                            step(1)
                            dpadFeedbackMessage = "Next Photo ⏭  •  Click Back to wake up TV"
                            dpadTriggerTick++
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (currentPhoto != null) {
                            step(-1)
                            dpadFeedbackMessage = "Previous Photo ⏮  •  Click Back to wake up TV"
                            dpadTriggerTick++
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val photo = currentPhoto
                        if (photo != null) {
                            val willBeFav = !photo.isFavorite
                            onToggleFavorite(photo)
                            val favText = if (willBeFav) "Added to Favorites ♥" else "Removed from Favorites ♡"
                            dpadFeedbackMessage = "$favText  •  Click Back to wake up TV"
                            dpadTriggerTick++
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_INFO -> {
                        overlayTemporarilyHidden = !overlayTemporarilyHidden
                        val stateLabel = if (overlayTemporarilyHidden) "Overlay Hidden" else "Overlay Shown"
                        dpadFeedbackMessage = "$stateLabel  •  Click Back to wake up TV"
                        dpadTriggerTick++
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (currentPhoto == null) {
            Text(
                text = "No photos match current filter. Press Back to adjust filters or click Refresh.",
                color = Color.White,
                fontSize = 18.sp
            )
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "kenBurnsTransition")
            val kenBurnsScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (preferences.enableKenBurnsEffect) 1.08f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (preferences.slideIntervalSeconds * 1000).coerceAtLeast(10_000),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "kenBurnsScale"
            )
            val kenBurnsTranslationX by infiniteTransition.animateFloat(
                initialValue = -12f,
                targetValue = if (preferences.enableKenBurnsEffect) 12f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (preferences.slideIntervalSeconds * 1000).coerceAtLeast(10_000),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "kenBurnsPanX"
            )

            // Keyed by id: a favorite toggle (same photo, new data) must not re-trigger the crossfade.
            Crossfade(
                targetState = SlideFrame(currentPhoto),
                animationSpec = tween(durationMillis = 1200),
                label = "photoCrossfade"
            ) { frame ->
                val photo = frame.photo
                val imageRequest = remember(photo.id) {
                    ImageRequest.Builder(context)
                        .data(photo.resolveDisplayModel())
                        .size(screenW, screenH)
                        .precision(Precision.INEXACT)
                        .allowHardware(true)
                        .crossfade(true)
                        .build()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Optional Blurred Backdrop when user explicitly chooses FIT_WITH_BLUR.
                    // It is blurred anyway, so a tiny decode is visually identical and saves a
                    // second full-resolution bitmap.
                    if (preferences.photoDisplayMode == PhotoDisplayMode.FIT_WITH_BLUR) {
                        val backdropRequest = remember(photo.id) {
                            ImageRequest.Builder(context)
                                .data(photo.resolveDisplayModel())
                                .size(BACKDROP_DECODE_PX)
                                .precision(Precision.INEXACT)
                                .memoryCacheKey("${photo.id}#backdrop$BACKDROP_DECODE_PX")
                                .allowHardware(true)
                                .build()
                        }
                        AsyncImage(
                            model = backdropRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(32.dp)
                                .graphicsLayer { alpha = 0.45f }
                        )
                    }

                    // Foreground Photo: FULLSCREEN_CROP fills 100% of the TV screen edge-to-edge with zero margins
                    val foregroundScale = when (preferences.photoDisplayMode) {
                        PhotoDisplayMode.FULLSCREEN_CROP -> ContentScale.Crop
                        PhotoDisplayMode.FIT_WITH_BLUR,
                        PhotoDisplayMode.FIT_BLACK_BARS -> ContentScale.Fit
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = photo.locationName,
                        contentScale = foregroundScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = kenBurnsScale
                                scaleY = kenBurnsScale
                                translationX = kenBurnsTranslationX
                            }
                    )
                }
            }

            // Metadata & QR Code Overlays
            if (!overlayTemporarilyHidden) {
                val edgePadding = preferences.overlayEdgePaddingDp.dp
                val showTextBlock = preferences.showPhotographerCredit || preferences.showLocation
                val showQrBlock = currentPhoto.shouldRenderQrCode(preferences)

                if (showTextBlock && showQrBlock && preferences.textAnchor == preferences.qrAnchor) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(edgePadding)
                            .offset { IntOffset(burnInOffsetX, burnInOffsetY) },
                        contentAlignment = preferences.textAnchor.toComposeAlignment()
                    ) {
                        Column(
                            horizontalAlignment = preferences.textAnchor.toHorizontalAlignment(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QrOverlayBlock(photo = currentPhoto, preferences = preferences)
                            MetadataTextOverlayBlock(photo = currentPhoto, preferences = preferences)
                        }
                    }
                } else {
                    if (showTextBlock) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(edgePadding)
                                .offset { IntOffset(burnInOffsetX, burnInOffsetY) },
                            contentAlignment = preferences.textAnchor.toComposeAlignment()
                        ) {
                            MetadataTextOverlayBlock(photo = currentPhoto, preferences = preferences)
                        }
                    }

                    if (showQrBlock) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(edgePadding)
                                .offset { IntOffset(-burnInOffsetX, burnInOffsetY) },
                            contentAlignment = preferences.qrAnchor.toComposeAlignment()
                        ) {
                            QrOverlayBlock(photo = currentPhoto, preferences = preferences)
                        }
                    }
                }
            }

        }

        // D-Pad Interaction HUD Banner ("Click Back to wake up TV")
        AnimatedVisibility(
            visible = dpadFeedbackMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xDD0F172A), RoundedCornerShape(999.dp))
                    .border(1.5.dp, TvFocusAccent, RoundedCornerShape(999.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = dpadFeedbackMessage.orEmpty(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // USB Hot-Plug Fallback Notification Banner
        AnimatedVisibility(
            visible = !usbStatusBanner.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xEE1E293B), RoundedCornerShape(12.dp))
                    .border(1.dp, TvZapGold, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = usbStatusBanner.orEmpty(),
                    color = TvZapGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private const val BACKDROP_DECODE_PX = 160

@Composable
private fun MetadataTextOverlayBlock(
    photo: PhotoItem,
    preferences: UserPreferences
) {
    val fontFamily = preferences.fontFamily.toComposeFontFamily()
    val textColor = Color(preferences.textColor.argbHex)
    val useCard = preferences.textContrastMode == TextContrastMode.SEMI_TRANSPARENT_CARD ||
        preferences.textContrastMode == TextContrastMode.CARD_AND_SHADOW
    val useShadow = preferences.textContrastMode == TextContrastMode.DROP_SHADOW_ONLY ||
        preferences.textContrastMode == TextContrastMode.CARD_AND_SHADOW

    val textShadow = if (useShadow) {
        Shadow(
            color = Color.Black.copy(alpha = 0.9f),
            offset = Offset(2f, 2f),
            blurRadius = 6f
        )
    } else {
        null
    }

    val containerModifier = if (useCard) {
        Modifier
            .widthIn(max = 520.dp)
            .background(Color(0xAA090D16), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    } else {
        Modifier
            .widthIn(max = 520.dp)
            .padding(4.dp)
    }

    Column(modifier = containerModifier) {
        if (preferences.showLocation && photo.locationName.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📍 ${photo.locationName}",
                    color = textColor,
                    fontFamily = fontFamily,
                    fontSize = preferences.textScale.primarySp.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = textShadow)
                )
                if (photo.isFavorite) {
                    Text(
                        text = "♥",
                        color = Color(0xFFFB7185),
                        fontSize = preferences.textScale.primarySp.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (preferences.showPhotographerCredit && photo.photographerName.isNotBlank()) {
            if (preferences.showLocation && photo.locationName.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
            }
            val prefix = if (photo.sourceType == PhotoSourceType.WIKIMEDIA) "📷 " else "🗂 "
            Text(
                text = "$prefix${photo.photographerName}",
                color = textColor.copy(alpha = 0.90f),
                fontFamily = fontFamily,
                fontSize = preferences.textScale.secondarySp.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = textShadow)
            )
        }
    }
}

@Composable
private fun QrOverlayBlock(
    photo: PhotoItem,
    preferences: UserPreferences
) {
    val url = photo.commonsPageUrl ?: return
    val qrBitmap = remember(
        url,
        preferences.qrSizeDp,
        preferences.qrQuietZoneModules,
        preferences.qrColorTheme
    ) {
        QrCodeGenerator.generateQrBitmap(
            content = url,
            sizePx = preferences.qrSizeDp * 3,
            quietZoneModules = preferences.qrQuietZoneModules,
            foregroundColorArgb = preferences.qrColorTheme.foregroundArgb,
            backgroundColorArgb = preferences.qrColorTheme.backgroundArgb
        )
    }

    if (qrBitmap != null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0x88090D16), RoundedCornerShape(10.dp))
                .padding(6.dp)
        ) {
            Image(
                bitmap = qrBitmap,
                contentDescription = "Scan for Wikimedia Commons Info",
                modifier = Modifier
                    .size(preferences.qrSizeDp.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Scan for details",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun OverlayAnchor.toComposeAlignment(): Alignment = when (this) {
    OverlayAnchor.TOP_LEFT -> Alignment.TopStart
    OverlayAnchor.TOP_RIGHT -> Alignment.TopEnd
    OverlayAnchor.BOTTOM_LEFT -> Alignment.BottomStart
    OverlayAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
    OverlayAnchor.CENTER_BOTTOM -> Alignment.BottomCenter
}

private fun OverlayAnchor.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    OverlayAnchor.TOP_LEFT, OverlayAnchor.BOTTOM_LEFT -> Alignment.Start
    OverlayAnchor.TOP_RIGHT, OverlayAnchor.BOTTOM_RIGHT -> Alignment.End
    OverlayAnchor.CENTER_BOTTOM -> Alignment.CenterHorizontally
}
