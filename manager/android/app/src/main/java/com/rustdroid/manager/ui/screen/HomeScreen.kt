package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.ui.HomeUiState
import com.rustdroid.manager.ui.StatusLevel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RustDroid",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auditable Rust Root Manager",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF0284C7)
                    )
                }
            }
        }

        // Error banner
        state.errorMessage?.let { error ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }

        // Status card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    StatusRow("RustDroid", state.rustdroidStatus, statusColor(state.rustdroidStatus))
                    StatusRow("Native Bridge", state.nativeBridgeStatus, statusColor(state.nativeBridgeStatus))
                    StatusRow("Root", state.rootStatus, statusColor(state.rootStatus))
                    StatusRow("Daemon", state.daemonStatus, statusColor(state.daemonStatus))
                }
            }
        }

        // Compatibility card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Compatibility",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    StatusLevelRow("Device", state.deviceCompatibility)
                    StatusLevelRow("Runtime", state.runtimeCompatibility)
                }
            }
        }

        // Release readiness card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Release Readiness",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Level", fontSize = 12.sp, color = Color.Gray)
                        StatusChip(
                            text = state.releaseReadiness,
                            level = StatusLevel.fromString(state.releaseReadiness)
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

@Composable
private fun StatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun StatusLevelRow(label: String, level: StatusLevel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        StatusChip(text = level.name, level = level)
    }
}

@Composable
private fun StatusChip(text: String, level: StatusLevel) {
    val (bgColor, fgColor, icon) = when (level) {
        StatusLevel.Ready -> Triple(Color(0xFF10B981), Color.White, Icons.Rounded.CheckCircle)
        StatusLevel.Warning -> Triple(Color(0xFFF59E0B), Color.White, Icons.Rounded.Warning)
        StatusLevel.Blocked -> Triple(Color(0xFFEF4444), Color.White, Icons.Rounded.Error)
        StatusLevel.Unsupported -> Triple(Color(0xFF6B7280), Color.White, Icons.Rounded.Error)
        StatusLevel.Unavailable -> Triple(Color(0xFF9CA3AF), Color.White, Icons.Rounded.Error)
        StatusLevel.Unknown -> Triple(Color(0xFF6B7280), Color.White, Icons.Rounded.Warning)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = bgColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = bgColor
        )
    }
}

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "active", "loaded", "connected", "patched" -> Color(0xFF10B981)
    "unavailable", "not loaded", "offline", "not patched" -> Color(0xFF9CA3AF)
    else -> Color(0xFF6B7280)
}
