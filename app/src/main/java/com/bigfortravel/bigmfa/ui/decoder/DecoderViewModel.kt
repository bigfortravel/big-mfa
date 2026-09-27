// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.decoder

import androidx.lifecycle.ViewModel
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.otp.OtpAuthUriParser
import com.bigfortravel.bigmfa.otp.TotpGenerator
import com.bigfortravel.bigmfa.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bouncycastle.util.encoders.Base32

class DecoderViewModel : ViewModel() {

    private val _secretText = MutableStateFlow("")
    val secretText: StateFlow<String> = _secretText.asStateFlow()

    private val _generatedCode = MutableStateFlow<String?>(null)
    val generatedCode: StateFlow<String?> = _generatedCode.asStateFlow()

    // UiText au lieu de String -- traduit uniquement au moment de
    // l'affichage, jamais figé dans une langue au moment de l'émission.
    private val _errorMessage = MutableStateFlow<UiText?>(null)
    val errorMessage: StateFlow<UiText?> = _errorMessage.asStateFlow()

    fun onSecretTextChanged(text: String) {
        _secretText.value = text
        _generatedCode.value = null
        _errorMessage.value = null
    }

    fun onQrImageDecoded(rawValue: String) {
        val extractedSecret = try {
            Base32.toBase32String(OtpAuthUriParser.parse(rawValue).secret)
        } catch (_: Exception) {
            rawValue.trim()
        }
        _secretText.value = extractedSecret
        _errorMessage.value = null
        generateCode()
    }

    fun generateCode() {
        val cleaned = _secretText.value.trim().uppercase().replace(" ", "")
        if (cleaned.isEmpty()) {
            _errorMessage.value = UiText.Resource(R.string.decoder_secret_empty_error)
            _generatedCode.value = null
            return
        }
        try {
            val secretBytes = Base32.decode(cleaned.toByteArray(Charsets.US_ASCII))
            _generatedCode.value = TotpGenerator.generate(secretBytes)
            _errorMessage.value = null
        } catch (_: Exception) {
            _generatedCode.value = null
            _errorMessage.value = UiText.Resource(R.string.decoder_secret_invalid_error)
        }
    }
}