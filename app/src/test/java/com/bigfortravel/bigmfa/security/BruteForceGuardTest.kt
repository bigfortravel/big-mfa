// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BruteForceGuardTest {

    @Test
    fun `moins de 5 echecs - toujours autorise`() {
        val state = BruteForceGuard.BruteForceState(failedAttempts = 4, lastFailureTimeMillis = 1_000_000L)
        val result = BruteForceGuard.check(state, nowMillis = 1_000_001L)
        assertTrue(result is BruteForceGuard.CheckResult.Allowed)
    }

    @Test
    fun `exactement 5 echecs - bloque immediatement apres l'echec`() {
        val state = BruteForceGuard.BruteForceState(failedAttempts = 5, lastFailureTimeMillis = 1_000_000L)
        val result = BruteForceGuard.check(state, nowMillis = 1_000_001L)
        assertTrue(result is BruteForceGuard.CheckResult.Blocked)
    }

    @Test
    fun `5 echecs - autorise a nouveau apres 30 secondes ecoulees`() {
        val state = BruteForceGuard.BruteForceState(failedAttempts = 5, lastFailureTimeMillis = 0L)
        val result = BruteForceGuard.check(state, nowMillis = 31_000L) // 31s plus tard
        assertTrue(result is BruteForceGuard.CheckResult.Allowed)
    }

    @Test
    fun `6 echecs - le delai double bien a 60 secondes`() {
        val state = BruteForceGuard.BruteForceState(failedAttempts = 6, lastFailureTimeMillis = 0L)

        val tooEarly = BruteForceGuard.check(state, nowMillis = 45_000L) // 45s < 60s
        assertTrue(tooEarly is BruteForceGuard.CheckResult.Blocked)

        val nowAllowed = BruteForceGuard.check(state, nowMillis = 61_000L) // 61s > 60s
        assertTrue(nowAllowed is BruteForceGuard.CheckResult.Allowed)
    }

    @Test
    fun `recordFailure incremente bien le compteur et met a jour l'horodatage`() {
        val initial = BruteForceGuard.BruteForceState(failedAttempts = 2, lastFailureTimeMillis = 1000L)
        val after = BruteForceGuard.recordFailure(initial, nowMillis = 5000L)

        assertEquals(3, after.failedAttempts)
        assertEquals(5000L, after.lastFailureTimeMillis)
    }

    @Test
    fun `recordSuccess remet completement a zero`() {
        val after = BruteForceGuard.recordSuccess()
        assertEquals(0, after.failedAttempts)
        assertEquals(0L, after.lastFailureTimeMillis)
    }

    @Test
    fun `Blocked indique bien un nombre de secondes restantes positif`() {
        val state = BruteForceGuard.BruteForceState(failedAttempts = 5, lastFailureTimeMillis = 0L)
        val result = BruteForceGuard.check(state, nowMillis = 10_000L) as BruteForceGuard.CheckResult.Blocked
        assertTrue(result.remainingSeconds > 0)
    }
}