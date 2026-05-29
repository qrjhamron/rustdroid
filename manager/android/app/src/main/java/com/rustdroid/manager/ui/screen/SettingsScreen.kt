package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.data.LanguageMode
import com.rustdroid.manager.data.ThemeMode
import com.rustdroid.manager.data.UpdateChannel
import com.rustdroid.manager.ui.SettingsUiState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (LanguageMode) -> Unit,
    onChannelChange: (UpdateChannel) -> Unit,
    onCustomChannelChange: (String) -> Unit,
    onReloadNative: () -> Unit,
    onExportNativeDiagnostics: () -> Unit,
    onClearMessage: () -> Unit
) {
    state.statusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onClearMessage,
            title = { Text("Status") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onClearMessage) {
                    Text("OK")
                }
            }
        )
    }

    var customChannel by remember(state.appSettings.customChannel) {
        mutableStateOf(state.appSettings.customChannel)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            SectionTitle("Appearance")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme")
                    OptionChips(
                        options = ThemeMode.entries,
                        selected = state.appSettings.themeMode,
                        label = { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) },
                        onSelect = onThemeChange
                    )
                    Text("Accent color")
                    Text(state.appSettings.accentColor, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SectionTitle("Language")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionChips(
                        options = LanguageMode.entries,
                        selected = state.appSettings.languageMode,
                        label = {
                            when (it) {
                                LanguageMode.SYSTEM -> "System default"
                                LanguageMode.ENGLISH -> "English"
                                LanguageMode.INDONESIAN -> "Indonesian"
                            }
                        },
                        onSelect = onLanguageChange
                    )
                }
            }
        }

        item {
            SectionTitle("Update / Channel")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionChips(
                        options = UpdateChannel.entries,
                        selected = state.appSettings.updateChannel,
                        label = {
                            when (it) {
                                UpdateChannel.STABLE -> "Stable"
                                UpdateChannel.BETA -> "Beta"
                                UpdateChannel.CANARY -> "Canary"
                                UpdateChannel.CUSTOM -> "Custom"
                            }
                        },
                        onSelect = onChannelChange
                    )

                    if (state.appSettings.updateChannel == UpdateChannel.CUSTOM) {
                        OutlinedTextField(
                            value = customChannel,
                            onValueChange = {
                                customChannel = it
                                onCustomChannelChange(it)
                            },
                            label = { Text("Custom channel") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Native")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Native library status: ${if (state.nativeStatus.loaded) "Loaded" else "Not loaded"}")
                    Text("ABI: ${state.nativeStatus.abi}")
                    Text("Version: ${state.nativeStatus.version}")
                    if (!state.nativeStatus.error.isNullOrBlank()) {
                        Text(state.nativeStatus.error, color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onReloadNative) { Text("Reload native status") }
                        TextButton(onClick = onExportNativeDiagnostics) { Text("Export diagnostics") }
                    }
                }
            }
        }

        item {
            SectionTitle("About")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("App version", state.appVersion)
                    InfoRow("RustDroid version", state.rustdroidVersion)
                    InfoRow("Native library", state.nativeStatus.libraryName)
                    InfoRow("Native ABI", state.nativeStatus.abi)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            AssistChip(
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
