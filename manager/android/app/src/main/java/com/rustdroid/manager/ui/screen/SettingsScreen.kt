package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.ui.SettingsUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRefreshNative: () -> Unit,
    onExportReport: () -> Unit,
    onClearMessage: () -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    // Status message dialog
    state.statusMessage?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClearMessage,
            title = { Text(text = "Status", fontWeight = FontWeight.Bold) },
            text = { Text(text = message, fontSize = 13.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onClearMessage) {
                    Text(text = "OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // General section
        item {
            Text(
                text = "General",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SuperArrow(
                    title = "Refresh native status",
                    summary = "Re-check the native bridge and daemon state",
                    onClick = onRefreshNative
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SuperArrow(
                    title = "Export report bundle",
                    summary = "Save compatibility and status reports to file",
                    onClick = onExportReport
                )
            }
        }

        // About section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "About",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "About",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RustDroid Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsInfoRow("App version", state.appVersion)
                    SettingsInfoRow("RustDroid version", state.rustdroidVersion)
                    SettingsInfoRow(
                        "Native bridge",
                        if (state.nativeLoaded) "Loaded" else "Not loaded"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Auditable Rust-based Android root manager.\nNo bypasses, no root hiding, no stealth.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
