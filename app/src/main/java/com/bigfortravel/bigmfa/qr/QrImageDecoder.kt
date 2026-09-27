// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.qr

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Décode un QR depuis une image STATIQUE (import galerie/fichier) --
 * complémentaire de QrCameraScanner (flux caméra en direct). Même
 * moteur ML Kit embarqué, juste une source d'image différente.
 */
object QrImageDecoder {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    fun decode(bitmap: Bitmap, onResult: (String?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes -> onResult(barcodes.firstOrNull()?.rawValue) }
            .addOnFailureListener { onResult(null) }
    }
}