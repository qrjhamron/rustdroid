package com.rustdroid.manager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.ui.ModuleEntry
import com.rustdroid.manager.ui.ModulesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import java.io.File

@Composable
fun ModulesScreen(
    state: ModulesUiState,
    onRefresh: () -> Unit,
    onToggle: (moduleId: String, enable: Boolean) -> Unit,
    onRemove: (moduleId: String) -> Unit,
    onInstall: (zipPath: String) -> Unit,
    onClearMessage: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "temp_module.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onInstall(cacheFile.absolutePath)
                } catch (_: Exception) {}
            }
        }
    }

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

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    // Error state
    if (state.errorMessage != null && state.modules.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Extension,
            title = "Modules unavailable",
            subtitle = state.errorMessage
        )
        return
    }

    // Empty state
    if (state.modules.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { pickZipLauncher.launch("application/zip") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Install",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Install from storage", color = Color.White)
            }
            EmptyState(
                icon = Icons.Rounded.Extension,
                title = "No modules installed",
                subtitle = "Install a module ZIP to get started."
            )
        }
        return
    }

    // Module list
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Button(
                onClick = { pickZipLauncher.launch("application/zip") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Install",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Install from storage", color = Color.White)
            }
        }

        items(state.modules) { module ->
            ModuleItem(
                module = module,
                onToggle = { onToggle(module.id, it) },
                onRemove = { onRemove(module.id) }
            )
        }
    }
}

@Composable
private fun ModuleItem(
    module: ModuleEntry,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "v${module.version} · ${module.author}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onToggle
                )
            }
            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
