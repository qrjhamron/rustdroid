package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.LogUiState
import com.rustdroid.manager.ui.components.EmptyStateCard
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.ScreenHeader

@Composable
fun LogScreen(
    state: LogUiState,
    onRefresh: (category: String) -> Unit,
    onClearCategory: (category: String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh(state.selectedCategory) }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            lazyListState.animateScrollToItem(state.entries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(title = "Logs", subtitle = "System activity history") {
            TextButton(onClick = { onClearCategory(state.selectedCategory) }) { Text("Clear") }
            IconButton(onClick = { onRefresh(state.selectedCategory) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh logs")
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        state.errorMessage?.let { error ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(12.dp)
            ) {
                Text("Native log unavailable", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                Text(error, style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            return
        }

        if (state.isEmpty) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Rounded.Subject,
                title = "No logs yet",
                subtitle = "Log history is empty."
            )
            return
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.entries) { entry ->
                val formattedLine = remember(entry) {
                    val time = entry.timestamp.substringAfter('T', entry.timestamp).take(8).ifBlank { "--:--:--" }
                    val tag = entry.category.uppercase()
                    "[$time] [$tag] ${entry.message}"
                }
                Text(
                    text = formattedLine,
                    color = Color(0xFF33FF33),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
