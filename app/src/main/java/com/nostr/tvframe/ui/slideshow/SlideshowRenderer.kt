package com.nostr.tvframe.ui.slideshow

import android.view.KeyEvent
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
import com.nostr.tvframe.domain.model.OverlayAnchor
import com.nostr.tvframe.domain.model.PhotoDisplayMode
import com.nostr.tvframe.domain.model.PhotoItem
import com.nostr.tvframe.domain.model.PhotoSourceType
import com.nostr.tvframe.domain.model.TextContrastMode
import com.nostr.tvframe.domain.model.UserPreferences
import com.nostr.tvframe.ui.theme.TvFocusAccent
import com.nostr.tvframe.ui.theme.TvZapGold
import com.nostr.tvframe.ui.theme.toComposeFontFamily
import com.nostr.tvframe.util.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Shared Fullscreen Slideshow & Android TV DreamService Renderer.
 *
 * Defaults to [PhotoDisplayMode.FULLSCREEN_CROP] (`ContentScale.Crop`) so photos fill 100%
 * of the 16:9 TV screen edge-to-edge with zero side margins.
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
    val focusRequester = remember { FocusRequester() }
    var currentIndex by remember(photos.size) { mutableIntStateOf(0) }
    var overlayTemporarilyHidden by remember { mutableStateOf(false) }

    var dpadFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var dpadTriggerTick by remember { mutableIntStateOf(0) }

    var burnInOffsetX by remember { mutableIntStateOf(0) }
    var burnInOffsetY by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(currentIndex, photos.size, preferences.slideIntervalSeconds) {
        if (photos.isNotEmpty()) {
            val safeIndex = currentIndex.coerceIn(0, photos.lastIndex)
            onPhotoDisplayed(photos[safeIndex])
            val intervalMs = (preferences.slideIntervalSeconds.coerceAtLeast(5)) * 1000L
            delay(intervalMs)
            currentIndex = (safeIndex + 1) % photos.size
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

    if (photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                    ) {
                        onExitOrWakeTv()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No photos match current filter. Press Back to adjust filters or click Refresh.",
                color = Color.White,
                fontSize = 18.sp
            )
        }
        return
    }

    val currentPhoto = photos[currentIndex.coerceIn(0, photos.lastIndex)]

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
                        onExitOrWakeTv()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        currentIndex = (currentIndex + 1) % photos.size
                        dpadFeedbackMessage = "Next Photo ⏭  •  Click Back to wake up TV"
                        dpadTriggerTick++
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        currentIndex = if (currentIndex - 1 < 0) photos.lastIndex else currentIndex - 1
                        dpadFeedbackMessage = "Previous Photo ⏮  •  Click Back to wake up TV"
                        dpadTriggerTick++
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val willBeFav = !currentPhoto.isFavorite
                        onToggleFavorite(currentPhoto)
                        val favText = if (willBeFav) "Added to Favorites ♥" else "Removed from Favorites ♡"
                        dpadFeedbackMessage = "$favText  •  Click Back to wake up TV"
                        dpadTriggerTick++
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
            }
    ) {
        Crossfade(
            targetState = currentPhoto,
            animationSpec = tween(durationMillis = 1200),
            label = "photoCrossfade"
        ) { photo ->
            val context = LocalContext.current
            val imageRequest = remember(photo.id) {
                ImageRequest.Builder(context)
                    .data(photo.resolveDisplayModel())
                    .crossfade(true)
                    .build()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Optional Blurred Backdrop when user explicitly chooses FIT_WITH_BLUR
                if (preferences.photoDisplayMode == PhotoDisplayMode.FIT_WITH_BLUR) {
                    AsyncImage(
                        model = imageRequest,
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
