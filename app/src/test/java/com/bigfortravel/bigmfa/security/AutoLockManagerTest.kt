// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockManagerTest {

    @Test
    fun `avant le delai ecoule - ne verrouille pas`() {
        val result = AutoLockManager.shouldLock(
            lastActivityTimeMillis = 0L,
            nowMillis = 60_000L, // 60s < 120s par défaut
        )
        assertFalse(result)
    }

    @Test
    fun `exactement au delai - verrouille`() {
        val result = AutoLockManager.shouldLock(
            lastActivityTimeMillis = 0L,
            nowMillis = 120_000L, // exactement 120s
        )
        assertTrue(result)
    }

    @Test
    fun `apres le delai - verrouille`() {
        val result = AutoLockManager.shouldLock(
            lastActivityTimeMillis = 0L,
            nowMillis = 150_000L, // 150s > 120s
        )
        assertTrue(result)
    }

    @Test
    fun `delai personnalise est respecte`() {
        val result = AutoLockManager.shouldLock(
            lastActivityTimeMillis = 0L,
            nowMillis = 45_000L,
            timeoutSeconds = 30L, // délai custom de 30s, déjà dépassé
        )
        assertTrue(result)
    }

    @Test
    fun `secondsRemainingBeforeLock diminue correctement avec le temps`() {
        val remaining = AutoLockManager.secondsRemainingBeforeLock(
            lastActivityTimeMillis = 0L,
            nowMillis = 100_000L, // 100s écoulées sur 120s
        )
        assertEquals(20L, remaining)
    }

    @Test
    fun `secondsRemainingBeforeLock ne devient jamais negatif`() {
        val remaining = AutoLockManager.secondsRemainingBeforeLock(
            lastActivityTimeMillis = 0L,
            nowMillis = 999_000L, // très largement dépassé
        )
        assertEquals(0L, remaining)
    }
}