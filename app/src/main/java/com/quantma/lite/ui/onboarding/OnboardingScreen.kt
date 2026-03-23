package com.quantma.lite.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R

/**
 * 3-step onboarding wizard shown on first launch.
 * Phase 9 (v1.9.1)
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onNavigateToModelCatalog: () -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> OnboardingStep1()
                1 -> OnboardingStep2(onNavigateToModelCatalog = onNavigateToModelCatalog)
                2 -> OnboardingStep3()
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (step < 2) {
                Button(
                    onClick = { step++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_next))
                }
            } else {
                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinish()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_start))
                }
            }

            if (step > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.back))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step indicator dots
            Text(
                text = (0..2).joinToString("  ") { if (it == step) "●" else "○" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun OnboardingStep1() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "🤖",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingStep2(onNavigateToModelCatalog: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "📦",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_model_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_model_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onNavigateToModelCatalog,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.model_catalog))
        }
    }
}

@Composable
private fun OnboardingStep3() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "✅",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
