package com.rustdroid.manager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PublishedWithChanges
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rustdroid.manager.ui.DeviceCompatibilityLevel
import com.rustdroid.manager.ui.HomeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onPatch: () -> Unit,
    onSelectBootImage: (path: String) -> Unit,
    onClearMessage: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val target = File(context.cacheDir, "selected_boot.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onSelectBootImage(target.absolutePath)
            }
        }
    }

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

    state.lastPatchResult?.takeIf { it.success }?.let { patchResult ->
        AlertDialog(
            onDismissRequest = onClearMessage,
            title = { Text("Patch created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Output: ${patchResult.outputPath ?: "Unavailable"}")
                    Text("SHA-256: ${patchResult.outputSha256 ?: "Unavailable"}")
                    Text(patchResult.manualFlashWarning, color = MaterialTheme.colorScheme.tertiary)
                }
            },
            confirmButton = {
                TextButton(onClick = onClearMessage) {
                    Text("Close")
                }
            }
        )
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RustDroid", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Rust-based Android root manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            if (state.nativeStatus.loaded) "Loaded" else "Not loaded",
                            color = if (state.nativeStatus.loaded) Color(0xFF3E6B56) else Color(0xFF7A5A5A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    KeyValue("Library", state.nativeStatus.libraryName)
                    KeyValue("ABI", state.nativeStatus.abi)
                    KeyValue("Version", state.nativeStatus.version)
                    if (!state.nativeStatus.loaded) {
                        Text(
                            state.nativeStatus.error ?: "Native library not loaded",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Boot Image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    KeyValue("Selected image", state.selectedImagePath?.substringAfterLast('/') ?: "unavailable")
                    KeyValue("Format", state.analysis?.format ?: "unknown")
                    KeyValue("Kernel", if (state.analysis?.kernelDetected == true) "detected" else "unknown")
                    KeyValue("Ramdisk", if (state.analysis?.ramdiskDetected == true) "detected" else "unknown")
                    KeyValue("Patch status", state.analysis?.patchStatus ?: "Not patched")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { launcher.launch("*/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select")
                        }
                        Button(
                            onClick = onPatch,
                            enabled = state.nativeStatus.loaded && !state.selectedImagePath.isNullOrBlank() && !state.isPatching,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isPatching) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Patching")
                            } else {
                                Icon(Icons.Rounded.PublishedWithChanges, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Patch / Pasang")
                            }
                        }
                    }

                    if (!state.nativeStatus.loaded) {
                        Text("Native library not loaded", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val level = state.deviceCompatibilityLevel
                        val icon = when (level) {
                            DeviceCompatibilityLevel.Ready -> Icons.Rounded.CheckCircle
                            DeviceCompatibilityLevel.Warning -> Icons.Rounded.Warning
                            DeviceCompatibilityLevel.Unknown -> Icons.Rounded.Warning
                            DeviceCompatibilityLevel.Blocked -> Icons.Rounded.Warning
                        }
                        Icon(icon, contentDescription = null)
                        Text(level.name)
                    }
                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
