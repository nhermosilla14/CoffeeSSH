package cl.segfault.coffeessh.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.ui.groups.GroupNameDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionEditorScreen(
    onBack: () -> Unit,
    viewModel: ConnectionEditorViewModel = viewModel(factory = ConnectionEditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var identityMenuOpen by remember { mutableStateOf(false) }
    var newGroupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.new_connection else R.string.edit_connection,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.canSave) {
                        Icon(Icons.Filled.Check, stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.nickname,
                onValueChange = viewModel::onNicknameChange,
                label = { Text(stringResource(R.string.nickname_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::onHostChange,
                label = { Text(stringResource(R.string.host_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = viewModel::onPortChange,
                label = { Text(stringResource(R.string.port_label)) },
                singleLine = true,
                isError = !state.portValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = identityMenuOpen,
                onExpandedChange = { identityMenuOpen = it },
            ) {
                val selectedLabel = state.identities.firstOrNull { it.id == state.identityId }
                    ?.nickname
                    ?: stringResource(R.string.identity_none)
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.identity_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = identityMenuOpen) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = identityMenuOpen,
                    onDismissRequest = { identityMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.identity_none)) },
                        onClick = {
                            viewModel.onIdentityChange(null)
                            identityMenuOpen = false
                        },
                    )
                    state.identities.forEach { identity ->
                        DropdownMenuItem(
                            text = { Text("${identity.nickname} (${identity.username})") },
                            onClick = {
                                viewModel.onIdentityChange(identity.id)
                                identityMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.groups_label),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.groups.forEach { group ->
                    FilterChip(
                        selected = group.id in state.selectedGroupIds,
                        onClick = { viewModel.toggleGroup(group.id) },
                        label = { Text(group.name) },
                    )
                }
                AssistChip(
                    onClick = { newGroupDialog = true },
                    label = { Text(stringResource(R.string.new_group)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        }
    }

    if (newGroupDialog) {
        GroupNameDialog(
            title = stringResource(R.string.new_group),
            confirmLabel = stringResource(R.string.create),
            onConfirm = viewModel::createGroupAndSelect,
            onDismiss = { newGroupDialog = false },
        )
    }
}
