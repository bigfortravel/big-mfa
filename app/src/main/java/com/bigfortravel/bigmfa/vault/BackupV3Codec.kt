// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lit et écrit le format backup-v3 de l'extension Chrome -- PBKDF2-SHA256
 * (600k it.) + AES-256-GCM classique AVEC AAD (Additional Authenticated
 * Data) -- Chrome lie cryptographiquement l'en-tête (format/version/kdf/
 * cipher) au chiffrement via AAD, vérifié ici byte pour byte, sinon le
 * tag GCM ne correspond jamais côté Chrome à l'import, même avec le bon
 * mot de passe (bug réel trouvé et corrigé).
 */
object BackupV3Codec {

    private const val ITERATIONS = 600_000
    private const val SALT_LENGTH_BYTES = 32
    private const val NONCE_LENGTH_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class DecodeException(message: String) : Exception(message)

    fun encode(accounts: List<VaultAccount>, password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)

        val payload = JSONObject().apply {
            put("payload_version", 1)
            put("generator", "big-mfa-android")
            put("exported_at", isoTimestampNow())
            put("accounts", accountsToJsonArray(accounts))
            put("settings", JSONObject())
        }
        val plaintext = payload.toString().toByteArray(Charsets.UTF_8)

        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val aad = canonicalHeaderString(ITERATIONS).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        val envelope = JSONObject().apply {
            put("format", "big-mfa-backup")
            put("version", 3)
            put(
                "kdf",
                JSONObject().apply {
                    put("name", "PBKDF2")
                    put("hash", "SHA-256")
                    put("iterations", ITERATIONS)
                    put("salt", bytesToBase64(salt))
                },
            )
            put(
                "cipher",
                JSONObject().apply {
                    put("name", "AES-GCM")
                    put("key_bits", KEY_BITS)
                    put("nonce", bytesToBase64(nonce))
                    put("tag_bits", TAG_BITS)
                },
            )
            put("ciphertext", bytesToBase64(ciphertext))
        }

        password.fill('\u0000')
        return envelope.toString(2)
    }

    fun decode(jsonString: String, password: CharArray): List<VaultAccount> {
        try {
            val envelope = JSONObject(jsonString)
            if (envelope.optString("format") != "big-mfa-backup") {
                throw DecodeException("Format de fichier non reconnu")
            }

            val kdf = envelope.getJSONObject("kdf")
            val salt = base64ToBytes(kdf.getString("salt"))
            val iterations = kdf.getInt("iterations")

            val cipherInfo = envelope.getJSONObject("cipher")
            val nonce = base64ToBytes(cipherInfo.getString("nonce"))
            val ciphertext = base64ToBytes(envelope.getString("ciphertext"))

            val key = deriveKey(password, salt, iterations)
            val aad = canonicalHeaderString(iterations).toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(ciphertext)

            val payload = JSONObject(String(plaintext, Charsets.UTF_8))
            return jsonArrayToAccounts(payload.getJSONArray("accounts"))
        } catch (e: DecodeException) {
            throw e
        } catch (_: Exception) {
            throw DecodeException("Mot de passe incorrect ou fichier corrompu")
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * DOIT être identique, byte pour byte, à canonicalBackupV3HeaderString
     * côté Chrome (backup-v3.js) -- ordre des champs et séparateur "|"
     * fixes, jamais recalculés différemment.
     */
    private fun canonicalHeaderString(iterations: Int): String {
        return listOf(
            "format=big-mfa-backup",
            "version=3",
            "kdf.name=PBKDF2",
            "kdf.hash=SHA-256",
            "kdf.iterations=$iterations",
            "cipher.name=AES-GCM",
            "cipher.key_bits=$KEY_BITS",
            "cipher.tag_bits=$TAG_BITS",
        ).joinToString("|")
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }

    private fun accountsToJsonArray(accounts: List<VaultAccount>): JSONArray {
        val array = JSONArray()
        for (acc in accounts) {
            array.put(
                JSONObject().apply {
                    put("name", acc.name)
                    put("issuer", acc.issuer)
                    put("type", acc.type)
                    put("algorithm", acc.algorithm)
                    put("digits", acc.digits)
                    put("secret", acc.secret)
                    acc.period?.let { put("period", it) }
                    acc.counter?.let { put("counter", it) }
                },
            )
        }
        return array
    }

    private fun jsonArrayToAccounts(array: JSONArray): List<VaultAccount> {
        val accounts = mutableListOf<VaultAccount>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            accounts.add(
                VaultAccount(
                    id = UUID.randomUUID().toString(),
                    type = obj.optString("type", "totp"),
                    name = obj.getString("name"),
                    issuer = obj.optString("issuer", ""),
                    secret = obj.getString("secret"),
                    algorithm = obj.optString("algorithm", "SHA-1"),
                    digits = obj.optInt("digits", 6),
                    period = if (obj.has("period")) obj.getInt("period") else null,
                    counter = if (obj.has("counter")) obj.getLong("counter") else null,
                ),
            )
        }
        return accounts
    }

    private fun isoTimestampNow(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date())
    }

    private fun bytesToBase64(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val result = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            result.append(alphabet[b0 shr 2])
            result.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
            result.append(if (i + 1 < bytes.size) alphabet[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            result.append(if (i + 2 < bytes.size) alphabet[b2 and 0x3F] else '=')
            i += 3
        }
        return result.toString()
    }

    private fun base64ToBytes(base64: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val clean = base64.trimEnd('=')
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bitsCollected = 0
        for (char in clean) {
            val value = alphabet.indexOf(char)
            if (value == -1) continue
            buffer = (buffer shl 6) or value
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                output.write((buffer shr bitsCollected) and 0xFF)
            }
        }
        return output.toByteArray()
    }
}