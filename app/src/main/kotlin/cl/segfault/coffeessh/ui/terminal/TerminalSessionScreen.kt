package cl.segfault.coffeessh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.ssh.SshSessionState
import cl.segfault.coffeessh.terminal.KeyEncoder
import cl.segfault.coffeessh.terminal.TerminalColorScheme
import cl.segfault.coffeessh.terminal.TerminalKey
import cl.segfault.coffeessh.terminal.snapshotText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSessionScreen(
    connectionId: Long,
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(connectionId, sessionId)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sshState by viewModel.sshState.collectAsStateWithLifecycle()
    var ctrlSticky by rememberSaveable { mutableStateOf(false) }
    var keepScreenOn by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var terminalActionsOpen by remember { mutableStateOf(false) }
    var selectionInstructionVisible by remember { mutableStateOf(false) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var extraKeysVisible by rememberSaveable { mutableStateOf(true) }
    var fnActive by rememberSaveable { mutableStateOf(false) }
    var showCloseDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var sessionName by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val app = context.applicationContext as CoffeeSshApp
    val appSettings by app.container.settingsManager.settings.collectAsStateWithLifecycle()
    val terminalFont = TerminalFont.entries.firstOrNull {
        it.name.lowercase() == appSettings.terminalFont
    } ?: TerminalFont.JETBRAINS_MONO
    val terminalColorScheme = TerminalColorScheme.fromId(appSettings.terminalColorScheme)

    LaunchedEffect(sshState) {
        if (sshState is SshSessionState.Disconnected) {
            viewModel.closeSession()
            onBack()
        }
    }

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
                    Column(
                        modifier = Modifier.clickable { sessionName = ui.title; showRenameDialog = true },
                    ) {
                        Text(ui.title)
                        Text(
                            text = statusLabel(sshState),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showCloseDialog = true }) {
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
                                selectionInstructionVisible = true
                                terminalView?.beginSelection()
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
                            colorScheme = terminalColorScheme
                            setFont(terminalFont.fontRes)
                            textSizeSp = appSettings.terminalFontSize.toFloat()
                            onInput = ::sendInput
                            onResize = { rows, cols -> viewModel.resize(rows, cols) }
                            onReady = { showKeyboard() }
                            onTap = { extraKeysVisible = !extraKeysVisible }
                            onLongPress = { terminalActionsOpen = true }
                            onSelectionComplete = { text ->
                                clipboard.setText(AnnotatedString(text))
                                selectionInstructionVisible = false
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            }
                        }.also { terminalView = it }
                    },
                    update = { view ->
                        view.keepScreenOn = keepScreenOn
                        view.colorScheme = terminalColorScheme
                        view.setFont(terminalFont.fontRes)
                        view.textSizeSp = appSettings.terminalFontSize.toFloat()
                    },
                )
                StatusOverlay(ui = ui, sshState = sshState, onRetry = viewModel::retry)
            }
            if (selectionInstructionVisible) {
                Text(
                    text = stringResource(R.string.terminal_selection_instruction),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (extraKeysVisible) {
                ExtraKeysBar(
                    ctrlActive = ctrlSticky,
                    onCtrlToggle = { ctrlSticky = !ctrlSticky },
                    onKey = { key -> viewModel.sendInput(KeyEncoder.encode(key, viewModel.terminal)) },
                    onEsc = { viewModel.sendInput(byteArrayOf(0x1B)) },
                    onTab = { viewModel.sendInput(byteArrayOf(0x09)) },
                    fnActive = fnActive,
                    onFnToggle = { fnActive = !fnActive },
                )
            }
        }
    }

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text(stringResource(R.string.close_session_title)) },
            text = { Text(stringResource(R.string.close_session_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showCloseDialog = false
                    viewModel.closeSession()
                    onBack()
                }) { Text(stringResource(R.string.close_session_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCloseDialog = false
                    onBack()
                }) { Text(stringResource(R.string.close_session_keep)) }
            },
        )
    }

    if (terminalActionsOpen) {
        AlertDialog(
            onDismissRequest = { terminalActionsOpen = false },
            title = { Text(stringResource(R.string.terminal_action_title)) },
            text = {
                Column {
                    TerminalAction(stringResource(R.string.terminal_action_copy_screen)) {
                        terminalActionsOpen = false
                        selectionInstructionVisible = true
                        terminalView?.beginSelection()
                    }
                    TerminalAction(stringResource(R.string.terminal_action_copy_session)) {
                        terminalActionsOpen = false
                        clipboard.setText(AnnotatedString(viewModel.terminal.scrollbackSnapshot().joinToString("\n") { row ->
                            row.filter { it.width != 0 }.joinToString("") { cell ->
                                if (cell.codePoint == 0) " " else String(Character.toChars(cell.codePoint))
                            }.trimEnd()
                        }))
                    }
                    TerminalAction(stringResource(R.string.terminal_action_paste)) {
                        terminalActionsOpen = false
                        pasteFromClipboard(viewModel, clipboard)
                    }
                    TerminalAction(stringResource(R.string.terminal_action_save_transcript)) { terminalActionsOpen = false }
                    TerminalAction(stringResource(R.string.terminal_action_share_transcript)) { terminalActionsOpen = false }
                    TerminalAction(stringResource(R.string.terminal_action_keep_screen_on)) {
                        keepScreenOn = !keepScreenOn
                        terminalActionsOpen = false
                    }
                    TerminalAction(stringResource(R.string.terminal_action_clear)) {
                        terminalActionsOpen = false
                        viewModel.sendInput("\u001b[2J\u001b[H".encodeToByteArray())
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_session_title)) },
            text = {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text(stringResource(R.string.rename_session_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(sessionName)
                    showRenameDialog = false
                }) { Text(stringResource(R.string.rename_session_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
    fnActive: Boolean,
    onFnToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(0.dp),
    ) {
        if (fnActive) {
            ExtraKeyRow {
                listOf(TerminalKey.F1, TerminalKey.F2, TerminalKey.F3, TerminalKey.F4, TerminalKey.F5, TerminalKey.F6).forEachIndexed { index, key ->
                    ExtraKey("F${index + 1}") { onKey(key) }
                }
                ExtraKey("↶") { onFnToggle() }
            }
            ExtraKeyRow {
                listOf(TerminalKey.F7, TerminalKey.F8, TerminalKey.F9, TerminalKey.F10, TerminalKey.F11, TerminalKey.F12).forEachIndexed { index, key ->
                    ExtraKey("F${index + 7}") { onKey(key) }
                }
                ExtraKey("⌨") {}
            }
        } else {
            ExtraKeyRow {
                ExtraKey("ESC", onClick = onEsc); ExtraKey("/") {}; ExtraKey("|") {}; ExtraKey("-") {}
                ExtraKey("HOME") { onKey(TerminalKey.HOME) }; ExtraKey("↑") { onKey(TerminalKey.ARROW_UP) }
                ExtraKey("END") { onKey(TerminalKey.END) }; ExtraKey("PGUP") { onKey(TerminalKey.PAGE_UP) }; ExtraKey("FN", onClick = onFnToggle)
            }
            ExtraKeyRow {
                ExtraKey("TAB", onClick = onTab); ExtraKey("CTRL", highlighted = ctrlActive, onClick = onCtrlToggle); ExtraKey("ALT") {}
                ExtraKey("←") { onKey(TerminalKey.ARROW_LEFT) }; ExtraKey("↓") { onKey(TerminalKey.ARROW_DOWN) }
                ExtraKey("→") { onKey(TerminalKey.ARROW_RIGHT) }; ExtraKey("PGDN") { onKey(TerminalKey.PAGE_DOWN) }; ExtraKey("⌨") {}
            }
        }
    }
}

@Composable
private fun ExtraKeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        content = content,
    )
}

@Composable
private fun RowScope.ExtraKey(label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = label,
        )
    }
}

@Composable
private fun TerminalAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
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
