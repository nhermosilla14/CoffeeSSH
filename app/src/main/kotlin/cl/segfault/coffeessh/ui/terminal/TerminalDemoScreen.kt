package cl.segfault.coffeessh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.terminal.KeyEncoder
import cl.segfault.coffeessh.terminal.Terminal
import cl.segfault.coffeessh.terminal.TerminalKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalDemoScreen(onBack: () -> Unit) {
    val terminal = remember { Terminal(rows = 24, cols = 80) }
    val shell = remember(terminal) { DemoShell(terminal) }
    var ctrlSticky by rememberSaveable { mutableStateOf(false) }

    fun sendInput(bytes: ByteArray) {
        val asCtrlable = bytes.size == 1 && bytes[0].toInt().toChar().let { it in 'a'..'z' || it in 'A'..'Z' }
        if (ctrlSticky && asCtrlable) {
            shell.onInput(KeyEncoder.encodeCtrl(bytes[0].toInt().toChar()))
            ctrlSticky = false
        } else {
            shell.onInput(bytes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.terminal_demo_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    TerminalView(ctx).apply {
                        this.terminal = terminal
                        setFont(TerminalFont.JETBRAINS_MONO.fontRes)
                        onInput = ::sendInput
                        onReady = { shell.start() }
                    }
                },
            )
            ExtraKeysBar(
                ctrlActive = ctrlSticky,
                onCtrlToggle = { ctrlSticky = !ctrlSticky },
                onKey = { key -> shell.onInput(KeyEncoder.encode(key, terminal)) },
                onEsc = { shell.onInput(byteArrayOf(0x1B)) },
                onTab = { shell.onInput(byteArrayOf(0x09)) },
            )
        }
    }
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
