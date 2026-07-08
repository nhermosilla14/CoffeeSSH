package cl.segfault.coffeessh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.ui.common.PlaceholderScreen
import cl.segfault.coffeessh.ui.dashboard.DashboardScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val CONNECTIONS = "connections"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenConnections = { navController.navigate(Routes.CONNECTIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CONNECTIONS) {
            PlaceholderScreen(
                titleRes = R.string.dashboard_connections_title,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            PlaceholderScreen(
                titleRes = R.string.dashboard_settings_title,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
