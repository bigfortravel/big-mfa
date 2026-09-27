// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/**
 * Dérivation de clé Argon2id, avec paliers mémoire selon la RAM
 * disponible (Confortable / Standard / Contraint).
 *
 * IMPORTANT (découvert par un vrai crash sur appareil) : utilise
 * Argon2BytesGenerator de Bouncy Castle -- une implémentation en Java
 * pur -- et NON argon2-jvm (retiré). argon2-jvm s'appuie sur JNA pour
 * charger une bibliothèque native compilée, un mécanisme incompatible
 * avec le bac à sable strict d'Android (EACCES au moment de l'extraction
 * du fichier natif). Bouncy Castle, déjà utilisé pour AES-256-GCM-SIV,
 * fournit sa propre implémentation Argon2 pure JVM, sans ce problème.
 */
object Argon2KeyDerivation {

    enum class MemoryTier(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
        CONFORTABLE(memoryKiB = 65536, iterations = 3, parallelism = 4),  // 64 Mo
        STANDARD(memoryKiB = 32768, iterations = 3, parallelism = 2),     // 32 Mo
        CONTRAINT(memoryKiB = 19456, iterations = 3, parallelism = 1),    // 19 Mo — minimum OWASP
    }

    private const val SALT_LENGTH_BYTES = 32
    private const val DERIVED_KEY_LENGTH_BYTES = 32 // 256 bits, pour AES-256

    fun detectTierForThisDevice(availableRamBytes: Long): MemoryTier {
        val availableRamMb = availableRamBytes / (1024 * 1024)
        return when {
            availableRamMb >= 4096 -> MemoryTier.CONFORTABLE
            availableRamMb >= 2048 -> MemoryTier.STANDARD
            else -> MemoryTier.CONTRAINT
        }
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun derive(password: CharArray, salt: ByteArray, memoryKiB: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(parallelism)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val output = ByteArray(DERIVED_KEY_LENGTH_BYTES)
        try {
            generator.generateBytes(password, output)
        } finally {
            password.fill('\u0000')
        }
        return output
    }

    fun derive(password: CharArray, salt: ByteArray, tier: MemoryTier): ByteArray =
        derive(password, salt, tier.memoryKiB, tier.iterations, tier.parallelism)
}