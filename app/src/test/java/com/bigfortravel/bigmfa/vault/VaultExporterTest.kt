// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultExporterTest {

    private val exporter = VaultExporter()

    private fun sampleAccounts(): List<VaultAccount> = listOf(
        VaultAccount(
            id = "acc-1",
            type = "totp",
            name = "GitHub",
            issuer = "GitHub Inc.",
            secret = "JBSWY3DPEHPK3PXP",
            algorithm = "SHA-1",
            digits = 6,
            period = 30,
        ),
        VaultAccount(
            id = "acc-2",
            type = "hotp",
            name = "ServiceX",
            issuer = "",
            secret = "KRSXG5CTMVRXEZLU",
            algorithm = "SHA-1",
            digits = 6,
            counter = 12L,
        ),
    )

    @Test
    fun `export produit un JSON bien forme avec tous les champs attendus`() {
        val json = exporter.export(sampleAccounts(), "MotDePasseExport123!".toCharArray())
        val parsed = JSONObject(json)

        assertEquals("big-mfa-export", parsed.getString("format"))
        assertEquals(1, parsed.getInt("version"))
        assertTrue(parsed.has("kdf"))
        assertTrue(parsed.has("content"))
        assertTrue(parsed.has("content_nonce"))
        assertTrue(parsed.has("content_tag"))
        assertTrue(parsed.has("export_hmac"))
    }

    @Test
    fun `les parametres Argon2id sont bien ceux du palier STANDARD`() {
        val json = exporter.export(sampleAccounts(), "MotDePasseExport123!".toCharArray())
        val kdf = JSONObject(json).getJSONObject("kdf")

        assertEquals("argon2id", kdf.getString("name"))
        assertEquals(32768, kdf.getInt("memory_kib"))
        assertEquals(3, kdf.getInt("iterations"))
        assertEquals(2, kdf.getInt("parallelism"))
    }

    @Test
    fun `deux exports du meme contenu avec le meme mot de passe donnent des sels differents`() {
        val json1 = exporter.export(sampleAccounts(), "MemeMotDePasse123!".toCharArray())
        val json2 = exporter.export(sampleAccounts(), "MemeMotDePasse123!".toCharArray())

        val salt1 = JSONObject(json1).getJSONObject("kdf").getString("salt")
        val salt2 = JSONObject(json2).getJSONObject("kdf").getString("salt")
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `le contenu chiffre n'est jamais identique au secret en clair`() {
        val json = exporter.export(sampleAccounts(), "MotDePasseExport123!".toCharArray())
        val content = JSONObject(json).getString("content")

        // Le secret Base32 "JBSWY3DPEHPK3PXP" ne doit JAMAIS apparaître
        // tel quel dans le contenu chiffré.
        assertTrue(!content.contains("JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun `export_hmac fait bien 64 caracteres hexadecimaux (32 octets, HMAC-SHA256)`() {
        val json = exporter.export(sampleAccounts(), "MotDePasseExport123!".toCharArray())
        val mac = JSONObject(json).getString("export_hmac")
        assertEquals(64, mac.length)
    }
}