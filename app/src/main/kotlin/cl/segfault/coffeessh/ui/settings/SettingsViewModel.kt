package cl.segfault.coffeessh.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.AppSettings
import cl.segfault.coffeessh.data.SettingsManager
import cl.segfault.coffeessh.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val settingsManager: SettingsManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsManager.settings.value)

    fun setTheme(mode: ThemeMode) = settingsManager.updateTheme(mode)

    fun setFont(font: String) = settingsManager.updateFont(font)

    fun setFontSize(size: Int) = settingsManager.updateFontSize(size)

    fun setScrollbackSize(size: Int) = settingsManager.updateScrollbackSize(size)

    fun setKeepaliveSeconds(seconds: Int) = settingsManager.updateKeepaliveSeconds(seconds)

    fun setTerminalColorScheme(scheme: String) = settingsManager.updateTerminalColorScheme(scheme)

    fun setAppColorScheme(scheme: String) = settingsManager.updateAppColorScheme(scheme)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CoffeeSshApp
                SettingsViewModel(app.container.settingsManager)
            }
        }
    }
}
