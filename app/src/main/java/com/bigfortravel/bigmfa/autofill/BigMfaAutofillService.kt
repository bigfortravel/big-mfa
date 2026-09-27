// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.bigfortravel.bigmfa.R

/**
 * Service Autofill de Big MFA -- Option B améliorée, détection finale.
 *
 * onFillRequest ne lit jamais le coffre et ne fait aucune correspondance
 * de compte à ce stade -- il détecte tous les champs pertinents (mot de
 * passe et/ou code MFA, potentiellement plusieurs sur un même écran) et
 * propose une suggestion générique verrouillée sur CHACUN d'eux. Toute
 * la vraie logique se déroule après clic, dans AutofillAuthActivity.
 *
 * Détection à plusieurs niveaux, validée sur des sites réels distincts :
 * 1. Indices Autofill natifs Android (smsOTPCode, oneTimeCode, password)
 * 2. inputType Android natif (apps non-web)
 * 3. Attribut HTML type="password" (Yahoo, Google, Namebay)
 * 4. Attribut HTML autocomplete="one-time-code" (norme WHATWG standard)
 * 5. Mots-clés dans name/id/label/placeholder HTML, restreint aux nœuds
 *    <input> uniquement (formulaire TOTP générique, IONOS "passcode",
 *    Namebay "TxtTwoFACode")
 *
 * Propose TOUS les champs pertinents trouvés (pas seulement le premier)
 * -- un formulaire comme Namebay affiche mot de passe ET code 2FA sur le
 * même écran ; s'arrêter au premier trouvé masquait le second.
 */
@RequiresApi(Build.VERSION_CODES.O)
class BigMfaAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val callingPackage = structure.activityComponent?.packageName
        if (callingPackage == null) {
            callback.onSuccess(null)
            return
        }

        val targetFieldIds = findAllAutofillableFieldIds(structure)
        if (targetFieldIds.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val declaredWebDomain = findWebDomain(structure)
        val responseBuilder = FillResponse.Builder()
        targetFieldIds.forEach { fieldId ->
            responseBuilder.addDataset(buildGenericLockedDataset(fieldId, callingPackage, declaredWebDomain))
        }

        callback.onSuccess(responseBuilder.build())
    }

    private fun buildGenericLockedDataset(
        fieldId: AutofillId,
        callingPackage: String,
        declaredWebDomain: String?,
    ): Dataset {
        val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_AUTOFILL_ID, fieldId)
            putExtra(AutofillAuthActivity.EXTRA_CALLING_PACKAGE, callingPackage)
            declaredWebDomain?.let { putExtra(AutofillAuthActivity.EXTRA_DECLARED_WEB_DOMAIN, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (callingPackage + fieldId.toString()).hashCode(),
            authIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.autofill_suggestion_text, getString(R.string.autofill_generic_suggestion))
        }

        return Dataset.Builder()
            .setValue(fieldId, null, presentation)
            .setAuthentication(pendingIntent.intentSender)
            .build()
    }

    private fun findAllAutofillableFieldIds(structure: AssistStructure): List<AutofillId> {
        val results = mutableListOf<AutofillId>()
        for (i in 0 until structure.windowNodeCount) {
            collectRelevantFields(structure.getWindowNodeAt(i).rootViewNode, results)
        }
        return results
    }

    private fun collectRelevantFields(node: AssistStructure.ViewNode, results: MutableList<AutofillId>) {
        if (node.autofillId != null && isRelevantField(node)) {
            results.add(node.autofillId!!)
        }
        for (i in 0 until node.childCount) {
            collectRelevantFields(node.getChildAt(i), results)
        }
    }

    private fun isRelevantField(node: AssistStructure.ViewNode): Boolean {
        val htmlTag = node.htmlInfo?.tag
        if (htmlTag != null && htmlTag != "input") return false

        val hints = node.autofillHints
        if (hints != null) {
            val hasRelevantHint = hints.any {
                it.equals("smsOTPCode", ignoreCase = true) ||
                        it.equals("oneTimeCode", ignoreCase = true) ||
                        it.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true)
            }
            if (hasRelevantHint) return true
        }

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordVariation = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        if (isPasswordVariation) return true

        val htmlAttributes = node.htmlInfo?.attributes
        val htmlType = htmlAttributes?.firstOrNull { it.first == "type" }?.second
        if (htmlType.equals("password", ignoreCase = true)) return true

        val htmlAutocomplete = htmlAttributes?.firstOrNull { it.first == "autocomplete" }?.second
        if (htmlAutocomplete.equals("one-time-code", ignoreCase = true)) return true

        val otpKeywords = listOf("totp", "otp", "2fa", "twofa", "verification", "mfa-code", "authcode", "passcode")
        val relevantAttributeValues = htmlAttributes
            ?.filter { it.first == "name" || it.first == "id" || it.first == "label" || it.first == "placeholder" }
            ?.map { it.second?.toString()?.lowercase() ?: "" }
            ?: emptyList()

        return relevantAttributeValues.any { value -> otpKeywords.any { keyword -> value.contains(keyword) } }
    }

    private fun findWebDomain(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val result = searchWebDomain(structure.getWindowNodeAt(i).rootViewNode)
            if (result != null) return result
        }
        return null
    }

    private fun searchWebDomain(node: AssistStructure.ViewNode): String? {
        node.webDomain?.let { return it }
        for (i in 0 until node.childCount) {
            val result = searchWebDomain(node.getChildAt(i))
            if (result != null) return result
        }
        return null
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onFailure(null)
    }
}