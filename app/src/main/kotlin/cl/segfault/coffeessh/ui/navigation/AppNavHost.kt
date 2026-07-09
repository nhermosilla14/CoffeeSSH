package cl.segfault.coffeessh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.segfault.coffeessh.R
import cl.segfault.coffeessh.ui.common.PlaceholderScreen
import cl.segfault.coffeessh.ui.connections.ConnectionEditorScreen
import cl.segfault.coffeessh.ui.connections.ConnectionsScreen
import cl.segfault.coffeessh.ui.dashboard.DashboardScreen
import cl.segfault.coffeessh.ui.groups.GroupsScreen
import cl.segfault.coffeessh.ui.identities.IdentityEditorScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val CONNECTIONS = "connections"
    const val SETTINGS = "settings"
    const val GROUPS = "groups"
    const val CONNECTION_NEW = "connection/new"
    const val CONNECTION_EDIT = "connection/{connectionId}"
    const val IDENTITY_NEW = "identity/new"
    const val IDENTITY_EDIT = "identity/{identityId}"

    fun connectionEdit(id: Long) = "connection/$id"
    fun identityEdit(id: Long) = "identity/$id"
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
            ConnectionsScreen(
                onBack = { navController.popBackStack() },
                onNewConnection = { navController.navigate(Routes.CONNECTION_NEW) },
                onEditConnection = { navController.navigate(Routes.connectionEdit(it)) },
                onNewIdentity = { navController.navigate(Routes.IDENTITY_NEW) },
                onEditIdentity = { navController.navigate(Routes.identityEdit(it)) },
                onManageGroups = { navController.navigate(Routes.GROUPS) },
            )
        }
        composable(Routes.CONNECTION_NEW) {
            ConnectionEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CONNECTION_EDIT,
            arguments = listOf(navArgument("connectionId") { type = NavType.LongType }),
        ) {
            ConnectionEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IDENTITY_NEW) {
            IdentityEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.IDENTITY_EDIT,
            arguments = listOf(navArgument("identityId") { type = NavType.LongType }),
        ) {
            IdentityEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GROUPS) {
            GroupsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            PlaceholderScreen(
                titleRes = R.string.dashboard_settings_title,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
