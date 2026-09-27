// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.keystore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Teste ce qui est vérifiable SANS authentification biométrique réelle
 * (génération, existence, suppression). Le chiffrement/déchiffrement
 * réel via une vraie clé biométrique sera testé dans
 * BiometricAuthenticatorTest, avec une vraie interaction utilisateur.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreManagerTest {

    private val testAlias = "test-key-${UUID.randomUUID()}"

    @After
    fun tearDown() {
        KeystoreManager.deleteKey(testAlias)
    }

    @Test
    fun keyDoesNotExistBeforeGeneration() {
        assertFalse(KeystoreManager.keyExists(testAlias))
    }

    @Test
    fun keyExistsAfterGeneration() {
        KeystoreManager.generateBiometricBoundKey(testAlias)
        assertTrue(KeystoreManager.keyExists(testAlias))
    }

    @Test
    fun deleteKeyRemovesItProperly() {
        KeystoreManager.generateBiometricBoundKey(testAlias)
        assertTrue(KeystoreManager.keyExists(testAlias))

        KeystoreManager.deleteKey(testAlias)
        assertFalse(KeystoreManager.keyExists(testAlias))
    }

    @Test
    fun deletingNonExistentKeyDoesNotCrash() {
        KeystoreManager.deleteKey("alias-qui-n-existe-pas-${UUID.randomUUID()}")
        // Aucune exception ne doit être levée.
    }
}