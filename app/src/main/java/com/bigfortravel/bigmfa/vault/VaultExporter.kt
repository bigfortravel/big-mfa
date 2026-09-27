// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.json.JSONArray
import org.json.JSONObject

/**
 * Génère un fichier de sauvegarde chiffré exportable — AVEC SON PROPRE
 * MOT DE PASSE, distinct du mot de passe du coffre principal (même
 * discipline que le convertisseur backup-v3 : deux secrets, jamais
 * mélangés). Format autonome, indépendant du fichier de coffre principal.
 */
class VaultExporter {

    /**
     * @param accounts Comptes DÉJÀ déchiffrés en mémoire (obtenus via un
     *   VaultUnlocker.UnlockResult.Success), jamais lus depuis le disque
     *   directement ici.
     * @param exportPassword Le mot de passe CHOISI POUR CET EXPORT
     *   uniquement — distinct du mot de passe du coffre.
     */
    fun export(accounts: List<VaultAccount>, exportPassword: CharArray): String {
        val salt = Argon2KeyDerivation.generateSalt()
        val tier = Argon2KeyDerivation.MemoryTier.STANDARD
        val exportKey = Argon2KeyDerivation.derive(exportPassword, salt, tier)

        val hmacKey = HkdfDerivation.expand(exportKey, "big-mfa-export-integrity-v1")
        val contentKey = HkdfDerivation.expand(exportKey, "big-mfa-export-content-v1")

        val accountsJson = accountsToJson(accounts)
        val contentBytes = accountsJson.toString().toByteArray(Charsets.UTF_8)
        val encryptedContent = AesGcmSivCipher.encrypt(contentBytes, contentKey)

        val json = JSONObject()
        json.put("format", "big-mfa-export")
        json.put("version", 1)
        json.put("exported_at", System.currentTimeMillis())
        json.put("kdf", JSONObject().apply {
            put("name", "argon2id")
            put("salt", bytesToHex(salt))
            put("memory_kib", tier.memoryKiB)
            put("iterations", tier.iterations)
            put("parallelism", tier.parallelism)
        })
        json.put("content", bytesToHex(encryptedContent.ciphertext))
        json.put("content_nonce", bytesToHex(encryptedContent.nonce))
        json.put("content_tag", bytesToHex(encryptedContent.tag))

        val canonicalBytes = json.toString().toByteArray(Charsets.UTF_8)
        val mac = VaultHmac.sign(canonicalBytes, hmacKey)
        json.put("export_hmac", bytesToHex(mac))

        return json.toString(2)
    }

    private fun accountsToJson(accounts: List<VaultAccount>): JSONArray {
        val array = JSONArray()
        for (acc in accounts) {
            array.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("type", acc.type)
                    put("name", acc.name)
                    put("issuer", acc.issuer)
                    put("secret", acc.secret)
                    put("algorithm", acc.algorithm)
                    put("digits", acc.digits)
                    acc.period?.let { put("period", it) }
                    acc.counter?.let { put("counter", it) }
                },
            )
        }
        return array
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}