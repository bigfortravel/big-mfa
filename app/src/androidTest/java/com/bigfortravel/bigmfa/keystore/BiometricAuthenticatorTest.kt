// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.keystore

import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test nécessitant une VRAIE interaction physique — le prompt biométrique
 * système va s'afficher sur le téléphone pendant l'exécution, il faudra
 * poser le doigt sur le capteur pour que le test réussisse.
 */
@RunWith(AndroidJUnit4::class)
class BiometricAuthenticatorTest {

    private val testAlias = "test-biometric-key-${UUID.randomUUID()}"

    @After
    fun tearDown() {
        KeystoreManager.deleteKey(testAlias)
    }

    @Test
    fun realFingerprintAuthenticationSucceedsAndReturnsUsableCipher() {
        KeystoreManager.generateBiometricBoundKey(testAlias)

        val latch = CountDownLatch(1)
        var authResult: BiometricAuthenticator.AuthResult? = null

        val scenario = ActivityScenario.launch(FragmentActivity::class.java)
        scenario.onActivity { activity ->
            val authenticator = BiometricAuthenticator(activity)
            val cipher = KeystoreManager.getEncryptCipher(testAlias)

            authenticator.authenticate(cipher) { result ->
                authResult = result
                latch.countDown()
            }
        }

        // Le prompt système apparaît à peu près ici — posez le doigt maintenant.
        val completedInTime = latch.await(30, TimeUnit.SECONDS)

        assertTrue("Aucune réponse du prompt biométrique dans le délai imparti", completedInTime)
        assertTrue(
            "Résultat obtenu : $authResult — attendu : Success",
            authResult is BiometricAuthenticator.AuthResult.Success,
        )

        scenario.close()
    }
}