package cl.segfault.coffeessh.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.segfault.coffeessh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(stringResource(R.string.about_text, "0.3.0"), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onOpenLicenses) {
                Text(stringResource(R.string.open_source_licenses))
            }
        }
    }
}

private data class LicenseNotice(val name: String, val license: String, val notice: String)

private val licenseNotices = listOf(
    LicenseNotice("AndroidX Core KTX", "Apache License 2.0", "Copyright The Android Open Source Project."),
    LicenseNotice("Jetpack Compose and Material 3", "Apache License 2.0", "Copyright The Android Open Source Project."),
    LicenseNotice("AndroidX Activity, Lifecycle, Navigation, Room, and Test", "Apache License 2.0", "Copyright The Android Open Source Project."),
    LicenseNotice("Kotlin", "Apache License 2.0", "Copyright JetBrains and Kotlin contributors."),
    LicenseNotice("sshj", "Apache License 2.0", "Copyright Hierynomus and sshj contributors."),
    LicenseNotice("Bouncy Castle", "MIT-style Bouncy Castle License", "Copyright The Legion of the Bouncy Castle."),
    LicenseNotice("SLF4J", "MIT License", "Copyright QOS.ch and SLF4J contributors."),
    LicenseNotice("JUnit", "Eclipse Public License 1.0", "Used by the test source set."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(licenseNotices) { item ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.license, style = MaterialTheme.typography.labelLarge)
                    Text(item.notice, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
    }
}
