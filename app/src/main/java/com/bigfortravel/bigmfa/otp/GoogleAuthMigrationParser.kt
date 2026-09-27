// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.otp

import android.util.Base64
import java.net.URLDecoder

/**
 * Décode le format d'export/migration de Google Authenticator
 * (otpauth-migration://offline?data=...), un schéma Protocol Buffers
 * simple et fixe, documenté publiquement par la communauté (jamais
 * officiellement publié par Google, mais stable depuis des années --
 * utilisé par plusieurs projets open source compatibles).
 *
 * Décodage manuel du format binaire (varint + length-delimited),
 * volontairement sans dépendance à une bibliothèque Protocol Buffers
 * complète, disproportionnée pour un schéma aussi simple et figé.
 */
object GoogleAuthMigrationParser {

    data class MigratedAccount(
        val secret: ByteArray,
        val name: String,
        val issuer: String,
        val algorithm: TotpGenerator.Algorithm,
        val digits: Int,
        val isHotp: Boolean,
        val counter: Long,
    )

    class MigrationParseException(message: String) : Exception(message)

    fun isMigrationUri(uri: String): Boolean = uri.startsWith("otpauth-migration://")

    fun parse(uri: String): List<MigratedAccount> {
        val dataParam = uri.substringAfter("data=", "")
        if (dataParam.isEmpty()) {
            throw MigrationParseException("Paramètre data absent")
        }
        val decodedUrl = URLDecoder.decode(dataParam, "UTF-8")
        val bytes = try {
            Base64.decode(decodedUrl, Base64.DEFAULT)
        } catch (_: Exception) {
            throw MigrationParseException("Décodage Base64 invalide")
        }

        return parseMigrationPayload(bytes)
    }

    private fun parseMigrationPayload(bytes: ByteArray): List<MigratedAccount> {
        val reader = ProtoReader(bytes)
        val accounts = mutableListOf<MigratedAccount>()

        while (reader.hasNext()) {
            val (fieldNumber, wireType) = reader.readTag()
            if (fieldNumber == 1 && wireType == WIRE_TYPE_LENGTH_DELIMITED) {
                val otpParamBytes = reader.readLengthDelimited()
                accounts.add(parseOtpParameters(otpParamBytes))
            } else {
                reader.skipField(wireType)
            }
        }

        return accounts
    }

    private fun parseOtpParameters(bytes: ByteArray): MigratedAccount {
        val reader = ProtoReader(bytes)

        var secret: ByteArray = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithmCode = 1
        var digitsCode = 1
        var typeCode = 2
        var counter = 0L

        while (reader.hasNext()) {
            val (fieldNumber, wireType) = reader.readTag()
            when (fieldNumber) {
                1 -> secret = reader.readLengthDelimited()
                2 -> name = String(reader.readLengthDelimited(), Charsets.UTF_8)
                3 -> issuer = String(reader.readLengthDelimited(), Charsets.UTF_8)
                4 -> algorithmCode = reader.readVarint().toInt()
                5 -> digitsCode = reader.readVarint().toInt()
                6 -> typeCode = reader.readVarint().toInt()
                7 -> counter = reader.readVarint()
                else -> reader.skipField(wireType)
            }
        }

        val algorithm = when (algorithmCode) {
            2 -> TotpGenerator.Algorithm.SHA256
            3 -> TotpGenerator.Algorithm.SHA512
            else -> TotpGenerator.Algorithm.SHA1
        }
        val digits = if (digitsCode == 2) 8 else 6
        val isHotp = typeCode == 1

        return MigratedAccount(secret, name, issuer, algorithm, digits, isHotp, counter)
    }

    private const val WIRE_TYPE_VARINT = 0
    private const val WIRE_TYPE_LENGTH_DELIMITED = 2

    /**
     * Lecteur minimal du format binaire Protocol Buffers -- uniquement
     * les deux types de champs utilisés par ce schéma précis (varint et
     * length-delimited), rien de plus.
     */
    private class ProtoReader(private val bytes: ByteArray) {
        private var position = 0

        fun hasNext(): Boolean = position < bytes.size

        fun readTag(): Pair<Int, Int> {
            val tag = readVarint()
            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 0x07).toInt()
            return fieldNumber to wireType
        }

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val byte = bytes[position].toLong() and 0xFF
                position++
                result = result or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0L) break
                shift += 7
            }
            return result
        }

        fun readLengthDelimited(): ByteArray {
            val length = readVarint().toInt()
            val result = bytes.copyOfRange(position, position + length)
            position += length
            return result
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                WIRE_TYPE_VARINT -> readVarint()
                WIRE_TYPE_LENGTH_DELIMITED -> readLengthDelimited()
                else -> throw MigrationParseException("Type de champ non géré : $wireType")
            }
        }
    }
}