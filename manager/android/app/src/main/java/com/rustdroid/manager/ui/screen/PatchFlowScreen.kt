package com.rustdroid.manager.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
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
import com.rustdroid.manager.ui.components.DangerNotice
import com.rustdroid.manager.ui.components.ErrorRed
import com.rustdroid.manager.ui.components.InactiveGrey
import com.rustdroid.manager.ui.components.PatchStepCard
import com.rustdroid.manager.ui.components.ResultCard
import com.rustdroid.manager.ui.components.SectionCard
import com.rustdroid.manager.ui.components.StatusChip
import com.rustdroid.manager.ui.components.SuccessGreen
import com.rustdroid.manager.ui.components.TerminalLogPanel
import com.rustdroid.manager.ui.components.WarningYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PatchFlowScreen(
    state: HomeUiState,
    isPatchApplied: Boolean,
    onConfirmFlashApplied: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onSelectBootImage: (path: String) -> Unit,
    onStartPatch: () -> Unit,
    onResetPatch: () -> Unit,
    onOpenLogs: () -> Unit
) {
    LaunchedEffect(Unit) { onPrepare() }

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
        TechnicalDetailsDialog(state.bootImage.technicalDetails) { showTechnicalDetails = false }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Patch boot image", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(screenSubtitle(state.patch.flowState), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when (state.patch.flowState) {
            PatchFlowState.Idle -> item {
                PatchSetupContent(
                    state = state,
                    onChoose = { launcher.launch("*/*") },
                    onStartPatch = onStartPatch,
                    onShowTechnicalDetails = { showTechnicalDetails = true }
                )
            }

            PatchFlowState.Patching -> item { PatchProgressContent(state) }
            PatchFlowState.Success -> item {
                PatchSuccessContent(
                    state = state,
                    isPatchApplied = isPatchApplied,
                    onConfirmFlashApplied = onConfirmFlashApplied,
                    context = context,
                    onResetPatch = onResetPatch,
                    onOpenLogs = onOpenLogs
                )
            }
            PatchFlowState.Failed -> item { PatchFailedContent(state, onStartPatch, onOpenLogs, onBack) }
        }
    }
}

@Composable
private fun PatchSetupContent(
    state: HomeUiState,
    onChoose: () -> Unit,
    onStartPatch: () -> Unit,
    onShowTechnicalDetails: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PatchStepCard(
            title = "Original boot image",
            subtitle = "Original boot image recommended",
            stateLabel = state.bootImage.statusLabel ?: if (state.bootImage.fileName == null) "Not selected" else "Analyzing",
            stateColor = state.bootImage.status.statusColor()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.bootImage.fileName ?: "No boot image selected", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("The original image is never overwritten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = if (state.bootImage.fileName == null) "Choose file" else "Change file",
                    onClick = onChoose,
                    isPrimary = false,
                    icon = Icons.Rounded.FolderOpen,
                    modifier = Modifier.weight(1f)
                )
                if (state.bootImage.technicalDetails != null) {
                    ActionButton("Details", onClick = onShowTechnicalDetails, isPrimary = false, modifier = Modifier.weight(1f))
                }
            }
        }

        state.patchDisabledReason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ActionButton(
            text = "Patch now",
            onClick = onStartPatch,
            enabled = state.canPatch,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PatchProgressContent(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PatchStepCard(
            title = "Patching",
            subtitle = state.bootImage.fileName ?: "boot.img",
            stateLabel = "Running",
            stateColor = WarningYellow
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                Column {
                    Text(state.patch.currentStep, style = MaterialTheme.typography.titleMedium)
                    Text("Native patch output is shown below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = InactiveGrey)
                Text("Patch terminal", style = MaterialTheme.typography.titleMedium)
            }
            TerminalLogPanel(lines = state.patch.logLines)
        }
    }
}

@Composable
private fun PatchSuccessContent(
    state: HomeUiState,
    isPatchApplied: Boolean,
    onConfirmFlashApplied: (Boolean) -> Unit,
    context: Context,
    onResetPatch: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val patch = state.patch
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        File(patch.outputPath.orEmpty()).inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResultCard(title = "Patch complete", color = SuccessGreen) {
            CompactInfoRow("Output file", patch.outputFileName ?: "patched boot image")
            CompactInfoRow("SHA-256", patch.sha256 ?: "Unavailable")
            DangerNotice("Flashing is manual. Verify the image before use.")

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    text = "Download patched image",
                    onClick = {
                        downloadLauncher.launch(patch.outputFileName ?: "boot_patched.img")
                    },
                    icon = Icons.Rounded.FolderOpen,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "Share",
                    onClick = { context.shareOutput(patch.outputPath) },
                    isPrimary = false,
                    icon = Icons.Rounded.IosShare,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ActionButton("View log", onClick = onOpenLogs, isPrimary = false, modifier = Modifier.fillMaxWidth())
        }

        SectionCard {
            Text("Confirm Flash Status", style = MaterialTheme.typography.titleMedium)
            Text(
                "Have you successfully flashed the patched boot image to your device?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (isPatchApplied) {
                StatusChip(text = "Flash confirmed & applied", color = SuccessGreen)
            } else {
                ActionButton(
                    text = "I have flashed this image",
                    onClick = { onConfirmFlashApplied(true) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionCard {
            Text("Terminal output", style = MaterialTheme.typography.titleMedium)
            TerminalLogPanel(lines = patch.logLines)
            ActionButton("Patch another", onClick = onResetPatch, isPrimary = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PatchFailedContent(
    state: HomeUiState,
    onStartPatch: () -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResultCard(title = "Patch failed", color = ErrorRed) {
            Text(
                state.patch.error ?: "The image could not be patched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("View log", onClick = onOpenLogs, isPrimary = false, modifier = Modifier.weight(1f))
                ActionButton("Try again", onClick = onStartPatch, enabled = state.canPatch, modifier = Modifier.weight(1f))
            }
            ActionButton("Back", onClick = onBack, isPrimary = false, modifier = Modifier.fillMaxWidth())
        }
        SectionCard {
            Text("Terminal output", style = MaterialTheme.typography.titleMedium)
            TerminalLogPanel(lines = state.patch.logLines)
        }
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
                CompactInfoRow("SHA-256", details?.sha256 ?: "Unavailable")
                CompactInfoRow("Parser", details?.nativeParserResult ?: "Unavailable")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun screenSubtitle(state: PatchFlowState): String = when (state) {
    PatchFlowState.Idle -> "Select, analyze, and patch"
    PatchFlowState.Patching -> "Patch terminal"
    PatchFlowState.Success -> "Result"
    PatchFlowState.Failed -> "Result"
}

private fun BootImageStatus?.statusColor() = when (this) {
    BootImageStatus.Patchable, BootImageStatus.Valid -> SuccessGreen
    BootImageStatus.AlreadyPatched, BootImageStatus.MissingRamdisk -> WarningYellow
    BootImageStatus.Unsupported, BootImageStatus.UnknownFormat -> ErrorRed
    null -> InactiveGrey
}

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
