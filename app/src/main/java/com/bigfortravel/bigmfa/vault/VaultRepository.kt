// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lecture/écriture du fichier de coffre au format JSON défini dans
 * VaultFile.kt. Reçoit un java.io.File (pas un Context Android) pour
 * rester testable en JVM pur — l'appelant réel fournira plus tard
 * File(context.filesDir, "vault.json").
 *
 * Aucune logique de sécurité ici (ni déchiffrement, ni vérification
 * d'intégrité) — uniquement la sérialisation JSON, volontairement séparée
 * de VaultIntegrityChecker.
 */
class VaultRepository(private val vaultFile: File) {

    fun exists(): Boolean = vaultFile.exists()

    fun read(): VaultFile {
        val json = JSONObject(vaultFile.readText(Charsets.UTF_8))
        return jsonToVaultFile(json)
    }

    fun write(vault: VaultFile) {
        val json = vaultFileToJson(vault)
        vaultFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    fun delete(): Boolean = vaultFile.exists() && vaultFile.delete()
    fun canonicalBytesForHmac(vault: VaultFile): ByteArray {
        val json = vaultFileToJson(vault)
        json.remove("vault_hmac")
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    // --- Sérialisation ---

    private fun vaultFileToJson(vault: VaultFile): JSONObject {
        val json = JSONObject()
        json.put("format", vault.format)
        json.put("version", vault.version)
        json.put("created_at", vault.createdAt)
        json.put("modified_at", vault.modifiedAt)

        val slotsArray = JSONArray()
        for (slot in vault.slots) {
            slotsArray.put(slotToJson(slot))
        }
        json.put("slots", slotsArray)

        json.put("content", vault.content)
        json.put("content_nonce", vault.contentNonce)
        json.put("content_tag", vault.contentTag)
        json.put("vault_hmac", vault.vaultHmac)
        return json
    }

    private fun slotToJson(slot: VaultSlot): JSONObject {
        val json = JSONObject()
        json.put("uuid", slot.uuid)
        json.put("wrapped_key", slot.wrappedKey)
        json.put("nonce", slot.nonce)
        json.put("tag", slot.tag)
        when (slot) {
            is VaultSlot.PasswordSlot -> {
                json.put("type", "password")
                json.put("salt", slot.salt)
                json.put("memory_kib", slot.memoryKiB)
                json.put("iterations", slot.iterations)
                json.put("parallelism", slot.parallelism)
            }
            is VaultSlot.BiometricSlot -> {
                json.put("type", "biometric")
                json.put("keystore_alias", slot.keystoreAlias)
            }
        }
        return json
    }

    // --- Désérialisation ---

    private fun jsonToVaultFile(json: JSONObject): VaultFile {
        val slotsArray = json.getJSONArray("slots")
        val slots = mutableListOf<VaultSlot>()
        for (i in 0 until slotsArray.length()) {
            slots.add(jsonToSlot(slotsArray.getJSONObject(i)))
        }

        return VaultFile(
            format = json.getString("format"),
            version = json.getInt("version"),
            createdAt = json.getString("created_at"),
            modifiedAt = json.getString("modified_at"),
            slots = slots,
            content = json.getString("content"),
            contentNonce = json.getString("content_nonce"),
            contentTag = json.getString("content_tag"),
            vaultHmac = json.getString("vault_hmac"),
        )
    }

    private fun jsonToSlot(json: JSONObject): VaultSlot {
        val uuid = json.getString("uuid")
        val wrappedKey = json.getString("wrapped_key")
        val nonce = json.getString("nonce")
        val tag = json.getString("tag")
        return when (json.getString("type")) {
            "password" -> VaultSlot.PasswordSlot(
                uuid = uuid, wrappedKey = wrappedKey, nonce = nonce, tag = tag,
                salt = json.getString("salt"),
                memoryKiB = json.getInt("memory_kib"),
                iterations = json.getInt("iterations"),
                parallelism = json.getInt("parallelism"),
            )
            "biometric" -> VaultSlot.BiometricSlot(
                uuid = uuid, wrappedKey = wrappedKey, nonce = nonce, tag = tag,
                keystoreAlias = json.getString("keystore_alias"),
            )
            else -> throw IllegalArgumentException("Type de slot inconnu")
        }
    }
}