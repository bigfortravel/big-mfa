// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Réécrit le contenu chiffré du coffre (liste de comptes) -- réutilisable
 * pour ajouter, renommer, ou supprimer un compte plus tard. Préserve
 * TOUJOURS les slots existants (mot de passe, biométrique) tels quels,
 * ne touche jamais à leur contenu -- uniquement content/contentNonce/
 * contentTag/vaultHmac sont recalculés à chaque écriture.
 */
class VaultContentWriter(private val repository: VaultRepository) {

    fun writeAccounts(accounts: List<VaultAccount>, contentKey: ByteArray, hmacKey: ByteArray) {
        val currentVault = repository.read()

        val accountsJson = accountsToJson(accounts)
        val contentBytes = accountsJson.toString().toByteArray(Charsets.UTF_8)
        val encryptedContent = AesGcmSivCipher.encrypt(contentBytes, contentKey)

        val vaultWithoutHmac = currentVault.copy(
            modifiedAt = currentIsoTimestamp(),
            content = bytesToHex(encryptedContent.ciphertext),
            contentNonce = bytesToHex(encryptedContent.nonce),
            contentTag = bytesToHex(encryptedContent.tag),
            vaultHmac = "",
        )

        val mac = VaultHmac.sign(repository.canonicalBytesForHmac(vaultWithoutHmac), hmacKey)
        repository.write(vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac)))
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
                    acc.lastUsedAt?.let { put("last_used_at", it) }
                    acc.autofillPackageName?.let { put("autofill_package_name", it) }
                    acc.autofillWebDomain?.let { put("autofill_web_domain", it) }
                },
            )
        }
        return array
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun currentIsoTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}