package com.rustdroid.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustdroid.manager.ui.MainViewModel
import com.rustdroid.manager.ui.screen.*
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RustDroidApp()
        }
    }
}

private enum class Tab(
    val label: String,
    val icon: ImageVector
) {
    Home("Home", Icons.Rounded.Home),
    Log("Log", Icons.Rounded.Description),
    Superuser("Superuser", Icons.Rounded.Security),
    Settings("Settings", Icons.Rounded.Settings),
    Modules("Modules", Icons.Rounded.Extension)
}

@Composable
private fun RustDroidApp() {
    MiuixTheme {
        val viewModel: MainViewModel = viewModel()
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = Tab.entries

        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = tab.icon,
                            label = tab.label
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (tabs[selectedTab]) {
                    Tab.Home -> HomeScreen(
                        state = viewModel.homeState,
                        onRefresh = { viewModel.refreshHome() }
                    )
                    Tab.Log -> LogScreen(
                        state = viewModel.logState,
                        onLoadLog = { viewModel.loadLog(it) }
                    )
                    Tab.Superuser -> SuperuserScreen(
                        state = viewModel.superuserState,
                        onRefresh = { viewModel.refreshSuperuser() },
                        onRevoke = { viewModel.revokePolicy(it) }
                    )
                    Tab.Settings -> SettingsScreen(
                        state = viewModel.settingsState,
                        onRefreshNative = { viewModel.refreshNativeStatus() },
                        onExportReport = { viewModel.exportReportBundle() },
                        onClearMessage = { viewModel.clearSettingsMessage() },
                        onRefresh = { viewModel.refreshSettings() }
                    )
                    Tab.Modules -> ModulesScreen(
                        state = viewModel.modulesState,
                        onRefresh = { viewModel.refreshModules() },
                        onToggle = { id, enable -> viewModel.toggleModule(id, enable) },
                        onRemove = { viewModel.removeModule(it) },
                        onInstall = { viewModel.installModuleFromPath(it) },
                        onClearMessage = { viewModel.clearModulesStatusMessage() }
                    )
                }
            }
        }
    }
}
