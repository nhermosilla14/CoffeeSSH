package cl.segfault.coffeessh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.segfault.coffeessh.data.ThemeMode
import cl.segfault.coffeessh.data.AppColorScheme
import cl.segfault.coffeessh.ui.navigation.AppNavHost
import cl.segfault.coffeessh.ui.theme.CoffeeSshTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op either way: the foreground service still works, just silently, without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val settingsManager = (application as CoffeeSshApp).container.settingsManager
        setContent {
            val settings by settingsManager.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // The theme reads the same StateFlow-backed preference as Settings, so
            // changing the app palette updates the whole Compose tree immediately.
            CoffeeSshTheme(
                darkTheme = darkTheme,
                appColorScheme = AppColorScheme.fromId(settings.appColorScheme),
            ) {
                AppNavHost()
            }
        }
    }

    /**
     * The foreground service that keeps SSH sessions alive shows a persistent notification
     * (session count + disconnect action) - without this permission (API 33+) it would run
     * silently invisible to the user, defeating half its purpose.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
