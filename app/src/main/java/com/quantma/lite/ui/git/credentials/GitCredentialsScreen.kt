package com.quantma.lite.ui.git.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import com.quantma.lite.ui.common.BiometricHelper

/** Безопасный поиск FragmentActivity через цепочку ContextWrapper */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCredentialsScreen(
    onBack: () -> Unit,
    viewModel: GitCredentialsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricHelper = remember(activity) {
        activity?.let { BiometricHelper(it) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.credentials_saved)
    val savedEvent by viewModel.savedEvent.collectAsState()

    // If no activity or device has no biometric/PIN — skip auth gate entirely
    var authenticated by remember {
        mutableStateOf(biometricHelper == null || !biometricHelper.canAuthenticate())
    }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!authenticated) {
            biometricHelper?.authenticate(
                title = context.getString(R.string.biometric_prompt_title),
                subtitle = context.getString(R.string.biometric_prompt_subtitle),
                onSuccess = { authenticated = true },
                onError = { err -> authError = err }
            )
        }
    }

    LaunchedEffect(savedEvent) {
        if (savedEvent) {
            snackbarHostState.showSnackbar(savedMsg)
            viewModel.clearSavedEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_credentials_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (authenticated) {
                GitCredentialsForm(viewModel = viewModel)
            } else {
                BiometricGate(
                    error = authError,
                    onRetry = {
                        authError = null
                        biometricHelper?.authenticate(
                            title = context.getString(R.string.biometric_prompt_title),
                            subtitle = context.getString(R.string.biometric_prompt_subtitle),
                            onSuccess = { authenticated = true },
                            onError = { err -> authError = err }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BiometricGate(
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.biometric_prompt_subtitle),
            style = MaterialTheme.typography.bodyLarge
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.authentication_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun GitCredentialsForm(viewModel: GitCredentialsViewModel) {
    val authorName by viewModel.authorName.collectAsState()
    val authorEmail by viewModel.authorEmail.collectAsState()
    val token by viewModel.token.collectAsState()

    var localName by remember(authorName) { mutableStateOf(authorName) }
    var localEmail by remember(authorEmail) { mutableStateOf(authorEmail) }
    var localToken by remember(token) { mutableStateOf(token) }
    var tokenVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = localName,
            onValueChange = { localName = it },
            label = { Text(stringResource(R.string.git_author_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = localEmail,
            onValueChange = { localEmail = it },
            label = { Text(stringResource(R.string.git_author_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = localToken,
            onValueChange = { localToken = it },
            label = { Text(stringResource(R.string.git_token)) },
            placeholder = { Text(stringResource(R.string.git_token_hint)) },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.save(localName, localEmail, localToken) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.git_save_credentials))
        }
    }
}
