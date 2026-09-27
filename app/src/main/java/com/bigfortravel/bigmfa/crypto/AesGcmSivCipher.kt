package com.bigfortravel.bigmfa.crypto

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom

/**
 * Chiffrement authentifié AES-256-GCM-SIV (RFC 8452), via Bouncy Castle.
 * Résistant à la réutilisation accidentelle de nonce (contrairement à
 * AES-GCM classique) — voir la discussion sur la limite documentée par
 * Aegis eux-mêmes sur ce point.
 *
 * nonce/ciphertext/tag sont exposés SÉPARÉMENT (pas un blob opaque),
 * conformément au format de fichier de coffre déjà défini — auditabilité
 * et transparence du format, dans l'esprit du projet.
 */
object AesGcmSivCipher {

    private const val NONCE_LENGTH_BYTES = 12  // 96 bits, RFC 8452
    private const val TAG_LENGTH_BITS = 128
    private const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8

    /**
     * Volontairement une "class" simple, PAS une "data class" : un
     * ByteArray dans une data class génère un equals()/hashCode() qui
     * compare par RÉFÉRENCE, pas par contenu — piège classique en Kotlin.
     * equals()/hashCode()/copy() sont donc réécrits ici à la main, en
     * utilisant contentEquals()/contentHashCode() (comparaison par octet).
     */
    class EncryptedData(
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val tag: ByteArray,
    ) {
        fun copy(
            nonce: ByteArray = this.nonce,
            ciphertext: ByteArray = this.ciphertext,
            tag: ByteArray = this.tag,
        ): EncryptedData = EncryptedData(nonce, ciphertext, tag)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedData) return false
            return nonce.contentEquals(other.nonce) &&
                    ciphertext.contentEquals(other.ciphertext) &&
                    tag.contentEquals(other.tag)
        }

        override fun hashCode(): Int {
            var result = nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            result = 31 * result + tag.contentHashCode()
            return result
        }
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH_BYTES)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedData {
        val nonce = generateNonce()
        val cipher = GCMSIVBlockCipher(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_LENGTH_BITS, nonce))

        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        var offset = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        offset += cipher.doFinal(output, offset)

        // La sortie de Bouncy Castle contient ciphertext + tag concaténés
        // — on les sépare pour respecter notre format de fichier.
        val ciphertext = output.copyOfRange(0, offset - TAG_LENGTH_BYTES)
        val tag = output.copyOfRange(offset - TAG_LENGTH_BYTES, offset)

        return EncryptedData(nonce, ciphertext, tag)
    }

    fun decrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        val cipher = GCMSIVBlockCipher(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_LENGTH_BITS, encryptedData.nonce))

        val combined = encryptedData.ciphertext + encryptedData.tag
        val output = ByteArray(cipher.getOutputSize(combined.size))
        var offset = cipher.processBytes(combined, 0, combined.size, output, 0)
        offset += cipher.doFinal(output, offset)

        return output.copyOfRange(0, offset)
    }
}