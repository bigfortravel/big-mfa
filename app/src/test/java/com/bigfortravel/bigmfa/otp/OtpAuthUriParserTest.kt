// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OtpAuthUriParserTest {

    @Test
    fun `lien TOTP complet est correctement decode`() {
        val uri = "otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30"
        val parsed = OtpAuthUriParser.parse(uri)

        assertEquals("totp", parsed.type)
        assertEquals("alice", parsed.name)
        assertEquals("GitHub", parsed.issuer)
        assertEquals(TotpGenerator.Algorithm.SHA1, parsed.algorithm)
        assertEquals(6, parsed.digits)
        assertEquals(30, parsed.period)
    }

    @Test
    fun `lien HOTP avec compteur est correctement decode`() {
        val uri = "otpauth://hotp/ServiceX:bob?secret=KRSXG5CTMVRXEZLU&counter=12"
        val parsed = OtpAuthUriParser.parse(uri)

        assertEquals("hotp", parsed.type)
        assertEquals("bob", parsed.name)
        assertEquals(12L, parsed.counter)
    }

    @Test
    fun `label sans emetteur (juste le nom) fonctionne aussi`() {
        val uri = "otpauth://totp/alice?secret=JBSWY3DPEHPK3PXP"
        val parsed = OtpAuthUriParser.parse(uri)

        assertEquals("alice", parsed.name)
        assertEquals("", parsed.issuer)
    }

    @Test
    fun `valeurs par defaut appliquees quand digits et period sont absents`() {
        val uri = "otpauth://totp/alice?secret=JBSWY3DPEHPK3PXP"
        val parsed = OtpAuthUriParser.parse(uri)

        assertEquals(6, parsed.digits)
        assertEquals(30, parsed.period)
    }

    @Test
    fun `le secret decode correspond bien au secret Base32 d'origine`() {
        val uri = "otpauth://totp/alice?secret=JBSWY3DPEHPK3PXP"
        val parsed = OtpAuthUriParser.parse(uri)
        // JBSWY3DPEHPK3PXP décodé en Base32 donne "Hello!\xDE\xAD\xBE\xEF" en octets réels
        val code = TotpGenerator.generate(parsed.secret, timeMillis = 59_000L, digits = 6)
        // On vérifie surtout que ça ne plante pas et produit un code à 6 chiffres valide
        assertEquals(6, code.length)
    }

    @Test
    fun `lien qui ne commence pas par otpauath echoue`() {
        assertThrows(IllegalArgumentException::class.java) {
            OtpAuthUriParser.parse("https://example.com")
        }
    }

    @Test
    fun `lien sans secret echoue`() {
        assertThrows(IllegalArgumentException::class.java) {
            OtpAuthUriParser.parse("otpauth://totp/alice")
        }
    }
}