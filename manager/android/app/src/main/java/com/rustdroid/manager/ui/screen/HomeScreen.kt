package com.rustdroid.manager.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PublishedWithChanges
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rustdroid.manager.ui.BootImageStatus
import com.rustdroid.manager.ui.BootImageTechnicalDetails
import com.rustdroid.manager.ui.HomeUiState
import com.rustdroid.manager.ui.NativeStatusLevel
import com.rustdroid.manager.ui.PatchFlowState
import com.rustdroid.manager.ui.components.ActionButton
import com.rustdroid.manager.ui.components.CompactInfoRow
import com.rustdroid.manager.ui.components.DangerNote
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.InactiveGrey
import com.rustdroid.manager.ui.components.PrimaryActionCard
import com.rustdroid.manager.ui.components.ScreenHeader
import com.rustdroid.manager.ui.components.SectionCard
import com.rustdroid.manager.ui.components.StatusPill
import com.rustdroid.manager.ui.components.SuccessGreen
import com.rustdroid.manager.ui.components.WarningYellow
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
    onResetPatch: () -> Unit,
    onOpenLogs: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    val context = LocalContext.current
    var showTechnicalDetails by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val target = File(context.cacheDir, "selected_boot_${System.currentTimeMillis()}.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                onSelectBootImage(target.absolutePath)
            }
        }
    }

    if (showTechnicalDetails) {
        TechnicalDetailsDialog(
            details = state.bootImage.technicalDetails,
            onDismiss = { showTechnicalDetails = false }
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
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
    ) {
        item {
            ScreenHeader(
                title = "RustDroid",
                subtitle = "Boot image patch manager"
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh diagnostics")
                }
            }
        }

        item {
            StatusPill(
                text = state.nativeStatus.label,
                color = if (state.nativeStatus.level == NativeStatusLevel.Ready) SuccessGreen else ErrorRed
            )
        }

        item {
            PrimaryActionCard {
                Text("Patch Boot Image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

                when (state.patch.flowState) {
                    PatchFlowState.Patching -> PatchingContent()
                    PatchFlowState.Success -> PatchSuccessContent(state, context, onResetPatch)
                    PatchFlowState.Failed -> PatchFailedContent(state, onPatch, onOpenLogs)
                    PatchFlowState.Idle -> PatchReadyContent(
                        state = state,
                        onSelect = { launcher.launch("*/*") },
                        onPatch = onPatch,
                        onViewDetails = { showTechnicalDetails = true }
                    )
                }
            }
        }

        if (state.nativeStatus.level == NativeStatusLevel.Unavailable) {
            item {
                SectionCard {
                    Text("Native unavailable", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                    Text(
                        "Patching is disabled until the native layer loads. Full details are in Settings > Diagnostics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchReadyContent(
    state: HomeUiState,
    onSelect: () -> Unit,
    onPatch: () -> Unit,
    onViewDetails: () -> Unit
) {
    val boot = state.bootImage
    if (boot.filePath == null) {
        Text("Select a boot image to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActionButton("Select boot.img", onClick = onSelect, icon = Icons.Rounded.FolderOpen, modifier = Modifier.fillMaxWidth())
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(boot.fileName ?: "boot.img", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                boot.statusLabel?.let { StatusPill(it, boot.status.stateColor()) }
                boot.formatChip?.let { StatusPill(it, InactiveGrey) }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton("Patch", onClick = onPatch, enabled = state.canPatch, icon = Icons.Rounded.PublishedWithChanges, modifier = Modifier.weight(1f))
        ActionButton("Change", onClick = onSelect, isPrimary = false, modifier = Modifier.weight(1f))
    }

    state.patchDisabledReason?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (boot.technicalDetails != null) {
        TextButton(onClick = onViewDetails) { Text("View technical details") }
    }
}

@Composable
private fun PatchingContent() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
        Column {
            Text("Patching boot image...", style = MaterialTheme.typography.titleMedium)
            Text("Keep the app open until the output image is created.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PatchSuccessContent(state: HomeUiState, context: Context, onResetPatch: () -> Unit) {
    val patch = state.patch
    Text("Patch complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
    CompactInfoRow("Output", patch.outputFileName ?: "patched boot image")
    patch.sha256?.let { sha ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SHA-256", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sha.shortSha(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { context.copyText("RustDroid SHA-256", sha) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy SHA-256")
            }
        }
    }
    DangerNote(patch.warning)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton("Open folder", onClick = { context.openOutput(patch.outputPath) }, isPrimary = false, modifier = Modifier.weight(1f))
        ActionButton("Share", onClick = { context.shareOutput(patch.outputPath) }, isPrimary = false, icon = Icons.Rounded.IosShare, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton("Copy path", onClick = { context.copyText("RustDroid output path", patch.outputPath.orEmpty()) }, isPrimary = false, icon = Icons.Rounded.ContentCopy, modifier = Modifier.weight(1f))
        ActionButton("Patch another", onClick = onResetPatch, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PatchFailedContent(state: HomeUiState, onPatch: () -> Unit, onOpenLogs: () -> Unit) {
    Text("Patch failed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ErrorRed)
    Text(
        state.patch.error ?: "The image could not be patched.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton("View logs", onClick = onOpenLogs, isPrimary = false, modifier = Modifier.weight(1f))
        ActionButton("Try again", onClick = onPatch, enabled = state.canPatch, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TechnicalDetailsDialog(details: BootImageTechnicalDetails?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Technical details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactInfoRow("Header version", details?.headerVersion ?: "Unknown")
                CompactInfoRow("Kernel", if (details?.kernelDetected == true) "Detected" else "Unknown")
                CompactInfoRow("Ramdisk", if (details?.ramdiskDetected == true) "Detected" else "Missing")
                CompactInfoRow("AVB/footer", if (details?.avbFooterDetected == true) "Detected" else "Not detected")
                CompactInfoRow("File size", details?.fileSize ?: "Unknown")
                CompactInfoRow("SHA-256", details?.sha256?.shortSha() ?: "Unavailable")
                CompactInfoRow("Parser", details?.nativeParserResult ?: "Unavailable")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun BootImageStatus?.stateColor() = when (this) {
    BootImageStatus.Patchable, BootImageStatus.Valid -> SuccessGreen
    BootImageStatus.AlreadyPatched, BootImageStatus.MissingRamdisk -> WarningYellow
    BootImageStatus.Unsupported, BootImageStatus.UnknownFormat -> ErrorRed
    null -> InactiveGrey
}

private fun String.shortSha(): String = if (length > 16) "${take(12)}...${takeLast(6)}" else this

private fun Context.copyText(label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun Context.shareOutput(path: String?) {
    val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Share patched image"))
}

private fun Context.openOutput(path: String?) {
    val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(intent) }.onFailure { copyText("RustDroid output path", file.parentFile?.absolutePath.orEmpty()) }
}
