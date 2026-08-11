package cl.segfault.coffeessh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
import cl.segfault.coffeessh.ui.about.AboutScreen
import cl.segfault.coffeessh.ui.about.OpenSourceLicensesScreen
import cl.segfault.coffeessh.ui.groups.GroupsScreen
import cl.segfault.coffeessh.ui.identities.IdentityEditorScreen
import cl.segfault.coffeessh.ui.settings.SettingsScreen
import cl.segfault.coffeessh.ui.terminal.TerminalSessionScreen
import cl.segfault.coffeessh.CoffeeSshApp

object Routes {
    const val DASHBOARD = "dashboard"
    const val CONNECTIONS = "connections"
    const val SETTINGS = "settings"
    const val GROUPS = "groups"
    const val CONNECTION_NEW = "connection/new"
    const val CONNECTION_EDIT = "connection/{connectionId}"
    const val IDENTITY_NEW = "identity/new"
    const val IDENTITY_EDIT = "identity/{identityId}"
    const val TERMINAL_SESSION = "terminal/session/{connectionId}/{sessionId}"
    const val ABOUT = "about"
    const val OPEN_SOURCE_LICENSES = "about/licenses"

    fun connectionEdit(id: Long) = "connection/$id"
    fun identityEdit(id: Long) = "identity/$id"
    fun terminalSession(id: Long, sessionId: String) = "terminal/session/$id/$sessionId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val app = LocalContext.current.applicationContext as CoffeeSshApp
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenConnections = { navController.navigate(Routes.CONNECTIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenConnection = {
                    val session = app.container.sshSessionRegistry.create(it)
                    navController.navigate(Routes.terminalSession(it, session.sessionId))
                },
                onOpenActiveSession = { connectionId, sessionId ->
                    navController.navigate(Routes.terminalSession(connectionId, sessionId))
                },
            )
        }
        composable(Routes.CONNECTIONS) {
            ConnectionsScreen(
                onBack = { navController.popBackStack() },
                onNewConnection = { navController.navigate(Routes.CONNECTION_NEW) },
                onConnect = {
                    val session = app.container.sshSessionRegistry.create(it)
                    navController.navigate(Routes.terminalSession(it, session.sessionId))
                },
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
        composable(
            route = Routes.TERMINAL_SESSION,
            arguments = listOf(
                navArgument("connectionId") { type = NavType.LongType },
                navArgument("sessionId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getLong("connectionId") ?: return@composable
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            TerminalSessionScreen(
                connectionId = connectionId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenLicenses = { navController.navigate(Routes.OPEN_SOURCE_LICENSES) },
            )
        }
        composable(Routes.OPEN_SOURCE_LICENSES) {
            OpenSourceLicensesScreen(onBack = { navController.popBackStack() })
        }
    }
}
