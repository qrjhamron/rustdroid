package com.rustdroid.manager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rustdroid.manager.BootImageAnalysisResult
import com.rustdroid.manager.NativeBridge
import com.rustdroid.manager.NativeLibraryStatus
import com.rustdroid.manager.data.LanguageMode
import com.rustdroid.manager.data.SettingsRepository
import com.rustdroid.manager.data.ThemeMode
import com.rustdroid.manager.data.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)

    var homeState by mutableStateOf(HomeUiState())
        private set

    var logState by mutableStateOf(LogUiState())
        private set

    var superuserState by mutableStateOf(SuperuserUiState())
        private set

    var modulesState by mutableStateOf(ModulesUiState())
        private set

    var settingsState by mutableStateOf(SettingsUiState())
        private set

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                settingsState = settingsState.copy(appSettings = settings)
            }
        }
        refreshHome()
        refreshLogs(logState.selectedCategory)
        refreshSettings()
    }

    fun refreshHome() {
        homeState = homeState.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val nativeStatus = NativeBridge.getNativeLibraryStatus()
            val nativeUi = nativeStatus.toUiState()
            val selectedPath = resolveBootImagePath(settingsState.appSettings.selectedBootImagePath)

            if (selectedPath != null && selectedPath != settingsState.appSettings.selectedBootImagePath) {
                settingsRepository.setSelectedBootImagePath(selectedPath)
            }

            val analysis = selectedPath?.let { NativeBridge.analyzeBootImage(it) }
            val bootImage = selectedPath.toBootImageUiState(analysis)

            homeState = homeState.copy(
                isLoading = false,
                nativeStatus = nativeUi,
                bootImage = bootImage
            )
        }
    }

    fun patchBootImage() {
        if (homeState.patch.flowState == PatchFlowState.Patching || !homeState.canPatch) return

        val selectedPath = homeState.bootImage.filePath ?: return
        NativeBridge.clearNativeLogs("patch")
        homeState = homeState.copy(
            patch = PatchUiState(
                flowState = PatchFlowState.Patching,
                currentStep = "Starting patch",
                logLines = listOf("starting patch session")
            )
        )

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = appContext.getExternalFilesDir("patched")?.apply { mkdirs() }?.absolutePath
                ?: File(appContext.filesDir, "patched").apply { mkdirs() }.absolutePath

            val poller = launch {
                while (homeState.patch.flowState == PatchFlowState.Patching) {
                    updatePatchLogSnapshot()
                    delay(250)
                }
            }

            val result = NativeBridge.patchBootImage(selectedPath, outputDir)
            poller.cancel()
            val finalLines = currentPatchLogLines().ifEmpty { homeState.patch.logLines }
            homeState = homeState.copy(
                patch = if (result.success) {
                    PatchUiState(
                        flowState = PatchFlowState.Success,
                        currentStep = "Patch complete",
                        logLines = finalLines,
                        outputFileName = result.outputFileName ?: result.outputPath?.substringAfterLast('/'),
                        outputPath = result.outputPath,
                        sha256 = result.outputSha256,
                        warning = result.manualFlashWarning.ifBlank {
                            "Flashing is manual. Verify the image before use."
                        }
                    )
                } else {
                    PatchUiState(
                        flowState = PatchFlowState.Failed,
                        currentStep = "Patch failed",
                        logLines = finalLines,
                        error = result.error?.toUserPatchError() ?: "Patch failed. Check the selected image and try again."
                    )
                }
            )
            refreshLogs("patch")
        }
    }

    fun resetPatchState() {
        homeState = homeState.copy(patch = PatchUiState())
    }

    fun preparePatchFlow() {
        if (homeState.patch.flowState != PatchFlowState.Patching) {
            homeState = homeState.copy(patch = PatchUiState())
        }
        refreshHome()
    }

    fun setSelectedBootImage(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setSelectedBootImagePath(path)
            homeState = homeState.copy(patch = PatchUiState())
            refreshHome()
        }
    }

    fun refreshLogs(category: String = logState.selectedCategory) {
        logState = logState.copy(isLoading = true, selectedCategory = category, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            val nativeStatus = NativeBridge.getNativeLibraryStatus()
            if (!nativeStatus.loaded) {
                logState = logState.copy(
                    isLoading = false,
                    entries = emptyList(),
                    isEmpty = true,
                    errorMessage = if (category == "native") nativeStatus.error ?: "Native library unavailable" else null
                )
                return@launch
            }

            val result = NativeBridge.getNativeLogs(category)
            val entries = result.entries
            logState = logState.copy(
                isLoading = false,
                entries = entries,
                isEmpty = entries.isEmpty(),
                errorMessage = result.error
            )
        }
    }

    fun clearLogCategory(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NativeBridge.clearNativeLogs(category)
            refreshLogs(logState.selectedCategory)
        }
    }

    fun refreshSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val nativeStatus = NativeBridge.getNativeLibraryStatus()
            val nativeUi = nativeStatus.toUiState()
            settingsState = settingsState.copy(
                nativeStatus = nativeUi,
                rustdroidVersion = nativeUi.version
            )
        }
    }

    fun reloadNativeStatus() {
        refreshSettings()
        refreshHome()
        refreshLogs("native")
    }

    fun exportNativeDiagnostics() {
        val status = NativeBridge.getNativeLibraryStatus()
        val message = buildString {
            appendLine("Native diagnostics")
            appendLine("Library: ${status.libraryName}")
            appendLine("Loaded: ${status.loaded}")
            appendLine("ABI: ${status.abi}")
            appendLine("Version: ${status.version}")
            appendLine("Error: ${status.error ?: "none"}")
        }
        settingsState = settingsState.copy(diagnosticsMessage = message.trim())
    }

    fun clearDiagnosticsMessage() {
        settingsState = settingsState.copy(diagnosticsMessage = null)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setThemeMode(mode) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setLanguageMode(mode) }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setUpdateChannel(channel) }
    }

    fun setCustomChannel(channel: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setCustomChannel(channel) }
    }


    private fun updatePatchLogSnapshot() {
        val lines = currentPatchLogLines()
        if (lines.isNotEmpty()) {
            homeState = homeState.copy(
                patch = homeState.patch.copy(
                    logLines = lines,
                    currentStep = lines.last().substringAfter("] ").take(48)
                )
            )
        }
    }

    private fun currentPatchLogLines(): List<String> {
        val result = NativeBridge.getNativeLogs("patch")
        return result.entries.map { entry ->
            val time = entry.timestamp.substringAfter('T', entry.timestamp).take(8).ifBlank { "--:--:--" }
            "[$time] ${entry.message}"
        }
    }

    private fun NativeLibraryStatus.toUiState(): NativeStatusUiState {
        val ready = loaded
        return NativeStatusUiState(
            level = if (ready) NativeStatusLevel.Ready else NativeStatusLevel.Unavailable,
            label = if (ready) "Native ready" else "Native unavailable",
            libraryName = libraryName,
            abi = abi,
            version = version,
            error = error
        )
    }

    private fun String?.toBootImageUiState(analysis: BootImageAnalysisResult?): BootImageUiState {
        val path = this ?: return BootImageUiState()
        val file = File(path)
        val status = analysis?.toBootStatus()
        return BootImageUiState(
            fileName = file.name,
            filePath = path,
            status = status,
            statusLabel = status?.label(),
            formatChip = analysis?.format?.takeIf { it.isMeaningfulFormat() },
            technicalDetails = BootImageTechnicalDetails(
                headerVersion = analysis?.headerVersion?.takeIf { it > 0 }?.toString() ?: "Unknown",
                kernelDetected = analysis?.kernelDetected == true,
                ramdiskDetected = analysis?.ramdiskDetected == true,
                avbFooterDetected = analysis?.avbFooterDetected == true,
                fileSize = file.length().formatFileSize(),
                sha256 = runCatching { file.sha256() }.getOrDefault("Unavailable"),
                nativeParserResult = analysis?.error ?: analysis?.patchStatus ?: "Unavailable"
            )
        )
    }

    private fun BootImageAnalysisResult.toBootStatus(): BootImageStatus = when {
        !success && error?.contains("unsupported", ignoreCase = true) == true -> BootImageStatus.Unsupported
        !success -> BootImageStatus.UnknownFormat
        patchStatus.contains("patched", ignoreCase = true) -> BootImageStatus.AlreadyPatched
        !ramdiskDetected -> BootImageStatus.MissingRamdisk
        !format.isMeaningfulFormat() -> BootImageStatus.UnknownFormat
        ramdiskDetected -> BootImageStatus.Patchable
        else -> BootImageStatus.Valid
    }

    private fun BootImageStatus.label(): String = when (this) {
        BootImageStatus.Valid -> "Valid boot image"
        BootImageStatus.Unsupported -> "Unsupported image"
        BootImageStatus.AlreadyPatched -> "Already patched"
        BootImageStatus.UnknownFormat -> "Unknown format"
        BootImageStatus.MissingRamdisk -> "Missing ramdisk"
        BootImageStatus.Patchable -> "Patchable"
    }

    private fun String.isMeaningfulFormat(): Boolean = isNotBlank() && !equals("unknown", ignoreCase = true)

    private fun String.toUserPatchError(): String = when {
        contains("native", ignoreCase = true) && contains("load", ignoreCase = true) -> "Native layer is unavailable. Open Settings > Diagnostics for details."
        contains("already", ignoreCase = true) && contains("patch", ignoreCase = true) -> "This boot image already appears patched."
        contains("ramdisk", ignoreCase = true) -> "This image cannot be patched because no ramdisk was found."
        else -> take(160)
    }

    private fun Long.formatFileSize(): String {
        if (this <= 0L) return "Unknown"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun resolveBootImagePath(savedPath: String): String? {
        val candidates = buildList {
            if (savedPath.isNotBlank()) add(savedPath)
            add("/sdcard/Download/boot.img")
            add("/storage/emulated/0/Download/boot.img")
        }
        return candidates.firstOrNull { path ->
            runCatching { File(path).exists() && File(path).isFile }.getOrDefault(false)
        }
    }
}
