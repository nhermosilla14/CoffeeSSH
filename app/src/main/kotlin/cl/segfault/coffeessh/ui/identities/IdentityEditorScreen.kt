package cl.segfault.coffeessh.ui.identities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.ssh.KeyTypeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityEditorScreen(
    onBack: () -> Unit,
    viewModel: IdentityEditorViewModel = viewModel(factory = IdentityEditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.new_identity else R.string.edit_identity,
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
                label = { Text(stringResource(R.string.identity_nickname_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.username_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(
                                if (showPassword) R.string.hide_password else R.string.show_password,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.key_pair_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = state.privateKey,
                onValueChange = {
                    viewModel.onPrivateKeyChange(it)
                    if (it != state.privateKey) viewModel.clearPublicKey()
                },
                label = { Text(stringResource(R.string.private_key_label)) },
                supportingText = if (state.publicKey == null) {
                    { Text(stringResource(R.string.private_key_hint)) }
                } else null,
                minLines = 6,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )

            FilledTonalButton(
                onClick = { viewModel.showKeyGenDialog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.generate_key_button))
            }

            if (state.publicKey != null) {
                PublicKeyCard(
                    publicKey = state.publicKey!!,
                    keyType = state.keyType,
                    username = state.username,
                    password = state.password,
                    privateKey = state.privateKey,
                    context = context,
                )
            }
        }
    }

    if (state.showKeyGenDialog) {
        KeyGenDialog(
            onDismiss = { viewModel.hideKeyGenDialog() },
            onGenerate = { type -> viewModel.generateKey(type) },
        )
    }
}

@Composable
private fun PublicKeyCard(
    publicKey: String,
    keyType: String?,
    username: String,
    password: String,
    privateKey: String,
    context: Context,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.public_key_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (keyType != null) {
                    Text(
                        text = keyType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = publicKey,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 5,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("SSH public key", publicKey))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(
                        stringResource(R.string.copy_to_clipboard),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, publicKey)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(
                        stringResource(R.string.share),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            CopyToServerButton(
                publicKey = publicKey,
                username = username,
                password = password,
                privateKey = privateKey,
                context = context,
            )
        }
    }
}

@Composable
private fun CopyToServerButton(
    publicKey: String,
    username: String,
    password: String,
    privateKey: String,
    context: Context,
) {
    val app = context.applicationContext as cl.segfault.coffeessh.CoffeeSshApp
    val connections by app.container.connectionsRepository.observeAll()
        .collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null)
        Text(
            stringResource(R.string.copy_key_to_server_button),
            modifier = Modifier.padding(start = 4.dp),
        )
    }

    if (showDialog) {
        CopyKeyDialog(
            connections = connections,
            publicKey = publicKey,
            username = username,
            password = password,
            privateKey = privateKey,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun KeyGenDialog(
    onDismiss: () -> Unit,
    onGenerate: (KeyTypeOption) -> Unit,
) {
    var selectedType by remember { mutableStateOf(KeyTypeOption.ED25519) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_key_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyTypeOption.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                        )
                        Text(
                            text = type.label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(selectedType) }) {
                Text(stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
