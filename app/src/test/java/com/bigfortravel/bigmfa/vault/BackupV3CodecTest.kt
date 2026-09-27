// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupV3CodecTest {

    private fun sampleAccounts() = listOf(
        VaultAccount(
            id = "acc-1", type = "totp", name = "GitHub", issuer = "GitHub Inc.",
            secret = "JBSWY3DPEHPK3PXP", algorithm = "SHA-1", digits = 6, period = 30,
        ),
        VaultAccount(
            id = "acc-2", type = "hotp", name = "ServiceX", issuer = "",
            secret = "KRSXG5CTMVRXEZLU", algorithm = "SHA-1", digits = 6, counter = 5L,
        ),
    )

    @Test
    fun `un export puis import redonne les memes comptes`() {
        val password = "MonMotDePasseExport123!"
        val json = BackupV3Codec.encode(sampleAccounts(), password.toCharArray())

        val decoded = BackupV3Codec.decode(json, password.toCharArray())

        assertEquals(2, decoded.size)
        assertEquals("GitHub", decoded[0].name)
        assertEquals("JBSWY3DPEHPK3PXP", decoded[0].secret)
        assertEquals(30, decoded[0].period)
        assertEquals("ServiceX", decoded[1].name)
        assertEquals(5L, decoded[1].counter)
    }

    @Test
    fun `mauvais mot de passe echoue avec un message generique`() {
        val json = BackupV3Codec.encode(sampleAccounts(), "MotDePasseCorrect123!".toCharArray())

        val exception = assertThrows(BackupV3Codec.DecodeException::class.java) {
            BackupV3Codec.decode(json, "MauvaisMotDePasse999!".toCharArray())
        }
        assertEquals("Mot de passe incorrect ou fichier corrompu", exception.message)
    }

    @Test
    fun `format de fichier non reconnu est rejete proprement`() {
        val invalidJson = """{"format": "autre-chose", "version": 1}"""

        assertThrows(BackupV3Codec.DecodeException::class.java) {
            BackupV3Codec.decode(invalidJson, "peu importe".toCharArray())
        }
    }

    @Test
    fun `le format produit contient bien la structure backup-v3 attendue`() {
        val json = BackupV3Codec.encode(sampleAccounts(), "MotDePasse123!".toCharArray())
        val parsed = JSONObject(json)

        assertEquals("big-mfa-backup", parsed.getString("format"))
        assertEquals(3, parsed.getInt("version"))
        assertEquals("PBKDF2", parsed.getJSONObject("kdf").getString("name"))
        assertEquals(600_000, parsed.getJSONObject("kdf").getInt("iterations"))
        assertEquals("AES-GCM", parsed.getJSONObject("cipher").getString("name"))
        assertTrue(parsed.has("ciphertext"))
    }

    @Test
    fun `secret en clair n'apparait jamais dans le fichier chiffre`() {
        val json = BackupV3Codec.encode(sampleAccounts(), "MotDePasse123!".toCharArray())
        assertTrue(!json.contains("JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun `liste de comptes vide fonctionne correctement`() {
        val password = "MotDePasse123!"
        val json = BackupV3Codec.encode(emptyList(), password.toCharArray())
        val decoded = BackupV3Codec.decode(json, password.toCharArray())
        assertEquals(0, decoded.size)
    }

    /**
     * TEST DE VERROUILLAGE -- reconstruit un fichier backup-v3 MANUELLEMENT
     * avec l'AAD exacte de Chrome (canonicalBackupV3HeaderString), sans
     * passer par BackupV3Codec.encode(). Si quelqu'un modifie un jour
     * canonicalHeaderString() dans BackupV3Codec.kt (ordre des champs,
     * séparateur, valeur d'un champ), ce test échoue immédiatement --
     * contrairement aux autres tests (Android<->Android), qui resteraient
     * verts même avec une AAD incohérente des deux côtés.
     */
    @Test
    fun `l'AAD reproduit exactement la chaine canonique de Chrome, verrouillage anti-regression`() {
        val password = "MotDePasseTest123!"
        val json = BackupV3Codec.encode(sampleAccounts(), password.toCharArray())
        val envelope = JSONObject(json)

        val iterations = envelope.getJSONObject("kdf").getInt("iterations")

        // Reconstruction manuelle de l'AAD, EXACTEMENT comme le ferait
        // canonicalBackupV3HeaderString() côté Chrome (backup-v3.js) --
        // valeurs et ordre copiés littéralement du fichier source Chrome.
        val expectedAad = listOf(
            "format=big-mfa-backup",
            "version=3",
            "kdf.name=PBKDF2",
            "kdf.hash=SHA-256",
            "kdf.iterations=$iterations",
            "cipher.name=AES-GCM",
            "cipher.key_bits=256",
            "cipher.tag_bits=128",
        ).joinToString("|").toByteArray(Charsets.UTF_8)

        // Déchiffrement manuel avec cette AAD reconstruite indépendamment
        // du code de production -- si BackupV3Codec utilisait une AAD
        // différente (mauvais ordre, séparateur différent, champ manquant),
        // le déchiffrement échouerait ici avec une exception, même si
        // decode() lui-même fonctionne parfaitement en interne.
        val salt = base64Decode(envelope.getJSONObject("kdf").getString("salt"))
        val nonce = base64Decode(envelope.getJSONObject("cipher").getString("nonce"))
        val ciphertext = base64Decode(envelope.getString("ciphertext"))

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val key = factory.generateSecret(spec).encoded

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(expectedAad)

        // Ne doit lever AUCUNE exception -- la seule assertion qui compte
        // ici est que doFinal() réussit avec CETTE AAD précise.
        val plaintext = cipher.doFinal(ciphertext)
        val payload = JSONObject(String(plaintext, Charsets.UTF_8))
        assertTrue(payload.has("accounts"))
    }

    private fun base64Decode(base64: String): ByteArray {
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