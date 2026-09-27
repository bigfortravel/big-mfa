// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.keystore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Premier test androidTest du projet — nécessite un vrai appareil/
 * émulateur pour s'exécuter, contrairement à tous les tests précédents.
 *
 * Noms de fonctions SANS espaces (contrairement aux tests JVM habituels)
 * — les tests instrumentés Android sont compilés au format DEX, qui
 * n'autorise pas les identifiants avec espaces entre backticks.
 */
@RunWith(AndroidJUnit4::class)
class HardwareSecurityCheckerTest {

    @Test
    fun deviceOffersAtLeastTeeLevelHardwareSecurity() {
        val level = HardwareSecurityChecker.detectSecurityLevel()
        assertTrue(
            "Niveau détecté : $level — devrait être TEE ou STRONGBOX sur un vrai téléphone récent",
            HardwareSecurityChecker.meetsMinimumRequirement(level),
        )
    }

    @Test
    fun detectSecurityLevelNeverCrashesAndReturnsExpectedValue() {
        val level = HardwareSecurityChecker.detectSecurityLevel()
        assertTrue(
            level == HardwareSecurityChecker.SecurityLevel.TEE ||
                    level == HardwareSecurityChecker.SecurityLevel.STRONGBOX ||
                    level == HardwareSecurityChecker.SecurityLevel.SOFTWARE_ONLY,
        )
    }
}