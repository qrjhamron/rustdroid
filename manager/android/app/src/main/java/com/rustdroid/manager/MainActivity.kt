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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RustDroidApp() }
    }
}

private enum class Tab(val label: String) {
    Patch("Patch"),
    Logs("Logs"),
    Superuser("Superuser"),
    Modules("Modules"),
    Settings("Settings")
}

@Composable
private fun RustDroidApp() {
    val viewModel: MainViewModel = viewModel()

    RustDroidTheme(themeMode = viewModel.settingsState.appSettings.themeMode) {
        var selectedTab by remember { mutableIntStateOf(0) }
        var showPatchFlow by remember { mutableStateOf(false) }
        val tabs = Tab.entries

        val isPatchApplied = viewModel.settingsState.appSettings.isPatchApplied
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!showPatchFlow) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = androidx.compose.ui.unit.Dp.Hairline
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isLocked = (tab == Tab.Superuser || tab == Tab.Modules) && !isPatchApplied
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = {
                                    if (isLocked) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Flash your patched boot image first to unlock this section.")
                                        }
                                    } else {
                                        selectedTab = index
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            Tab.Patch -> Icons.Rounded.Home
                                            Tab.Logs -> Icons.AutoMirrored.Rounded.Subject
                                            Tab.Superuser -> Icons.Rounded.AdminPanelSettings
                                            Tab.Modules -> Icons.Rounded.Extension
                                            Tab.Settings -> Icons.Rounded.Settings
                                        },
                                        contentDescription = tab.label,
                                        modifier = if (isLocked) Modifier.alpha(0.38f) else Modifier
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = if (isLocked) Modifier.alpha(0.38f) else Modifier
                                    )
                                },
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
                        isPatchApplied = isPatchApplied,
                        onConfirmFlashApplied = viewModel::setPatchApplied,
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
                    Tab.Patch -> HomeScreen(
                        state = viewModel.homeState,
                        superuserCount = viewModel.superuserState.entries.size,
                        moduleCount = viewModel.modulesState.modules.size,
                        onRefresh = viewModel::refreshHome,
                        onOpenPatcher = { showPatchFlow = true },
                        onOpenSettings = { selectedTab = tabs.indexOf(Tab.Settings) }
                    )

                    Tab.Logs -> LogScreen(
                        state = viewModel.logState,
                        onRefresh = viewModel::refreshLogs,
                        onClearCategory = viewModel::clearLogCategory
                    )

                    Tab.Superuser -> SuperuserScreen(state = viewModel.superuserState)
                    Tab.Modules -> ModulesScreen(state = viewModel.modulesState)
                    Tab.Settings -> SettingsScreen(
                        state = viewModel.settingsState,
                        onThemeChange = viewModel::setThemeMode,
                        onAccentColorChange = viewModel::setAccentColor,
                        onOutputNamingFormatChange = viewModel::setOutputNamingFormat,
                        onVerboseLoggingChange = viewModel::setVerboseLogging,
                        onReloadNative = viewModel::reloadNativeStatus,
                        onExportNativeDiagnostics = viewModel::exportNativeDiagnostics,
                        onClearMessage = viewModel::clearDiagnosticsMessage
                    )
                }
            }
        }
    }
}
