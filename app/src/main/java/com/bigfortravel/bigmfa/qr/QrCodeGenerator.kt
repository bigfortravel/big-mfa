// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.bouncycastle.util.encoders.Base32
import java.security.SecureRandom

/**
 * Génération -- l'inverse de QrCameraScanner (qui LIT un QR).
 *
 * ErrorCorrectionLevel.M (pas H) -- un niveau plus élevé rend le motif
 * PLUS dense à taille fixe (plus de modules pour la même redondance),
 * ce qui peut paradoxalement RENDRE LE SCAN PLUS DIFFICILE, pas plus
 * facile. M est le bon compromis robustesse/densité (leçon tirée d'un
 * vrai échec de scan sur un lien otpauth:// long).
 */
object QrCodeGenerator {

    private const val SECRET_LENGTH_BYTES = 20 // 160 bits, standard pour un secret TOTP
    private const val DEFAULT_SIZE_PX = 1600

    fun generateRandomSecret(): String {
        val bytes = ByteArray(SECRET_LENGTH_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base32.toBase32String(bytes)
    }

    fun generateQrCodeBitmap(content: String, sizePx: Int = DEFAULT_SIZE_PX): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}