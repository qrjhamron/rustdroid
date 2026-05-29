package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.SuperuserUiState
import com.rustdroid.manager.ui.components.CompactInfoRow
import com.rustdroid.manager.ui.components.EmptyStateCard
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.ScreenHeader
import com.rustdroid.manager.ui.components.SectionCard

@Composable
fun SuperuserScreen(state: SuperuserUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("Superuser", "App access requests") }
        state.backendError?.let { error ->
            item {
                SectionCard {
                    Text("Superuser backend error", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.entries.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "No superuser requests",
                    subtitle = "Apps requesting access will appear here."
                )
            }
        } else {
            items(state.entries.size) { index ->
                val entry = state.entries[index]
                SectionCard {
                    Text(entry.appName, style = MaterialTheme.typography.titleMedium)
                    CompactInfoRow("Package", entry.packageName)
                    CompactInfoRow("Access", entry.state)
                    CompactInfoRow("Last used", entry.lastUsed)
                }
            }
        }
    }
}
