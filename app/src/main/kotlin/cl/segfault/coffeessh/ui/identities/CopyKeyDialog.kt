package cl.segfault.coffeessh.ui.identities

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.data.db.ConnectionWithRefs
import cl.segfault.coffeessh.ssh.CopyKeyResult
import cl.segfault.coffeessh.ssh.SshCopyKeyExecutor
import cl.segfault.coffeessh.ssh.TofuHostKeyVerifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

@Composable
fun CopyKeyDialog(
    connections: List<ConnectionWithRefs>,
    publicKey: String,
    username: String,
    password: String,
    privateKey: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CoffeeSshApp
    var selectedConnection by remember { mutableStateOf<ConnectionWithRefs?>(null) }
    var step by remember { mutableStateOf<CopyDialogStep>(CopyDialogStep.PickConnection) }
    var pendingHostKey by remember { mutableStateOf<TofuHostKeyVerifier.Result.NeedsDecision?>(null) }
    var hostKeyDecision by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var copyResult by remember { mutableStateOf<CopyKeyResult?>(null) }
    val scope = rememberCoroutineScope()

    fun answerHostKey(accept: Boolean) {
        hostKeyDecision?.complete(accept)
        hostKeyDecision = null
        if (accept) {
            step = CopyDialogStep.Running
        } else {
            copyResult = CopyKeyResult.Failed(context.getString(R.string.copy_key_host_key_rejected))
            step = CopyDialogStep.Done
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        pendingHostKey != null && pendingHostKey!!.isChanged -> R.string.host_key_changed_title
                        pendingHostKey != null -> R.string.host_key_new_title
                        else -> R.string.copy_key_to_server_title
                    },
                ),
            )
        },
        text = {
            val pending = pendingHostKey
            when {
                pending != null -> {
                    Text(
                        stringResource(
                            if (pending.isChanged) R.string.host_key_changed_text else R.string.host_key_new_text,
                            pending.host,
                            pending.port,
                            pending.keyType,
                            pending.fingerprint,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                else -> when (step) {
                    is CopyDialogStep.PickConnection -> {
                        if (connections.isEmpty()) {
                            Text(stringResource(R.string.copy_key_no_connections))
                        } else {
                            Column {
                                Text(
                                    stringResource(R.string.copy_key_select_connection),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                LazyColumn {
                                    items(connections) { conn ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .selectable(
                                                    selected = selectedConnection == conn,
                                                    onClick = { selectedConnection = conn },
                                                    role = Role.RadioButton,
                                                )
                                                .padding(vertical = 4.dp),
                                        ) {
                                            RadioButton(
                                                selected = selectedConnection == conn,
                                                onClick = null,
                                            )
                                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                                Text(
                                                    text = conn.connection.nickname ?: conn.connection.host,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                                Text(
                                                    text = "${conn.connection.host}:${conn.connection.port}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is CopyDialogStep.Running -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.copy_key_working))
                        }
                    }
                    is CopyDialogStep.Done -> {
                        when (val result = copyResult) {
                            is CopyKeyResult.Success, is CopyKeyResult.AlreadyPresent -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    Text(
                                        stringResource(
                                            if (result is CopyKeyResult.AlreadyPresent) {
                                                R.string.copy_key_already_present
                                            } else {
                                                R.string.copy_key_success
                                            },
                                        ),
                                    )
                                }
                            }
                            is CopyKeyResult.Failed -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        stringResource(R.string.copy_key_failed),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        result.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            null -> {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                pendingHostKey != null -> {
                    Button(onClick = { answerHostKey(true) }) {
                        Text(stringResource(R.string.host_key_accept))
                    }
                }
                else -> when (step) {
                    is CopyDialogStep.PickConnection -> {
                        Button(
                            onClick = {
                                val conn = selectedConnection ?: return@Button
                                val targetUsername = conn.identity?.username ?: username
                                step = CopyDialogStep.Running
                                scope.launch {
                                    val result = SshCopyKeyExecutor.execute(
                                        host = conn.connection.host,
                                        port = conn.connection.port,
                                        username = targetUsername,
                                        password = password.ifBlank { null },
                                        privateKeyPem = privateKey.ifBlank { null },
                                        publicKey = publicKey,
                                        hostKeyVerifier = TofuHostKeyVerifier(app.container.knownHostDao),
                                        onProgress = { },
                                        confirmHostKey = { decision ->
                                            val deferred = CompletableDeferred<Boolean>()
                                            hostKeyDecision = deferred
                                            pendingHostKey = decision
                                            val accepted = deferred.await()
                                            pendingHostKey = null
                                            accepted
                                        },
                                    )
                                    copyResult = result
                                    step = CopyDialogStep.Done
                                    if (result is CopyKeyResult.Success) {
                                        Toast.makeText(context, R.string.copy_key_success, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = selectedConnection != null,
                        ) {
                            Text(stringResource(R.string.copy_key_copy_button))
                        }
                    }
                    is CopyDialogStep.Running -> {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                    is CopyDialogStep.Done -> {
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        },
        dismissButton = when {
            pendingHostKey != null -> {
                {
                    TextButton(onClick = { answerHostKey(false) }) {
                        Text(stringResource(R.string.host_key_reject))
                    }
                }
            }
            step is CopyDialogStep.PickConnection -> {
                { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
            }
            else -> null
        },
    )
}

private sealed interface CopyDialogStep {
    data object PickConnection : CopyDialogStep
    data object Running : CopyDialogStep
    data object Done : CopyDialogStep
}
