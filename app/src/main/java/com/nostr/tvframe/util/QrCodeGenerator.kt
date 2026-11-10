package com.nostr.tvframe.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Dynamically renders crisp ZXing QR codes into Compose [ImageBitmap] instances
 * with customizable foreground/background ARGB colors and quiet-zone margin scale.
 */
object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 320,
        quietZoneModules: Int = 2,
        foregroundColorArgb: Int = 0xFFFFFFFF.toInt(),
        backgroundColorArgb: Int = 0xCC0F172A.toInt()
    ): ImageBitmap? {
        if (content.isBlank() || sizePx <= 0) return null

        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to quietZoneModules.coerceIn(0, 6)
            )

            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) {
                        foregroundColorArgb
                    } else {
                        backgroundColorArgb
                    }
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }.asImageBitmap()
        }.getOrNull()
    }
}
