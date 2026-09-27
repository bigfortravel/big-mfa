// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.otp

import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Génération de codes TOTP (RFC 6238) — un code à N chiffres qui change
 * toutes les `period` secondes, dérivé du secret et de l'heure actuelle.
 */
object TotpGenerator {

    enum class Algorithm { SHA1, SHA256, SHA512 }

    /**
     * @param secret Le secret Base32 déjà décodé en octets bruts.
     * @param timeMillis Horodatage en millisecondes — paramétrable pour
     *   les tests (vecteurs RFC à des dates précises), sinon l'heure
     *   actuelle de l'appareil par défaut.
     */
    fun generate(
        secret: ByteArray,
        timeMillis: Long = System.currentTimeMillis(),
        period: Int = 30,
        digits: Int = 6,
        algorithm: Algorithm = Algorithm.SHA1,
    ): String {
        val counter = (timeMillis / 1000) / period
        return hmacOtp(secret, counter, digits, algorithm)
    }

    /** Nombre de secondes restantes avant que le code actuel change. */
    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis(), period: Int = 30): Int {
        val elapsedInPeriod = (timeMillis / 1000) % period
        return (period - elapsedInPeriod).toInt()
    }

    /**
     * Cœur commun HOTP (RFC 4226) : HMAC du compteur, puis troncature
     * dynamique en un code à N chiffres. TOTP (ci-dessus) n'est qu'un
     * HOTP dont le compteur est dérivé du temps plutôt qu'incrémenté
     * manuellement.
     */
    internal fun hmacOtp(secret: ByteArray, counter: Long, digits: Int, algorithm: Algorithm): String {
        val counterBytes = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }

        val digest = when (algorithm) {
            Algorithm.SHA1 -> SHA1Digest()
            Algorithm.SHA256 -> SHA256Digest()
            Algorithm.SHA512 -> SHA512Digest()
        }
        val hmac = HMac(digest)
        hmac.init(KeyParameter(secret))
        hmac.update(counterBytes, 0, counterBytes.size)
        val hash = ByteArray(hmac.macSize)
        hmac.doFinal(hash, 0)

        // Troncature dynamique RFC 4226 §5.3
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binaryCode = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        var modulus = 1
        repeat(digits) { modulus *= 10 }
        val otp = binaryCode % modulus
        return otp.toString().padStart(digits, '0')
    }
}