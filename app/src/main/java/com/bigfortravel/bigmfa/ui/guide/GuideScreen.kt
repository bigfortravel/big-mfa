// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.guide

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigfortravel.bigmfa.R

@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.guide_tab_installation),
        stringResource(R.string.guide_tab_usage),
        stringResource(R.string.guide_tab_features),
    )

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> InstallationGuide()
            1 -> UsageGuide()
            else -> FeaturesGuide()
        }
    }
}

@Composable
private fun InstallationGuide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        GuideSection(stringResource(R.string.guide_install_step1_title), stringResource(R.string.guide_install_step1_body))
        GuideSection(stringResource(R.string.guide_install_step2_title), stringResource(R.string.guide_install_step2_body))
        GuideSection(stringResource(R.string.guide_install_step3_title), stringResource(R.string.guide_install_step3_body))
        GuideSection(stringResource(R.string.guide_install_step4_title), stringResource(R.string.guide_install_step4_body))
    }
}

@Composable
private fun UsageGuide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        GuideSection(stringResource(R.string.guide_usage_step1_title), stringResource(R.string.guide_usage_step1_body))
        GuideSection(stringResource(R.string.guide_usage_step2_title), stringResource(R.string.guide_usage_step2_body))
        GuideSection(stringResource(R.string.guide_usage_step3_title), stringResource(R.string.guide_usage_step3_body))
        GuideSection(stringResource(R.string.guide_usage_step4_title), stringResource(R.string.guide_usage_step4_body))
        GuideSection(stringResource(R.string.guide_usage_step5_title), stringResource(R.string.guide_usage_step5_body))
    }
}

@Composable
private fun FeaturesGuide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.guide_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        GuideSection(stringResource(R.string.guide_codes_title), stringResource(R.string.guide_codes_body))
        GuideSection(stringResource(R.string.guide_decoder_title), stringResource(R.string.guide_decoder_body))
        GuideSection(stringResource(R.string.guide_key_title), stringResource(R.string.guide_key_body))
        GuideSection(stringResource(R.string.guide_add_title), stringResource(R.string.guide_add_body))
        GuideSection(stringResource(R.string.guide_google_import_title), stringResource(R.string.guide_google_import_body))
        GuideSection(stringResource(R.string.guide_biometric_title), stringResource(R.string.guide_biometric_body))
        GuideSection(stringResource(R.string.guide_autofill_title), stringResource(R.string.guide_autofill_body))
        GuideSection(stringResource(R.string.guide_backup_title), stringResource(R.string.guide_backup_body))
        GuideSection(stringResource(R.string.guide_security_title), stringResource(R.string.guide_security_body))
        GuideSection(stringResource(R.string.guide_language_title), stringResource(R.string.guide_language_body))
    }
}

@Composable
private fun GuideSection(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}