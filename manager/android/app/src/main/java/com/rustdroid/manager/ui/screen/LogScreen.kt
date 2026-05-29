package com.rustdroid.manager.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.LogCategories
import com.rustdroid.manager.ui.LogUiState
import com.rustdroid.manager.ui.components.EmptyStateCard
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.LogRow
import com.rustdroid.manager.ui.components.ScreenHeader
import com.rustdroid.manager.ui.components.SectionCard

@Composable
fun LogScreen(
    state: LogUiState,
    onRefresh: (category: String) -> Unit,
    onClearCategory: (category: String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh(state.selectedCategory) }

    val context = LocalContext.current
    var expandedIndex by remember(state.selectedCategory) { mutableStateOf<Int?>(null) }
    val selectedLabel = LogCategories.firstOrNull { it.first == state.selectedCategory }?.second ?: "Patch"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(title = "Logs", subtitle = "Focused activity history") {
            TextButton(onClick = { onClearCategory(state.selectedCategory) }) { Text("Clear") }
            IconButton(onClick = { onRefresh(state.selectedCategory) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh logs")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogCategories.forEach { (key, label) ->
                AssistChip(
                    onClick = { onRefresh(key) },
                    label = { Text(label) },
                    leadingIcon = if (key == state.selectedCategory) {
                        { Icon(Icons.AutoMirrored.Rounded.Subject, contentDescription = null) }
                    } else null
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        state.errorMessage?.let { error ->
            SectionCard {
                Text("Native log unavailable", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        if (state.isEmpty) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Rounded.Subject,
                title = "No logs yet",
                subtitle = emptySubtitle(selectedLabel)
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.entries.size) { index ->
                val row = state.entries[index]
                LogRow(
                    entry = row,
                    expanded = expandedIndex == index,
                    onClick = { expandedIndex = if (expandedIndex == index) null else index },
                    onCopy = { context.copyToClipboard("rustdroid-log", row.message) }
                )
            }
        }
    }
}

private fun emptySubtitle(label: String): String = when (label) {
    "Patch" -> "Patch activity will appear after selecting and patching a boot image."
    "Native" -> "Native loader and parser messages will appear here."
    "Superuser" -> "Superuser request events will appear here."
    "Modules" -> "Module activity will appear when module support is enabled."
    else -> "System events will appear here."
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
