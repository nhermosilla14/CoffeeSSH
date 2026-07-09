package cl.segfault.coffeessh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.ssh.SshSessionState
import cl.segfault.coffeessh.terminal.KeyEncoder
import cl.segfault.coffeessh.terminal.TerminalKey
import cl.segfault.coffeessh.terminal.snapshotText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSessionScreen(
    connectionId: Long,
    onBack: () -> Unit,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(connectionId)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sshState by viewModel.sshState.collectAsStateWithLifecycle()
    var ctrlSticky by rememberSaveable { mutableStateOf(false) }
    var keepScreenOn by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun sendInput(bytes: ByteArray) {
        val isLetter = bytes.size == 1 && bytes[0].toInt().toChar().let { it in 'a'..'z' || it in 'A'..'Z' }
        if (ctrlSticky && isLetter) {
            viewModel.sendInput(KeyEncoder.encodeCtrl(bytes[0].toInt().toChar()))
            ctrlSticky = false
        } else {
            viewModel.sendInput(bytes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.title)
                        Text(
                            text = statusLabel(sshState),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_action_copy_screen)) },
                            onClick = {
                                menuOpen = false
                                copyScreenText(viewModel, clipboard, context)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_action_paste)) },
                            onClick = {
                                menuOpen = false
                                pasteFromClipboard(viewModel, clipboard)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_action_keep_screen_on)) },
                            leadingIcon = {
                                Icon(
                                    if (keepScreenOn) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                keepScreenOn = !keepScreenOn
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_action_clear)) },
                            onClick = {
                                menuOpen = false
                                viewModel.sendInput("\u001b[2J\u001b[H".encodeToByteArray())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_action_disconnect)) },
                            onClick = {
                                menuOpen = false
                                viewModel.disconnect()
                            },
                        )
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
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TerminalView(ctx).apply {
                            this.terminal = viewModel.terminal
                            setFont(TerminalFont.JETBRAINS_MONO.fontRes)
                            onInput = ::sendInput
                            onResize = { rows, cols -> viewModel.resize(rows, cols) }
                        }
                    },
                    update = { view -> view.keepScreenOn = keepScreenOn },
                )
                StatusOverlay(ui = ui, sshState = sshState, onRetry = viewModel::retry)
            }
            ExtraKeysBar(
                ctrlActive = ctrlSticky,
                onCtrlToggle = { ctrlSticky = !ctrlSticky },
                onKey = { key -> viewModel.sendInput(KeyEncoder.encode(key, viewModel.terminal)) },
                onEsc = { viewModel.sendInput(byteArrayOf(0x1B)) },
                onTab = { viewModel.sendInput(byteArrayOf(0x09)) },
            )
        }
    }

    val awaiting = sshState as? SshSessionState.AwaitingHostKeyConfirmation
    if (awaiting != null) {
        HostKeyDialog(
            awaiting = awaiting,
            onAccept = { viewModel.confirmHostKey(true) },
            onReject = { viewModel.confirmHostKey(false) },
        )
    }
}

@Composable
private fun StatusOverlay(ui: SessionUiState, sshState: SshSessionState, onRetry: () -> Unit) {
    when {
        ui.missingIdentity -> CenteredMessage(stringResource(R.string.session_no_identity))
        sshState is SshSessionState.Connecting || sshState is SshSessionState.Authenticating -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = statusLabel(sshState),
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        sshState is SshSessionState.Failed -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.session_failed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = sshState.message,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRetry) { Text(stringResource(R.string.session_retry)) }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(32.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun statusLabel(sshState: SshSessionState): String = when (sshState) {
    is SshSessionState.Idle, is SshSessionState.Connecting -> stringResource(R.string.session_connecting)
    is SshSessionState.Authenticating -> stringResource(R.string.session_authenticating)
    is SshSessionState.AwaitingHostKeyConfirmation -> stringResource(R.string.session_authenticating)
    is SshSessionState.Connected -> stringResource(R.string.session_connected)
    is SshSessionState.Disconnected -> stringResource(R.string.session_disconnected)
    is SshSessionState.Failed -> stringResource(R.string.session_failed)
}

@Composable
private fun HostKeyDialog(
    awaiting: SshSessionState.AwaitingHostKeyConfirmation,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    if (awaiting.isChanged) R.string.host_key_changed_title else R.string.host_key_new_title,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (awaiting.isChanged) R.string.host_key_changed_text else R.string.host_key_new_text,
                    awaiting.host,
                    awaiting.port,
                    awaiting.keyType,
                    awaiting.fingerprint,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(
                    text = stringResource(R.string.host_key_accept),
                    color = if (awaiting.isChanged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.host_key_reject)) }
        },
    )
}

@Composable
private fun ExtraKeysBar(
    ctrlActive: Boolean,
    onCtrlToggle: () -> Unit,
    onKey: (TerminalKey) -> Unit,
    onEsc: () -> Unit,
    onTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExtraKey("ESC", onClick = onEsc)
        ExtraKey("TAB", onClick = onTab)
        ExtraKey("CTRL", highlighted = ctrlActive, onClick = onCtrlToggle)
        ExtraKey("\u2190", onClick = { onKey(TerminalKey.ARROW_LEFT) })
        ExtraKey("\u2191", onClick = { onKey(TerminalKey.ARROW_UP) })
        ExtraKey("\u2193", onClick = { onKey(TerminalKey.ARROW_DOWN) })
        ExtraKey("\u2192", onClick = { onKey(TerminalKey.ARROW_RIGHT) })
        ExtraKey("HOME", onClick = { onKey(TerminalKey.HOME) })
        ExtraKey("END", onClick = { onKey(TerminalKey.END) })
        ExtraKey("PGUP", onClick = { onKey(TerminalKey.PAGE_UP) })
        ExtraKey("PGDN", onClick = { onKey(TerminalKey.PAGE_DOWN) })
    }
}

@Composable
private fun ExtraKey(label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun copyScreenText(viewModel: SessionViewModel, clipboard: ClipboardManager, context: android.content.Context) {
    clipboard.setText(AnnotatedString(viewModel.terminal.snapshotText()))
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
}

private fun pasteFromClipboard(viewModel: SessionViewModel, clipboard: ClipboardManager) {
    val text = clipboard.getText()?.text ?: return
    val bytes = if (viewModel.terminal.modes.bracketedPaste) {
        ("\u001b[200~$text\u001b[201~").encodeToByteArray()
    } else {
        text.encodeToByteArray()
    }
    viewModel.sendInput(bytes)
}
