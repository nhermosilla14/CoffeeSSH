package cl.segfault.coffeessh.ui.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.data.db.GroupEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    viewModel: GroupsViewModel = viewModel(factory = GroupsViewModel.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var createDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GroupEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createDialog = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.add))
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.groups_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(groups, key = { it.id }) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renameTarget = group }) {
                                    Icon(Icons.Filled.Edit, stringResource(R.string.rename))
                                }
                                IconButton(onClick = { deleteTarget = group }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (createDialog) {
        GroupNameDialog(
            title = stringResource(R.string.new_group),
            confirmLabel = stringResource(R.string.create),
            onConfirm = viewModel::create,
            onDismiss = { createDialog = false },
        )
    }

    renameTarget?.let { group ->
        GroupNameDialog(
            title = stringResource(R.string.rename_group),
            confirmLabel = stringResource(R.string.rename),
            initialValue = group.name,
            onConfirm = { viewModel.rename(group, it) },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_text, group.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(group.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
