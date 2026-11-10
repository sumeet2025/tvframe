package com.nostr.tvframe.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nostr.tvframe.domain.model.FontFamilyOption

val TvBackgroundDark = Color(0xFF090D16)
val TvSurfaceCard = Color(0xFF131C2E)
val TvSurfaceElevated = Color(0xFF1E293B)
val TvFocusAccent = Color(0xFF38BDF8)
val TvZapGold = Color(0xFFF59E0B)
val TvFavoriteCoral = Color(0xFFFB7185)
val TvTextPrimary = Color(0xFFF8FAFC)
val TvTextSecondary = Color(0xFF94A3B8)

private val DarkTvColorScheme = darkColorScheme(
    primary = TvFocusAccent,
    secondary = TvZapGold,
    tertiary = TvFavoriteCoral,
    background = TvBackgroundDark,
    surface = TvSurfaceCard,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TvTextPrimary,
    onSurface = TvTextPrimary
)

@Composable
fun TVFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkTvColorScheme,
        content = content
    )
}

/**
 * Explicit D-Pad focus modifier for 10-foot Android TV navigation.
 * Uses a high-contrast 2.5dp cyan border and surface highlight without overscaling outside parent bounds,
 * ensuring edge pills (such as "Gallery & Filters") never get their rounded corners sliced off.
 */
fun Modifier.tvFocusableCard(
    focusedScale: Float = 1.0f,
    cornerRadiusDp: Int = 12
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    this
        .onFocusChanged { state -> isFocused = state.isFocused || state.hasFocus }
        .border(
            border = if (isFocused) {
                BorderStroke(2.5.dp, TvFocusAccent)
            } else {
                BorderStroke(1.dp, Color(0x4494A3B8))
            },
            shape = shape
        )
        .background(
            color = if (isFocused) TvSurfaceElevated else TvSurfaceCard,
            shape = shape
        )
        .focusable()
}

fun FontFamilyOption.toComposeFontFamily(): FontFamily = when (this) {
    FontFamilyOption.SYSTEM_DEFAULT -> FontFamily.Default
    FontFamilyOption.SANS_SERIF -> FontFamily.SansSerif
    FontFamilyOption.SERIF -> FontFamily.Serif
    FontFamilyOption.MONOSPACE -> FontFamily.Monospace
    FontFamilyOption.CURSIVE_DISPLAY -> FontFamily.Cursive
}
