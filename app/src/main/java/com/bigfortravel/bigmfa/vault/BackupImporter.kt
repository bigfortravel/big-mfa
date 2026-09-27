// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.json.JSONObject
import java.util.UUID

/**
 * Détecte automatiquement le format d'un fichier importé (natif Android
 * ou Chrome backup-v3), le déchiffre avec le bon décodeur, puis fusionne
 * les comptes obtenus avec la liste actuelle -- ADDITIF uniquement,
 * jamais de remplacement. En cas de doublon de nom, les deux entrées
 * sont conservées (jamais de fusion/écrasement silencieux).
 *
 * ImportException porte désormais un UiText (pas une String) -- traduit
 * uniquement au moment de l'affichage par l'écran, jamais figé dans une
 * langue au moment où l'exception est levée.
 */
class BackupImporter {

    class ImportException(val uiMessage: UiText) : Exception()

    sealed class DetectedFormat {
        object Native : DetectedFormat()
        object ChromeCompatible : DetectedFormat()
    }

    fun detectFormat(jsonString: String): DetectedFormat {
        val format = try {
            JSONObject(jsonString).optString("format")
        } catch (_: Exception) {
            throw ImportException(UiText.Resource(R.string.backup_importer_unreadable))
        }
        return when (format) {
            "big-mfa-export" -> DetectedFormat.Native
            "big-mfa-backup" -> DetectedFormat.ChromeCompatible
            else -> throw ImportException(UiText.Resource(R.string.backup_importer_unknown_format))
        }
    }

    fun importAndMerge(
        jsonString: String,
        password: CharArray,
        currentAccounts: List<VaultAccount>,
    ): List<VaultAccount> {
        val format = detectFormat(jsonString)

        val importedAccounts = when (format) {
            is DetectedFormat.Native -> decodeNativeExport(jsonString, password)
            is DetectedFormat.ChromeCompatible -> {
                try {
                    BackupV3Codec.decode(jsonString, password)
                } catch (_: BackupV3Codec.DecodeException) {
                    throw ImportException(UiText.Resource(R.string.backup_importer_wrong_password))
                }
            }
        }

        val reIdentified = importedAccounts.map { it.copy(id = UUID.randomUUID().toString()) }
        return currentAccounts + reIdentified
    }

    private fun decodeNativeExport(jsonString: String, password: CharArray): List<VaultAccount> {
        try {
            val envelope = JSONObject(jsonString)
            val kdf = envelope.getJSONObject("kdf")
            val salt = hexToBytes(kdf.getString("salt"))
            val memoryKiB = kdf.getInt("memory_kib")
            val iterations = kdf.getInt("iterations")
            val parallelism = kdf.getInt("parallelism")

            val derivedKey = com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation.derive(
                password, salt, memoryKiB, iterations, parallelism,
            )
            val hmacKey = com.bigfortravel.bigmfa.crypto.HkdfDerivation.expand(derivedKey, "big-mfa-export-integrity-v1")
            val contentKey = com.bigfortravel.bigmfa.crypto.HkdfDerivation.expand(derivedKey, "big-mfa-export-content-v1")

            val canonicalBytes = envelope.toString().toByteArray(Charsets.UTF_8).let {
                val withoutHmac = JSONObject(envelope.toString())
                withoutHmac.remove("export_hmac")
                withoutHmac.toString().toByteArray(Charsets.UTF_8)
            }
            val expectedMac = hexToBytes(envelope.getString("export_hmac"))
            val isValid = com.bigfortravel.bigmfa.crypto.VaultHmac.verify(canonicalBytes, hmacKey, expectedMac)
            if (!isValid) {
                throw ImportException(UiText.Resource(R.string.backup_importer_wrong_password))
            }

            val encryptedData = com.bigfortravel.bigmfa.crypto.AesGcmSivCipher.EncryptedData(
                nonce = hexToBytes(envelope.getString("content_nonce")),
                ciphertext = hexToBytes(envelope.getString("content")),
                tag = hexToBytes(envelope.getString("content_tag")),
            )
            val contentBytes = com.bigfortravel.bigmfa.crypto.AesGcmSivCipher.decrypt(encryptedData, contentKey)
            val accountsArray = org.json.JSONArray(String(contentBytes, Charsets.UTF_8))

            val accounts = mutableListOf<VaultAccount>()
            for (i in 0 until accountsArray.length()) {
                val obj = accountsArray.getJSONObject(i)
                accounts.add(
                    VaultAccount(
                        id = UUID.randomUUID().toString(),
                        type = obj.getString("type"),
                        name = obj.getString("name"),
                        issuer = obj.getString("issuer"),
                        secret = obj.getString("secret"),
                        algorithm = obj.getString("algorithm"),
                        digits = obj.getInt("digits"),
                        period = if (obj.has("period")) obj.getInt("period") else null,
                        counter = if (obj.has("counter")) obj.getLong("counter") else null,
                    ),
                )
            }
            return accounts
        } catch (e: ImportException) {
            throw e
        } catch (_: Exception) {
            throw ImportException(UiText.Resource(R.string.backup_importer_wrong_password))
        } finally {
            password.fill('\u0000')
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Chaîne hexadécimale de longueur invalide" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}