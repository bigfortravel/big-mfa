// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.otp

import org.bouncycastle.util.encoders.Base32
import java.net.URI
import java.net.URLDecoder

/**
 * Décode un lien otpauth://totp/... ou otpauth://hotp/... (format standard
 * issu du scan d'un QR code) en ses différents champs.
 *
 * Utilise java.net.URI (pas android.net.Uri) délibérément : cette classe
 * Android nécessite un vrai appareil/émulateur pour fonctionner, alors que
 * java.net.URI tourne aussi bien dans les tests JVM rapides que sur le
 * téléphone — cohérent avec le reste de crypto/ et otp/, zéro dépendance
 * Android dans cette couche.
 */
object OtpAuthUriParser {

    class ParsedOtpAuth(
        val type: String,        // "totp" ou "hotp"
        val name: String,
        val issuer: String,
        val secret: ByteArray,
        val algorithm: TotpGenerator.Algorithm,
        val digits: Int,
        val period: Int,         // pertinent seulement si type == "totp"
        val counter: Long,       // pertinent seulement si type == "hotp"
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedOtpAuth) return false
            return type == other.type && name == other.name && issuer == other.issuer &&
                    secret.contentEquals(other.secret) && algorithm == other.algorithm &&
                    digits == other.digits && period == other.period && counter == other.counter
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + issuer.hashCode()
            result = 31 * result + secret.contentHashCode()
            return result
        }
    }

    fun parse(uriString: String): ParsedOtpAuth {
        require(uriString.startsWith("otpauth://")) { "Ce n'est pas un lien otpauth://" }
        val uri = URI(uriString)

        val type = uri.host?.lowercase() ?: throw IllegalArgumentException("Type manquant (totp/hotp)")
        require(type == "totp" || type == "hotp") { "Type inconnu : $type" }

        // Le label après le / est "Emetteur:NomDuCompte", ou juste "NomDuCompte"
        val rawLabel = uri.path?.removePrefix("/") ?: ""
        val label = URLDecoder.decode(rawLabel, "UTF-8")
        val labelParts = label.split(":", limit = 2)
        val labelIssuer = if (labelParts.size == 2) labelParts[0].trim() else ""
        val name = if (labelParts.size == 2) labelParts[1].trim() else label.trim()

        val params = parseQueryParams(uri.rawQuery ?: "")

        val secretBase32 = params["secret"]?.uppercase()?.replace(" ", "")
            ?: throw IllegalArgumentException("Secret manquant dans le lien")
        val secret = Base32.decode(secretBase32.toByteArray(Charsets.US_ASCII))

        val issuer = params["issuer"]?.let { URLDecoder.decode(it, "UTF-8") } ?: labelIssuer

        val algorithm = when (params["algorithm"]?.uppercase()) {
            "SHA256" -> TotpGenerator.Algorithm.SHA256
            "SHA512" -> TotpGenerator.Algorithm.SHA512
            else -> TotpGenerator.Algorithm.SHA1
        }

        val digits = params["digits"]?.toIntOrNull() ?: 6
        val period = params["period"]?.toIntOrNull() ?: 30
        val counter = params["counter"]?.toLongOrNull() ?: 0L

        return ParsedOtpAuth(type, name, issuer, secret, algorithm, digits, period, counter)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isEmpty()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx == -1) null else pair.substring(0, idx) to pair.substring(idx + 1)
        }.toMap()
    }
}