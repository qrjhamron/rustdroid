package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.ui.LogUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

private val LOG_FILES = listOf("su.log", "daemon.log", "first_boot.log", "self_check.log", "module.log")

@Composable
fun LogScreen(
    state: LogUiState,
    onLoadLog: (logName: String) -> Unit
) {
    LaunchedEffect(Unit) { onLoadLog(state.selectedLog) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header with refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Logs",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            IconButton(onClick = { onLoadLog(state.selectedLog) }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log file chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LOG_FILES.forEach { log ->
                val isSelected = log == state.selectedLog
                androidx.compose.material3.TextButton(
                    onClick = { onLoadLog(log) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
                        contentColor = if (isSelected) Color.White else Color.Gray
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = log.substringBefore("."),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Loading
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            return
        }

        // Error state
        state.errorMessage?.let {
            EmptyState(
                icon = Icons.Rounded.Description,
                title = "Logs unavailable",
                subtitle = it
            )
            return
        }

        // Empty state
        if (state.logLines.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Description,
                title = "No log entries",
                subtitle = "This log file is empty."
            )
            return
        }

        // Log content
        Card(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(10.dp)
            ) {
                items(state.logLines) { line ->
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
