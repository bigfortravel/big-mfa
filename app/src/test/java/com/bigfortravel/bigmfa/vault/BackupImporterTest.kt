// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImporterTest {

    private val importer = BackupImporter()

    private fun sampleAccounts() = listOf(
        VaultAccount(
            id = "acc-1", type = "totp", name = "GitHub", issuer = "GitHub Inc.",
            secret = "JBSWY3DPEHPK3PXP", algorithm = "SHA-1", digits = 6, period = 30,
        ),
    )

    @Test
    fun `detecte correctement le format Chrome`() {
        val json = BackupV3Codec.encode(sampleAccounts(), "MotDePasse123!".toCharArray())
        val format = importer.detectFormat(json)
        assertTrue(format is BackupImporter.DetectedFormat.ChromeCompatible)
    }

    @Test
    fun `detecte correctement le format natif`() {
        val exporter = VaultExporter()
        val json = exporter.export(sampleAccounts(), "MotDePasse123!".toCharArray())
        val format = importer.detectFormat(json)
        assertTrue(format is BackupImporter.DetectedFormat.Native)
    }

    @Test
    fun `format inconnu leve une exception claire`() {
        val invalidJson = """{"format": "quelque-chose-d-autre"}"""
        assertThrows(BackupImporter.ImportException::class.java) {
            importer.detectFormat(invalidJson)
        }
    }

    @Test
    fun `import Chrome fusionne bien avec les comptes existants, de facon additive`() {
        val password = "MotDePasse123!"
        val json = BackupV3Codec.encode(sampleAccounts(), password.toCharArray())

        val existingAccount = VaultAccount(
            id = "existing-1", type = "totp", name = "Amazon", issuer = "",
            secret = "KRSXG5CTMVRXEZLU", algorithm = "SHA-1", digits = 6, period = 30,
        )

        val merged = importer.importAndMerge(json, password.toCharArray(), listOf(existingAccount))

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.name == "Amazon" })
        assertTrue(merged.any { it.name == "GitHub" })
    }

    @Test
    fun `import natif fusionne bien avec les comptes existants`() {
        val password = "MotDePasse123!"
        val exporter = VaultExporter()
        val json = exporter.export(sampleAccounts(), password.toCharArray())

        val existingAccount = VaultAccount(
            id = "existing-1", type = "totp", name = "Amazon", issuer = "",
            secret = "KRSXG5CTMVRXEZLU", algorithm = "SHA-1", digits = 6, period = 30,
        )

        val merged = importer.importAndMerge(json, password.toCharArray(), listOf(existingAccount))

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.name == "GitHub" })
    }

    @Test
    fun `doublon de nom conserve les deux entrees, jamais d'ecrasement`() {
        val password = "MotDePasse123!"
        val json = BackupV3Codec.encode(sampleAccounts(), password.toCharArray())

        // Un compte EXISTANT avec le MEME nom "GitHub" que celui importé.
        val existingWithSameName = VaultAccount(
            id = "existing-1", type = "totp", name = "GitHub", issuer = "Ancien",
            secret = "AAAAAAAAAAAAAAAA", algorithm = "SHA-1", digits = 6, period = 30,
        )

        val merged = importer.importAndMerge(json, password.toCharArray(), listOf(existingWithSameName))

        assertEquals(2, merged.size)
        val githubEntries = merged.filter { it.name == "GitHub" }
        assertEquals(2, githubEntries.size)
        // Les deux gardent des id distincts -- jamais de collision.
        assertTrue(githubEntries[0].id != githubEntries[1].id)
    }

    @Test
    fun `mauvais mot de passe pour import Chrome echoue proprement`() {
        val json = BackupV3Codec.encode(sampleAccounts(), "MotDePasseCorrect123!".toCharArray())
        assertThrows(BackupImporter.ImportException::class.java) {
            importer.importAndMerge(json, "MauvaisMotDePasse999!".toCharArray(), emptyList())
        }
    }

    @Test
    fun `mauvais mot de passe pour import natif echoue proprement`() {
        val exporter = VaultExporter()
        val json = exporter.export(sampleAccounts(), "MotDePasseCorrect123!".toCharArray())
        assertThrows(BackupImporter.ImportException::class.java) {
            importer.importAndMerge(json, "MauvaisMotDePasse999!".toCharArray(), emptyList())
        }
    }
}