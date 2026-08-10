package cl.segfault.coffeessh.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.data.ThemeMode
import cl.segfault.coffeessh.data.AppColorScheme
import cl.segfault.coffeessh.ui.terminal.TerminalFont
import cl.segfault.coffeessh.terminal.TerminalColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_settings_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemeSection(settings.themeMode) { viewModel.setTheme(it) }
            HorizontalDivider()
            FontSection(settings.terminalFont) { viewModel.setFont(it) }
            HorizontalDivider()
            FontSizeSection(settings.terminalFontSize) { viewModel.setFontSize(it) }
            HorizontalDivider()
            ScrollbackSection(settings.scrollbackSize) { viewModel.setScrollbackSize(it) }
            HorizontalDivider()
            KeepaliveSection(settings.keepaliveSeconds) { viewModel.setKeepaliveSeconds(it) }
            HorizontalDivider()
            ColorSchemeSection(settings.terminalColorScheme) { viewModel.setTerminalColorScheme(it) }
            HorizontalDivider()
            AppColorSchemeSection(settings.appColorScheme) { viewModel.setAppColorScheme(it) }
        }
    }
}

@Composable
private fun ThemeSection(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_theme_title))
    ThemeMode.entries.forEach { mode ->
        val label = when (mode) {
            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(selected = current == mode, onClick = { onChange(mode) })
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FontSection(current: String, onChange: (String) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_font_title))
    TerminalFont.entries.forEach { font ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(
                selected = current == font.name.lowercase(),
                onClick = { onChange(font.name.lowercase()) },
            )
            Text(font.displayName, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FontSizeSection(current: Int, onChange: (Int) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_font_size_title))
    Text(
        text = "${current}sp",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = current.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 8f..32f,
        steps = 23,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ScrollbackSection(current: Int, onChange: (Int) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_scrollback_title))
    Text(
        text = "$current lines",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = current.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 100f..10000f,
        steps = 98,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KeepaliveSection(current: Int, onChange: (Int) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_keepalive_title))
    Text(
        text = if (current == 0) {
            stringResource(R.string.settings_keepalive_off)
        } else {
            stringResource(R.string.settings_keepalive_value, current)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = current.toFloat(),
        onValueChange = { onChange((it / 5).toInt() * 5) },
        valueRange = 0f..120f,
        steps = 23,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColorSchemeSection(current: String, onChange: (String) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_color_scheme_title))
    TerminalColorScheme.entries.forEach { scheme ->
        val selected = scheme.id == current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(selected = selected, onClick = { onChange(scheme.id) })
            Surface(
                color = androidx.compose.ui.graphics.Color(scheme.defaultBackground),
                contentColor = androidx.compose.ui.graphics.Color(scheme.defaultForeground),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(width = 56.dp, height = 32.dp),
            ) {
                Text(
                    text = "Aa",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = stringResource(colorSchemeLabel(scheme)),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private fun colorSchemeLabel(scheme: TerminalColorScheme): Int = when (scheme) {
    TerminalColorScheme.COFFEE -> R.string.settings_color_scheme_coffee
    TerminalColorScheme.SOLARIZED -> R.string.settings_color_scheme_solarized
    TerminalColorScheme.DRACULA -> R.string.settings_color_scheme_dracula
    TerminalColorScheme.NORD -> R.string.settings_color_scheme_nord
    TerminalColorScheme.MONOKAI -> R.string.settings_color_scheme_monokai
}

@Composable
private fun AppColorSchemeSection(current: String, onChange: (String) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_app_color_scheme_title))
    AppColorScheme.entries.forEach { scheme ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(selected = current == scheme.id, onClick = { onChange(scheme.id) })
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(width = 56.dp, height = 32.dp),
            ) {
                Text(
                    text = "Aa",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = stringResource(appColorSchemeLabel(scheme)),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private fun appColorSchemeLabel(scheme: AppColorScheme): Int = when (scheme) {
    AppColorScheme.COFFEE -> R.string.settings_app_color_scheme_coffee
    AppColorScheme.OCEAN -> R.string.settings_app_color_scheme_ocean
    AppColorScheme.FOREST -> R.string.settings_app_color_scheme_forest
    AppColorScheme.AMBER -> R.string.settings_app_color_scheme_amber
    AppColorScheme.VIOLET -> R.string.settings_app_color_scheme_violet
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
