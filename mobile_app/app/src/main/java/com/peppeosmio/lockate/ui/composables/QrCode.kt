package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [text] as a QR code, black on white in both themes: scanners read dark modules on a light
 * background best.
 */
@Composable
fun QrCode(text: String, description: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val matrix =
        remember(text) {
            QRCodeWriter()
                .encode(
                    text,
                    BarcodeFormat.QR_CODE,
                    0,
                    0,
                    mapOf(
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                        EncodeHintType.MARGIN to 0,
                    ),
                )
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp)
                .semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(size)) {
            val cell = this.size.width / matrix.width
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) {
                        drawRect(
                            Color.Black,
                            topLeft = Offset(x * cell, y * cell),
                            // a hair wider, so neighbouring modules don't show seams
                            size = Size(cell + 0.5f, cell + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun QrCodePreview() {
    PreviewSurface {
        QrCode(
            text =
                "lockate://invite?server=https%3A%2F%2Flockate.example.com&code=42-acid-acorn-acre",
            description = "Invite QR code",
            modifier = Modifier.padding(16.dp),
        )
    }
}
