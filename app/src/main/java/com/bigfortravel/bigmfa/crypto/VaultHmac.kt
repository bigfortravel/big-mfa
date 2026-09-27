// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.Arrays as BcArrays

/**
 * Signature/vérification HMAC-SHA256 de l'intégrité globale du coffre.
 *
 * La vérification utilise une comparaison en temps constant
 * (BcArrays.constantTimeAreEqual), pas un simple contentEquals — une
 * comparaison naïve s'arrête au premier octet différent, ce qui peut
 * théoriquement révéler des informations de timing à un attaquant. Un
 * réflexe de sécurité systématique, pas une réaction à une menace
 * concrète identifiée ici, mais qui ne coûte rien à appliquer partout.
 */
object VaultHmac {

    fun sign(data: ByteArray, hmacKey: ByteArray): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(hmacKey))
        hmac.update(data, 0, data.size)
        val result = ByteArray(hmac.macSize)
        hmac.doFinal(result, 0)
        return result
    }

    fun verify(data: ByteArray, hmacKey: ByteArray, expectedMac: ByteArray): Boolean {
        val computedMac = sign(data, hmacKey)
        return BcArrays.constantTimeAreEqual(computedMac, expectedMac)
    }
}