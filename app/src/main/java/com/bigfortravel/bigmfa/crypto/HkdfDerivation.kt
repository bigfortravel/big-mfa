// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * HKDF (RFC 5869) — dérive depuis la clé maîtresse (MK) des sous-clés
 * INDÉPENDANTES pour des usages distincts (contenu, intégrité), jamais la
 * même clé utilisée à deux fins différentes.
 *
 * Le "salt" de HKDF est laissé à null volontairement ici : contrairement
 * à Argon2 (où le sel protège contre les attaques par table précalculée
 * sur un mot de passe faible), MK est déjà une clé aléatoire de haute
 * entropie — HKDF sert uniquement à "étendre/séparer" cette entropie déjà
 * présente en plusieurs sous-clés, pas à en générer depuis un secret
 * humain. C'est un usage standard et documenté de HKDF (RFC 5869 §3.1).
 */
object HkdfDerivation {

    private const val DERIVED_KEY_LENGTH_BYTES = 32 // 256 bits

    /**
     * @param masterKey La clé maîtresse (MK), obtenue via un slot déverrouillé.
     * @param info Étiquette distincte par usage — ex. "big-mfa-content-v1"
     *   ou "big-mfa-integrity-v1" — garantit que deux appels avec des
     *   `info` différents ne peuvent JAMAIS produire la même sous-clé,
     *   même à partir de la même MK.
     */
    fun expand(masterKey: ByteArray, info: String): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(masterKey, null, info.toByteArray(Charsets.UTF_8)))
        val output = ByteArray(DERIVED_KEY_LENGTH_BYTES)
        hkdf.generateBytes(output, 0, DERIVED_KEY_LENGTH_BYTES)
        return output
    }
}