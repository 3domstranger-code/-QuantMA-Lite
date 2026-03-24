package com.quantma.lite.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.quantma.lite.ui.agentconfig.AgentConfigScreen
import com.quantma.lite.ui.agentconfig.SkillsRulesHooksScreen
import com.quantma.lite.ui.chat.ChatScreen
import com.quantma.lite.ui.editor.EditorScreen
import com.quantma.lite.ui.filebrowser.FileBrowserScreen
import com.quantma.lite.ui.git.GitScreen
import com.quantma.lite.ui.git.credentials.GitCredentialsScreen
import com.quantma.lite.ui.models.ModelCatalogScreen
import com.quantma.lite.ui.onboarding.OnboardingScreen
import com.quantma.lite.ui.cli.CliScreen
import com.quantma.lite.ui.settings.LicensesScreen
import com.quantma.lite.ui.settings.SettingsScreen

@Composable
fun NavGraph(startDestination: String = Screen.Chat.route) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Chat, Screen.Files, Screen.Settings)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            // Hide bottom nav for full-screen routes
            if (currentRoute?.startsWith("editor/") != true &&
                currentRoute?.startsWith("git/") != true &&
                currentRoute != "agent_config" &&
                currentRoute != "skills_rules_hooks" &&
                currentRoute != "git_credentials" &&
                currentRoute != "onboarding" &&
                currentRoute != "model_catalog" &&
                currentRoute != "licenses" &&
                currentRoute != "cli") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val currentDestination = navBackStackEntry?.destination

                    screens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    screen.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    onNavigateToCli = { navController.navigate("cli") }
                )
            }
            composable(Screen.Files.route) {
                FileBrowserScreen(
                    onFileSelected = { path ->
                        val encoded = Uri.encode(path)
                        navController.navigate("editor/$encoded")
                    },
                    onOpenGit = { repoPath ->
                        val encoded = Uri.encode(repoPath)
                        navController.navigate("git/$encoded")
                    }
                )
            }
            composable(
                "editor/{filePath}",
                arguments = listOf(navArgument("filePath") { type = NavType.StringType })
            ) { backStackEntry ->
                val filePath = Uri.decode(
                    backStackEntry.arguments?.getString("filePath") ?: ""
                )
                EditorScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "git/{repoPath}",
                arguments = listOf(navArgument("repoPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val repoPath = Uri.decode(
                    backStackEntry.arguments?.getString("repoPath") ?: ""
                )
                GitScreen(
                    repoPath = repoPath,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAgentConfig = { navController.navigate("agent_config") },
                    onNavigateToSkillsRulesHooks = { navController.navigate("skills_rules_hooks") },
                    onNavigateToGitCredentials = { navController.navigate("git_credentials") },
                    onNavigateToModelCatalog = { navController.navigate("model_catalog") },
                    onNavigateToLicenses = { navController.navigate("licenses") }
                )
            }
            composable("licenses") {
                LicensesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("git_credentials") {
                GitCredentialsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("agent_config") {
                AgentConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("skills_rules_hooks") {
                SkillsRulesHooksScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("onboarding") {
                OnboardingScreen(
                    onNavigateToModelCatalog = { navController.navigate("model_catalog") },
                    onFinish = {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("model_catalog") {
                ModelCatalogScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("cli") {
                CliScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
