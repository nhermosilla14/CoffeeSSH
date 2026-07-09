package cl.segfault.coffeessh.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.segfault.coffeessh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val frequent by viewModel.frequent.collectAsStateWithLifecycle()
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DashboardCard(
                    titleRes = R.string.dashboard_connections_title,
                    subtitleRes = R.string.dashboard_connections_subtitle,
                    icon = Icons.Filled.Public,
                    onClick = onOpenConnections,
                )
            }
            item {
                DashboardCard(
                    titleRes = R.string.dashboard_frequent_title,
                    subtitleRes = R.string.dashboard_frequent_subtitle,
                    icon = Icons.Filled.Star,
                    onClick = onOpenConnections,
                ) {
                    if (frequent.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dashboard_frequent_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            frequent.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                        Text(
                                            text = item.connection.nickname ?: item.connection.host,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = "${item.connection.host}:${item.connection.port}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                DashboardCard(
                    titleRes = R.string.dashboard_settings_title,
                    subtitleRes = R.string.dashboard_settings_subtitle,
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                )
            }
            item {
                DashboardCard(
                    titleRes = R.string.dashboard_help_title,
                    subtitleRes = R.string.dashboard_help_subtitle,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    onClick = { showAbout = true },
                )
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = { Text(stringResource(R.string.about_text)) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun DashboardCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            extraContent?.invoke()
        }
    }
}
