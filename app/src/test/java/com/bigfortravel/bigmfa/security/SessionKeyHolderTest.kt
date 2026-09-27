// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class SessionKeyHolderTest {

    private fun randomKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    @Test
    fun `isUnlocked est false avant tout deverrouillage`() {
        val holder = SessionKeyHolder()
        assertFalse(holder.isUnlocked)
        assertNull(holder.masterKey)
    }

    @Test
    fun `setKeys rend isUnlocked vrai et expose les cles`() {
        val holder = SessionKeyHolder()
        val mk = randomKey()
        holder.setKeys(mk, randomKey(), randomKey())

        assertTrue(holder.isUnlocked)
        assertTrue(mk.contentEquals(holder.masterKey))
    }

    @Test
    fun `clear remet tout a null et isUnlocked redevient false`() {
        val holder = SessionKeyHolder()
        holder.setKeys(randomKey(), randomKey(), randomKey())
        assertTrue(holder.isUnlocked)

        holder.clear()

        assertFalse(holder.isUnlocked)
        assertNull(holder.masterKey)
        assertNull(holder.contentKey)
        assertNull(holder.hmacKey)
    }

    @Test
    fun `appeler setKeys deux fois de suite efface bien l'ancienne session avant la nouvelle`() {
        val holder = SessionKeyHolder()
        val firstMk = randomKey()
        holder.setKeys(firstMk, randomKey(), randomKey())

        val secondMk = randomKey()
        holder.setKeys(secondMk, randomKey(), randomKey())

        assertTrue(secondMk.contentEquals(holder.masterKey))
        assertFalse(firstMk.contentEquals(holder.masterKey))
    }

    @Test
    fun `clear sur une session deja vide ne plante pas`() {
        val holder = SessionKeyHolder()
        holder.clear() // aucune session posée, ne doit lever aucune exception
        assertFalse(holder.isUnlocked)
    }
}