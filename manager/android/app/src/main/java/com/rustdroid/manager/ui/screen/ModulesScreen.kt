package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.ModulesUiState
import com.rustdroid.manager.ui.components.CompactInfoRow
import com.rustdroid.manager.ui.components.EmptyStateCard
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.ScreenHeader
import com.rustdroid.manager.ui.components.SectionCard

@Composable
fun ModulesScreen(state: ModulesUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("Modules", "Systemless extensions") }
        state.backendError?.let { error ->
            item {
                SectionCard {
                    Text("Module backend error", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.modules.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Extension,
                    title = "No modules installed",
                    subtitle = "Module support is not enabled yet."
                )
            }
        } else {
            items(state.modules.size) { index ->
                val module = state.modules[index]
                SectionCard {
                    Text(module.name, style = MaterialTheme.typography.titleMedium)
                    CompactInfoRow("Version", module.version)
                    CompactInfoRow("State", if (module.enabled) "Enabled" else "Disabled")
                }
            }
        }
    }
}
