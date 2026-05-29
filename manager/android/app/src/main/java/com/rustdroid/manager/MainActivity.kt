package com.rustdroid.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustdroid.manager.ui.MainViewModel
import com.rustdroid.manager.ui.PatchFlowState
import com.rustdroid.manager.ui.screen.HomeScreen
import com.rustdroid.manager.ui.screen.LogScreen
import com.rustdroid.manager.ui.screen.ModulesScreen
import com.rustdroid.manager.ui.screen.PatchFlowScreen
import com.rustdroid.manager.ui.screen.SettingsScreen
import com.rustdroid.manager.ui.screen.SuperuserScreen
import com.rustdroid.manager.ui.theme.RustDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RustDroidApp() }
    }
}

private enum class Tab(val label: String) {
    Home("Home"),
    Superuser("Superuser"),
    Modules("Modules"),
    Logs("Logs"),
    Settings("Settings")
}

@Composable
private fun RustDroidApp() {
    val viewModel: MainViewModel = viewModel()

    RustDroidTheme(themeMode = viewModel.settingsState.appSettings.themeMode) {
        var selectedTab by remember { mutableIntStateOf(0) }
        var showPatchFlow by remember { mutableStateOf(false) }
        val tabs = Tab.entries

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!showPatchFlow) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = androidx.compose.ui.unit.Dp.Hairline
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            Tab.Home -> Icons.Rounded.Home
                                            Tab.Superuser -> Icons.Rounded.AdminPanelSettings
                                            Tab.Modules -> Icons.Rounded.Extension
                                            Tab.Logs -> Icons.AutoMirrored.Rounded.Subject
                                            Tab.Settings -> Icons.Rounded.Settings
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (showPatchFlow) {
                    PatchFlowScreen(
                        state = viewModel.homeState,
                        onBack = {
                            if (viewModel.homeState.patch.flowState != PatchFlowState.Patching) {
                                showPatchFlow = false
                            }
                        },
                        onPrepare = viewModel::preparePatchFlow,
                        onSelectBootImage = viewModel::setSelectedBootImage,
                        onStartPatch = viewModel::patchBootImage,
                        onResetPatch = viewModel::resetPatchState,
                        onOpenLogs = {
                            showPatchFlow = false
                            selectedTab = tabs.indexOf(Tab.Logs)
                            viewModel.refreshLogs("patch")
                        }
                    )
                    return@Box
                }

                when (tabs[selectedTab]) {
                    Tab.Home -> HomeScreen(
                        state = viewModel.homeState,
                        superuserCount = viewModel.superuserState.entries.size,
                        moduleCount = viewModel.modulesState.modules.size,
                        onRefresh = viewModel::refreshHome,
                        onOpenPatcher = { showPatchFlow = true },
                        onOpenSettings = { selectedTab = tabs.indexOf(Tab.Settings) }
                    )

                    Tab.Superuser -> SuperuserScreen(state = viewModel.superuserState)
                    Tab.Modules -> ModulesScreen(state = viewModel.modulesState)
                    Tab.Logs -> LogScreen(
                        state = viewModel.logState,
                        onRefresh = viewModel::refreshLogs,
                        onClearCategory = viewModel::clearLogCategory
                    )

                    Tab.Settings -> SettingsScreen(
                        state = viewModel.settingsState,
                        onThemeChange = viewModel::setThemeMode,
                        onLanguageChange = viewModel::setLanguageMode,
                        onChannelChange = viewModel::setUpdateChannel,
                        onCustomChannelChange = viewModel::setCustomChannel,
                        onReloadNative = viewModel::reloadNativeStatus,
                        onExportNativeDiagnostics = viewModel::exportNativeDiagnostics,
                        onClearMessage = viewModel::clearDiagnosticsMessage
                    )
                }
            }
        }
    }
}
