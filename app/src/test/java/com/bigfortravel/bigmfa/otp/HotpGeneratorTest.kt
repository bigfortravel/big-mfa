// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HotpGeneratorTest {

    // Secret RFC 4226 Appendix D : "12345678901234567890" en ASCII
    private val rfcSecret = "12345678901234567890".toByteArray()

    // Vecteurs officiels RFC 4226 Appendix D, comptes 0 à 9, SHA-1, 6 chiffres
    private val rfcExpectedCodes = listOf(
        "755224", "287082", "359152", "969429", "338314",
        "254676", "287922", "162583", "399871", "520489",
    )

    @Test
    fun `vecteurs RFC 4226 - comptes 0 a 9`() {
        for (counter in 0..9) {
            val code = HotpGenerator.generate(rfcSecret, counter.toLong())
            assertEquals(
                "Compteur $counter : code attendu ${rfcExpectedCodes[counter]}",
                rfcExpectedCodes[counter],
                code,
            )
        }
    }

    @Test
    fun `meme secret et meme compteur donnent toujours le meme code`() {
        val code1 = HotpGenerator.generate(rfcSecret, counter = 5L)
        val code2 = HotpGenerator.generate(rfcSecret, counter = 5L)
        assertEquals(code1, code2)
    }

    @Test
    fun `compteurs differents donnent des codes differents`() {
        val code1 = HotpGenerator.generate(rfcSecret, counter = 0L)
        val code2 = HotpGenerator.generate(rfcSecret, counter = 1L)
        assertNotEquals(code1, code2)
    }

    @Test
    fun `generate ne modifie jamais le compteur fourni (fonction pure)`() {
        val counter = 3L
        HotpGenerator.generate(rfcSecret, counter)
        HotpGenerator.generate(rfcSecret, counter)
        // Si generate() avait un effet de bord sur le compteur, ce
        // deuxième appel donnerait un résultat différent du premier —
        // déjà vérifié par le test précédent, celui-ci documente
        // explicitement l'intention "fonction pure".
        val code = HotpGenerator.generate(rfcSecret, counter)
        assertEquals(rfcExpectedCodes[3], code)
    }
}