package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.data.ThemeMode
import com.rustdroid.manager.ui.NativeStatusLevel
import com.rustdroid.manager.ui.SettingsUiState
import com.rustdroid.manager.ui.components.CompactInfoRow
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.ScreenHeader
import com.rustdroid.manager.ui.components.SectionCard
import com.rustdroid.manager.ui.components.StatusPill
import com.rustdroid.manager.ui.components.SuccessGreen

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (String) -> Unit,
    onOutputNamingFormatChange: (String) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onReloadNative: () -> Unit,
    onExportNativeDiagnostics: () -> Unit,
    onClearMessage: () -> Unit
) {
    var showNativeDetails by remember { mutableStateOf(false) }

    state.diagnosticsMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onClearMessage,
            title = { Text("Diagnostics") },
            text = { Text(message, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = onClearMessage) { Text("Close") } }
        )
    }

    if (showNativeDetails) {
        AlertDialog(
            onDismissRequest = { showNativeDetails = false },
            title = { Text("Native details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactInfoRow("Status", state.nativeStatus.label)
                    CompactInfoRow("Library", state.nativeStatus.libraryName)
                    CompactInfoRow("ABI", state.nativeStatus.abi)
                    CompactInfoRow("Version", state.nativeStatus.version)
                    CompactInfoRow("Error", state.nativeStatus.error ?: "none")
                }
            },
            confirmButton = { TextButton(onClick = { showNativeDetails = false }) { Text("Close") } }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        item { ScreenHeader("Settings", "Manager preferences") }

        item {
            SectionCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Text("Theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OptionChips(
                    options = ThemeMode.entries,
                    selected = state.appSettings.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.LIGHT -> "Light"
                        }
                    },
                    onSelect = onThemeChange
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Accent color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OptionChips(
                    options = listOf("Graphite", "Slate", "Midnight"),
                    selected = state.appSettings.accentColor,
                    label = { it },
                    onSelect = onAccentColorChange
                )
            }
        }

        item {
            SectionCard {
                Text("Patch", style = MaterialTheme.typography.titleMedium)
                
                OutlinedTextField(
                    value = state.appSettings.outputNamingFormat,
                    onValueChange = onOutputNamingFormatChange,
                    label = { Text("Output naming format") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Verbose logging", style = MaterialTheme.typography.bodyLarge)
                        Text("Enable verbose native logs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.appSettings.verboseLogging,
                        onCheckedChange = onVerboseLoggingChange
                    )
                }
            }
        }

        item {
            SectionCard {
                Text("About / Diagnostics", style = MaterialTheme.typography.titleMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        text = "Native status: ${if (state.nativeStatus.level == NativeStatusLevel.Ready) "Ready" else "Unavailable"}",
                        color = if (state.nativeStatus.level == NativeStatusLevel.Ready) SuccessGreen else ErrorRed
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(
                        onClick = onReloadNative,
                        label = { Text("Run self-check") },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = onExportNativeDiagnostics,
                        label = { Text("Export diagnostics") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                AssistChip(
                    onClick = { showNativeDetails = true },
                    label = { Text("View native details") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                CompactInfoRow("App version", state.appVersion)
                CompactInfoRow("RustDroid version", state.rustdroidVersion)
                CompactInfoRow("License", "GPL-compatible project components")
            }
        }
    }
}

@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}
