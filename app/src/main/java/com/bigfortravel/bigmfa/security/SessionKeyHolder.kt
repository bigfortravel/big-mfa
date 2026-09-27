// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

/**
 * Détient les clés de la session déverrouillée en cours — masterKey,
 * contentKey, hmacKey. UN SEUL point de vérité pour "vider la session",
 * exactement le principe déjà appliqué côté Chrome (sessionKey/
 * sessionSalt/sessionVaultHmacKey remis à null sur tout chemin d'erreur).
 *
 * clear() efface explicitement le contenu des tableaux (Arrays.fill),
 * pas juste la référence — une String/ByteArray simplement "oubliée" par
 * le garbage collector peut rester en mémoire un moment, potentiellement
 * lisible par un dump mémoire. On ne laisse jamais ça au hasard.
 */
class SessionKeyHolder {

    var masterKey: ByteArray? = null
        private set
    var contentKey: ByteArray? = null
        private set
    var hmacKey: ByteArray? = null
        private set

    val isUnlocked: Boolean
        get() = masterKey != null

    fun setKeys(masterKey: ByteArray, contentKey: ByteArray, hmacKey: ByteArray) {
        clear() // efface toute ancienne session avant d'en poser une nouvelle
        this.masterKey = masterKey
        this.contentKey = contentKey
        this.hmacKey = hmacKey
    }

    /**
     * Vide la session — appelé sur TOUT chemin d'erreur (mauvais mot de
     * passe, coffre corrompu, verrouillage automatique par inactivité),
     * jamais seulement en cas de verrouillage volontaire.
     */
    fun clear() {
        masterKey?.let { java.util.Arrays.fill(it, 0) }
        contentKey?.let { java.util.Arrays.fill(it, 0) }
        hmacKey?.let { java.util.Arrays.fill(it, 0) }
        masterKey = null
        contentKey = null
        hmacKey = null
    }
}