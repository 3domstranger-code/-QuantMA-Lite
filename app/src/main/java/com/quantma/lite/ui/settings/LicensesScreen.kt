package com.quantma.lite.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantma.lite.R

private data class LibraryLicense(
    val name: String,
    val version: String,
    val license: String,
    val url: String
)

private val libraries = listOf(
    LibraryLicense(
        name = "llama.cpp",
        version = "submodule",
        license = "MIT License",
        url = "https://github.com/ggerganov/llama.cpp"
    ),
    LibraryLicense(
        name = "Eclipse JGit",
        version = "7.2.0",
        license = "Eclipse Distribution License 1.0 (BSD)",
        url = "https://www.eclipse.org/jgit/"
    ),
    LibraryLicense(
        name = "Sora Editor",
        version = "0.23.6",
        license = "LGPL-2.1",
        url = "https://github.com/Rosemoe/sora-editor"
    ),
    LibraryLicense(
        name = "java-diff-utils",
        version = "4.12",
        license = "Apache License 2.0",
        url = "https://github.com/java-diff-utils/java-diff-utils"
    ),
    LibraryLicense(
        name = "Kotlin",
        version = "2.2.10",
        license = "Apache License 2.0",
        url = "https://kotlinlang.org"
    ),
    LibraryLicense(
        name = "Kotlin Coroutines",
        version = "1.9.0",
        license = "Apache License 2.0",
        url = "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    LibraryLicense(
        name = "Jetpack Compose",
        version = "BOM 2024.12.01",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/compose"
    ),
    LibraryLicense(
        name = "Dagger Hilt",
        version = "2.56",
        license = "Apache License 2.0",
        url = "https://dagger.dev/hilt/"
    ),
    LibraryLicense(
        name = "AndroidX Room",
        version = "2.7.0",
        license = "Apache License 2.0",
        url = "https://developer.android.com/training/data-storage/room"
    ),
    LibraryLicense(
        name = "AndroidX Security Crypto",
        version = "1.1.0-alpha06",
        license = "Apache License 2.0",
        url = "https://developer.android.com/topic/security/data"
    ),
    LibraryLicense(
        name = "AndroidX Biometric",
        version = "1.1.0",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/biometric"
    ),
    LibraryLicense(
        name = "AndroidX Navigation",
        version = "2.8.5",
        license = "Apache License 2.0",
        url = "https://developer.android.com/guide/navigation"
    ),
    LibraryLicense(
        name = "AndroidX DataStore",
        version = "1.1.1",
        license = "Apache License 2.0",
        url = "https://developer.android.com/topic/libraries/architecture/datastore"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.licenses_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            libraries.forEach { lib ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${lib.name} ${lib.version}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lib.license,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lib.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
