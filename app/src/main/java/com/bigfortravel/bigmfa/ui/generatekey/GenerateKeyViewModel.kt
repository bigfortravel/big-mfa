// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.generatekey

import androidx.lifecycle.ViewModel
import com.bigfortravel.bigmfa.qr.QrCodeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

/**
 * Générateur de clé indépendant -- n'a besoin d'aucune donnée du coffre.
 * Ne persiste RIEN automatiquement dans le coffre (pas de "Enregistrer"),
 * cohérent avec le comportement observé côté Chrome : générer une clé
 * pour un AUTRE service, avec option de visualiser le QR équivalent.
 */
class GenerateKeyViewModel : ViewModel() {

    private val _secret = MutableStateFlow("")
    val secret: StateFlow<String> = _secret.asStateFlow()

    fun generateNewKey() {
        _secret.value = QrCodeGenerator.generateRandomSecret()
    }

    /**
     * Construit le lien otpauth:// à partir de la clé actuelle + les
     * options fournies -- identique en esprit à ce que OtpAuthUriParser
     * sait lire, mais dans le sens inverse (on écrit, pas on lit).
     */
    fun buildOtpAuthUri(
        name: String,
        issuer: String,
        isHotp: Boolean,
        algorithm: String,
        digits: Int,
        period: Int,
    ): String {
        val type = if (isHotp) "hotp" else "totp"
        val encodedName = URLEncoder.encode(name.ifBlank { "Compte" }, "UTF-8")
        val label = if (issuer.isNotBlank()) {
            "${URLEncoder.encode(issuer, "UTF-8")}:$encodedName"
        } else {
            encodedName
        }

        val builder = StringBuilder("otpauth://$type/$label")
        builder.append("?secret=").append(_secret.value)
        if (issuer.isNotBlank()) {
            builder.append("&issuer=").append(URLEncoder.encode(issuer, "UTF-8"))
        }
        builder.append("&algorithm=").append(algorithm)
        builder.append("&digits=").append(digits)
        if (isHotp) {
            builder.append("&counter=0")
        } else {
            builder.append("&period=").append(period)
        }
        return builder.toString()
    }
}