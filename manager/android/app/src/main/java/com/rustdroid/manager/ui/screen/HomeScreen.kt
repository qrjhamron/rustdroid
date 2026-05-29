package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.HomeUiState
import com.rustdroid.manager.ui.NativeStatusLevel
import com.rustdroid.manager.ui.components.ActionCard
import com.rustdroid.manager.ui.components.CompactInfoRow
import com.rustdroid.manager.ui.components.InactiveGrey
import com.rustdroid.manager.ui.components.ScreenHeader
import com.rustdroid.manager.ui.components.SectionCard
import com.rustdroid.manager.ui.components.StatusChip
import com.rustdroid.manager.ui.components.SuccessGreen
import com.rustdroid.manager.ui.components.WarningYellow

@Composable
fun HomeScreen(
    state: HomeUiState,
    superuserCount: Int,
    moduleCount: Int,
    onRefresh: () -> Unit,
    onOpenPatcher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
    ) {
        item {
            ScreenHeader(title = "RustDroid", subtitle = "Boot image patch manager") {
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
            }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Manager status", style = MaterialTheme.typography.titleLarge)
                        Text("Everything needed before patching.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(
                        text = state.patchEngineLabel,
                        color = if (state.canPatch) SuccessGreen else WarningYellow
                    )
                }
                CompactInfoRow("Native status", if (state.nativeStatus.level == NativeStatusLevel.Ready) "Ready" else "Unavailable")
                CompactInfoRow("Boot image", state.bootImage.fileName ?: "Not selected")
                CompactInfoRow("Patch engine", state.patchEngineLabel)
                state.patchDisabledReason?.let { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ActionCard(
                title = "Patch boot image",
                subtitle = state.bootImage.fileName ?: "Select an original boot.img to begin",
                actionLabel = if (state.bootImage.fileName == null) "Select boot.img" else "Open patcher",
                onAction = onOpenPatcher,
                icon = Icons.Rounded.FolderOpen
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CompactSummary(
                    title = "Superuser",
                    value = if (superuserCount == 0) "No requests" else "$superuserCount requests",
                    icon = Icons.Rounded.AdminPanelSettings,
                    modifier = Modifier.weight(1f)
                )
                CompactSummary(
                    title = "Modules",
                    value = if (moduleCount == 0) "Not enabled" else "$moduleCount installed",
                    icon = Icons.Rounded.Extension,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactSummary(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = InactiveGrey)
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
