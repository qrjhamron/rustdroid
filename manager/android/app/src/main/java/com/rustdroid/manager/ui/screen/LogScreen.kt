package com.rustdroid.manager.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.LogCategories
import com.rustdroid.manager.ui.LogUiState

@Composable
fun LogScreen(
    state: LogUiState,
    onRefresh: (category: String) -> Unit,
    onClearCategory: (category: String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh(state.selectedCategory) }

    val context = LocalContext.current
    val selectedLine = remember { mutableStateOf<String?>(null) }

    selectedLine.value?.let { line ->
        AlertDialog(
            onDismissRequest = { selectedLine.value = null },
            title = { Text("Log line") },
            text = { Text(line) },
            confirmButton = {
                TextButton(onClick = { selectedLine.value = null }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    copyToClipboard(context, line)
                    selectedLine.value = null
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("Copy")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row {
                IconButton(onClick = { onClearCategory(state.selectedCategory) }) {
                    Text("Clear", style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { onRefresh(state.selectedCategory) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LogCategories.forEach { category ->
                AssistChip(
                    onClick = { onRefresh(category) },
                    label = { Text(category) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        state.errorMessage?.let { error ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp)
                )
            }
            return
        }

        if (state.entries.isEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.message ?: "No logs yet",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.entries) { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLine.value = row.message }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${row.timestamp} • ${row.level.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = row.message,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("rustdroid-log", text))
}
