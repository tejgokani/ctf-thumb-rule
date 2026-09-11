package com.tejgokani.strata.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.ui.screens.files.FilesScreen
import com.tejgokani.strata.ui.screens.ledger.LedgerScreen
import com.tejgokani.strata.ui.screens.nodes.NodesScreen
import com.tejgokani.strata.ui.screens.settings.SettingsScreen
import com.tejgokani.strata.ui.screens.shardmap.ShardMapScreen
import com.tejgokani.strata.ui.screens.transfers.TransfersScreen
import com.tejgokani.strata.ui.screens.vault.VaultScreen
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.FilesViewModel
import com.tejgokani.strata.ui.vm.LedgerViewModel
import com.tejgokani.strata.ui.vm.NodesViewModel
import com.tejgokani.strata.ui.vm.SettingsViewModel
import com.tejgokani.strata.ui.vm.ShardMapViewModel
import com.tejgokani.strata.ui.vm.StrataViewModelFactory
import com.tejgokani.strata.ui.vm.TransfersViewModel
import com.tejgokani.strata.ui.vm.VaultViewModel

private sealed class BottomTab(val route: String, val label: String) {
    object Vault : BottomTab("vault", "VAULT")
    object Nodes : BottomTab("nodes", "NODES")
    object Files : BottomTab("files", "FILES")
    object Transfers : BottomTab("transfers", "XFER")
    object Ledger : BottomTab("ledger", "LOG")
    object Settings : BottomTab("settings", "SET")
}

private val bottomTabs = listOf(BottomTab.Vault, BottomTab.Nodes, BottomTab.Files, BottomTab.Transfers, BottomTab.Ledger, BottomTab.Settings)

@Composable
fun StrataNavHost(container: AppContainer, onLocked: () -> Unit) {
    val navController = rememberNavController()
    val factory = StrataViewModelFactory(container)

    Scaffold(
        containerColor = StrataColors.Bg,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar(containerColor = StrataColors.Surface, tonalElevation = 0.dp) {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(tab.label, style = StrataType.Label.copy(fontWeight = FontWeight.Bold)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = StrataColors.Signal, unselectedTextColor = StrataColors.Dim,
                            indicatorColor = StrataColors.Grid,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = BottomTab.Vault.route, modifier = Modifier.padding(padding).background(StrataColors.Bg)) {
            composable(BottomTab.Vault.route) { VaultScreen(viewModel(factory = factory)) }
            composable(BottomTab.Nodes.route) { NodesScreen(viewModel(factory = factory)) }
            composable(BottomTab.Files.route) {
                val vm: FilesViewModel = viewModel(factory = factory)
                FilesScreen(vm, onOpenShardMap = { fileId -> navController.navigate("shardmap/$fileId") })
            }
            composable("shardmap/{fileId}") { backStackEntry ->
                val fileId = backStackEntry.arguments?.getString("fileId") ?: return@composable
                val vm: ShardMapViewModel = viewModel(factory = factory)
                ShardMapScreen(vm, fileId)
            }
            composable(BottomTab.Transfers.route) { TransfersScreen(viewModel(factory = factory)) }
            composable(BottomTab.Ledger.route) { LedgerScreen(viewModel(factory = factory)) }
            composable(BottomTab.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(vm, onLocked = onLocked)
            }
        }
    }
}
