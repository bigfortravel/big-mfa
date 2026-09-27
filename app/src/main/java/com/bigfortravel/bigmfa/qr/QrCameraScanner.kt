// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.bigfortravel.bigmfa.qr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Analyse chaque image de la caméra à la recherche d'un QR code -- via
 * ML Kit barcode-scanning EMBARQUÉ (bundled), jamais la variante
 * play-services-mlkit (cloud), pour préserver la garantie "zéro requête
 * réseau" de tout le projet.
 *
 * Restreint volontairement à FORMAT_QR_CODE -- Big MFA n'a jamais besoin
 * de codes-barres classiques.
 */
class QrCameraScanner(private val onQrCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val hasDetected = AtomicBoolean(false)

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (hasDetected.get()) {
            imageProxy.close()
            return
        }

        val mediaImage: android.media.Image? = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstOrNull()?.rawValue
                if (rawValue != null && hasDetected.compareAndSet(false, true)) {
                    onQrCodeDetected(rawValue)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}