package com.bigfortravel.bigmfa.otp

/**
 * Génération de codes HOTP (RFC 4226) — un code à N chiffres dérivé d'un
 * COMPTEUR incrémenté manuellement à chaque usage (pas du temps, comme
 * TOTP). Réutilise directement TotpGenerator.hmacOtp(), qui implémente
 * déjà la troncature dynamique RFC 4226 §5.3 commune aux deux formats.
 */
object HotpGenerator {

    /**
     * @param secret Le secret Base32 déjà décodé en octets bruts.
     * @param counter Le compteur actuel — DOIT être incrémenté par
     *   l'appelant après chaque génération réussie, jamais ici (cette
     *   fonction ne modifie aucun état, elle est volontairement pure).
     */
    fun generate(
        secret: ByteArray,
        counter: Long,
        digits: Int = 6,
        algorithm: TotpGenerator.Algorithm = TotpGenerator.Algorithm.SHA1,
    ): String {
        return TotpGenerator.hmacOtp(secret, counter, digits, algorithm)
    }
}