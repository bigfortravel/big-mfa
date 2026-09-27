// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.autofill

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.keystore.KeystoreManager
import com.bigfortravel.bigmfa.otp.TotpGenerator
import com.bigfortravel.bigmfa.ui.theme.BigMFATheme
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import org.bouncycastle.util.encoders.Base32
import java.io.File

/**
 * Activité déclenchée par le PendingIntent verrouillé du service
 * Autofill -- Option B améliorée.
 *
 * Reste invisible (aucun contenu affiché) pendant l'étape biométrique.
 * Devient visible SEULEMENT après déverrouillage réussi, et seulement
 * si un choix humain est nécessaire (plusieurs comptes correspondants,
 * ou aucun -- recherche manuelle). Ne lit ni ne déchiffre jamais rien
 * avant le succès biométrique.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_AUTOFILL_ID = "extra_autofill_id"
        const val EXTRA_CALLING_PACKAGE = "extra_calling_package"
        const val EXTRA_DECLARED_WEB_DOMAIN = "extra_declared_web_domain"

        /**
         * Liste des navigateurs connus -- seul un domaine web déclaré par
         * l'un de ces packages est jugé crédible. Un domaine déclaré par
         * n'importe quelle autre app est ignoré, car rien ne garantit
         * qu'une app tierce ne ment pas sur le site qu'elle prétend être.
         */
        private val TRUSTED_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.android.browser",
        )
    }

    private lateinit var autofillId: AutofillId
    private lateinit var callingPackage: String
    private var trustedWebDomain: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getParcelableExtra<AutofillId>(EXTRA_AUTOFILL_ID)
        val pkg = intent.getStringExtra(EXTRA_CALLING_PACKAGE)
        if (id == null || pkg == null) {
            cancelAndFinish()
            return
        }
        autofillId = id
        callingPackage = pkg

        val declaredDomain = intent.getStringExtra(EXTRA_DECLARED_WEB_DOMAIN)
        trustedWebDomain = if (declaredDomain != null && callingPackage in TRUSTED_BROWSER_PACKAGES) {
            declaredDomain
        } else {
            null
        }

        startBiometricUnlock()
    }

    private fun startBiometricUnlock() {
        val repository = VaultRepository(File(filesDir, "vault.json"))
        val unlocker = VaultUnlocker(repository)

        if (!unlocker.hasBiometricSlot()) {
            // Pas de biométrie configurée -- Autofill reste indisponible
            // pour ce coffre tant que l'utilisateur ne l'active pas dans
            // l'application, jamais de repli par mot de passe ici.
            cancelAndFinish()
            return
        }

        val slotInfo = unlocker.getBiometricSlotInfo()
        val cipher = try {
            slotInfo?.let { KeystoreManager.getDecryptCipher(it.alias, it.nonce) }
        } catch (_: Exception) {
            null
        }

        if (cipher == null) {
            cancelAndFinish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val resultCipher = result.cryptoObject?.cipher
                    if (resultCipher == null) {
                        cancelAndFinish()
                        return
                    }

                    val unlockResult = unlocker.unlockWithBiometric(resultCipher)
                    if (unlockResult !is VaultUnlocker.UnlockResult.Success) {
                        cancelAndFinish()
                        return
                    }

                    AutofillSessionHolder.currentSession = unlockResult
                    onUnlockSuccess(unlockResult.accounts)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    cancelAndFinish()
                }

                override fun onAuthenticationFailed() {
                    // Le prompt système reste ouvert, rien à faire ici.
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.autofill_biometric_prompt_title))
            .setSubtitle(getString(R.string.autofill_biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.autofill_biometric_prompt_cancel))
            .build()

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * Recherche uniquement APRÈS déverrouillage réussi -- jamais avant.
     * Ne sélectionne jamais un compte par simple ressemblance de nom :
     * seule une correspondance exacte de package OU de domaine web
     * digne de confiance compte.
     */
    private fun onUnlockSuccess(accounts: List<VaultAccount>) {
        val matches = accounts.filter { account ->
            account.autofillPackageName == callingPackage ||
                    (trustedWebDomain != null && account.autofillWebDomain == trustedWebDomain)
        }

        when {
            matches.size == 1 -> generateCodeAndReturn(matches.first())
            matches.size > 1 -> showAccountPicker(matches, accounts)
            else -> showNoMatchScreen(accounts)
        }
    }

    private fun showAccountPicker(matches: List<VaultAccount>, allAccounts: List<VaultAccount>) {
        setContent {
            BigMFATheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AccountPickerScreen(
                        accounts = matches,
                        onAccountChosen = { generateCodeAndReturn(it) },
                        onSearchInsteadRequested = { showNoMatchScreen(allAccounts) },
                        onCancel = { cancelAndFinish() },
                    )
                }
            }
        }
    }

    private fun showNoMatchScreen(allAccounts: List<VaultAccount>) {
        setContent {
            BigMFATheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoMatchScreen(
                        allAccounts = allAccounts,
                        onAccountChosen = { generateCodeAndReturn(it) },
                        onCancel = { cancelAndFinish() },
                    )
                }
            }
        }
    }

    private fun generateCodeAndReturn(account: VaultAccount) {
        val code = try {
            val secretBytes = Base32.decode(account.secret.toByteArray(Charsets.US_ASCII))
            TotpGenerator.generate(secretBytes)
        } catch (_: Exception) {
            null
        }

        if (code == null) {
            cancelAndFinish()
            return
        }

        val replyDataset = Dataset.Builder()
            .setValue(
                autofillId,
                AutofillValue.forText(code),
                RemoteViews(packageName, R.layout.autofill_suggestion).apply {
                    setTextViewText(R.id.autofill_suggestion_text, account.name)
                },
            )
            .build()

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, replyDataset)
        }
        setResult(RESULT_OK, replyIntent)
        finish()
    }

    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }
}

@Composable
private fun AccountPickerScreen(
    accounts: List<VaultAccount>,
    onAccountChosen: (VaultAccount) -> Unit,
    onSearchInsteadRequested: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.autofill_picker_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(accounts, key = { it.id }) { account ->
                TextButton(onClick = { onAccountChosen(account) }, modifier = Modifier.fillMaxWidth()) {
                    Text(account.name, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        TextButton(onClick = onSearchInsteadRequested) {
            Text(stringResource(R.string.autofill_search_manually_button))
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.autofill_cancel_button))
        }
    }
}

@Composable
private fun NoMatchScreen(
    allAccounts: List<VaultAccount>,
    onAccountChosen: (VaultAccount) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        allAccounts
    } else {
        allAccounts.filter { it.name.contains(query, ignoreCase = true) || it.issuer.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.autofill_no_match_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.autofill_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { account ->
                TextButton(onClick = { onAccountChosen(account) }, modifier = Modifier.fillMaxWidth()) {
                    Text(account.name, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.autofill_cancel_button))
        }
    }
}