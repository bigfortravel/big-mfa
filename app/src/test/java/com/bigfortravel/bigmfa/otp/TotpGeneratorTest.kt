// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpGeneratorTest {

    // Secret RFC 6238 : "12345678901234567890" en ASCII, SHA-1, 8 chiffres
    private val rfcSecretSha1 = "12345678901234567890".toByteArray()

    @Test
    fun `vecteur RFC 6238 - T=59 secondes donne 94287082`() {
        val code = TotpGenerator.generate(
            secret = rfcSecretSha1,
            timeMillis = 59_000L,
            digits = 8,
            algorithm = TotpGenerator.Algorithm.SHA1,
        )
        assertEquals("94287082", code)
    }

    @Test
    fun `vecteur RFC 6238 - T=1111111109 secondes donne 07081804`() {
        val code = TotpGenerator.generate(
            secret = rfcSecretSha1,
            timeMillis = 1_111_111_109_000L,
            digits = 8,
            algorithm = TotpGenerator.Algorithm.SHA1,
        )
        assertEquals("07081804", code)
    }

    @Test
    fun `vecteur RFC 6238 - T=1111111111 secondes donne 14050471`() {
        val code = TotpGenerator.generate(
            secret = rfcSecretSha1,
            timeMillis = 1_111_111_111_000L,
            digits = 8,
            algorithm = TotpGenerator.Algorithm.SHA1,
        )
        assertEquals("14050471", code)
    }

    @Test
    fun `vecteur RFC 6238 - T=1234567890 secondes donne 89005924`() {
        val code = TotpGenerator.generate(
            secret = rfcSecretSha1,
            timeMillis = 1_234_567_890_000L,
            digits = 8,
            algorithm = TotpGenerator.Algorithm.SHA1,
        )
        assertEquals("89005924", code)
    }

    @Test
    fun `meme secret et meme instant donnent toujours le meme code`() {
        val code1 = TotpGenerator.generate(rfcSecretSha1, timeMillis = 1_700_000_000_000L)
        val code2 = TotpGenerator.generate(rfcSecretSha1, timeMillis = 1_700_000_000_000L)
        assertEquals(code1, code2)
    }

    @Test
    fun `le code par defaut fait 6 chiffres`() {
        val code = TotpGenerator.generate(rfcSecretSha1)
        assertEquals(6, code.length)
    }

    @Test
    fun `secondsRemaining reste entre 1 et 30`() {
        val remaining = TotpGenerator.secondsRemaining(timeMillis = 1_700_000_015_000L, period = 30)
        assertTrue(remaining in 1..30)
    }
}