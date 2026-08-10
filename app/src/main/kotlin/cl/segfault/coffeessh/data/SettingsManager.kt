package cl.segfault.coffeessh.data

import android.content.Context
import cl.segfault.coffeessh.terminal.TerminalColorScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val terminalFont: String = "jetbrains_mono",
    val terminalFontSize: Int = 14,
    val scrollbackSize: Int = 1000,
    /** Seconds between SSH keepalive packets; 0 disables keepalives. */
    val keepaliveSeconds: Int = 15,
    val terminalColorScheme: String = TerminalColorScheme.COFFEE.id,
    val appColorScheme: String = AppColorScheme.COFFEE.id,
)

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String): ThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        return AppSettings(
            themeMode = ThemeMode.from(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value),
            terminalFont = prefs.getString(KEY_FONT, "jetbrains_mono") ?: "jetbrains_mono",
            terminalFontSize = prefs.getInt(KEY_FONT_SIZE, 14),
            scrollbackSize = prefs.getInt(KEY_SCROLLBACK, 1000),
            keepaliveSeconds = prefs.getInt(KEY_KEEPALIVE, 15),
            terminalColorScheme = prefs.getString(KEY_COLOR_SCHEME, TerminalColorScheme.COFFEE.id)
                ?: TerminalColorScheme.COFFEE.id,
            appColorScheme = prefs.getString(KEY_APP_COLOR_SCHEME, AppColorScheme.COFFEE.id)
                ?: AppColorScheme.COFFEE.id,
        )
    }

    fun updateTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.value).apply()
        _settings.value = load()
    }

    fun updateFont(font: String) {
        prefs.edit().putString(KEY_FONT, font).apply()
        _settings.value = load()
    }

    fun updateFontSize(size: Int) {
        prefs.edit().putInt(KEY_FONT_SIZE, size.coerceIn(8, 32)).apply()
        _settings.value = load()
    }

    fun updateScrollbackSize(size: Int) {
        prefs.edit().putInt(KEY_SCROLLBACK, size.coerceIn(100, 10000)).apply()
        _settings.value = load()
    }

    fun updateKeepaliveSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_KEEPALIVE, seconds.coerceIn(0, 120)).apply()
        _settings.value = load()
    }

    fun updateTerminalColorScheme(scheme: String) {
        val valid = TerminalColorScheme.fromId(scheme).id
        prefs.edit().putString(KEY_COLOR_SCHEME, valid).apply()
        _settings.value = load()
    }

    fun updateAppColorScheme(scheme: String) {
        val valid = AppColorScheme.fromId(scheme).id
        prefs.edit().putString(KEY_APP_COLOR_SCHEME, valid).apply()
        _settings.value = load()
    }

    companion object {
        private const val PREFS_NAME = "coffeessh_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "terminal_font"
        private const val KEY_FONT_SIZE = "terminal_font_size"
        private const val KEY_SCROLLBACK = "scrollback_size"
        private const val KEY_KEEPALIVE = "keepalive_seconds"
        private const val KEY_COLOR_SCHEME = "terminal_color_scheme"
        private const val KEY_APP_COLOR_SCHEME = "app_color_scheme"
    }
}
