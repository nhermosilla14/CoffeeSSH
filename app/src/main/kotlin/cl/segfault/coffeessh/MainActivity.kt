package cl.segfault.coffeessh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.segfault.coffeessh.ui.navigation.AppNavHost
import cl.segfault.coffeessh.ui.theme.CoffeeSshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoffeeSshTheme {
                AppNavHost()
            }
        }
    }
}
