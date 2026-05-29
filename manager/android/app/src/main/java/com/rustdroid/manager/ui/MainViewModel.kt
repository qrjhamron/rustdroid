package com.rustdroid.manager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rustdroid.manager.NativeBridge
import com.rustdroid.manager.data.LanguageMode
import com.rustdroid.manager.data.SettingsRepository
import com.rustdroid.manager.data.ThemeMode
import com.rustdroid.manager.data.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)

    var homeState by mutableStateOf(HomeUiState())
        private set

    var logState by mutableStateOf(LogUiState())
        private set

    var superuserState by mutableStateOf(SuperuserUiState(
        unavailableReason = "No superuser requests yet"
    ))
        private set

    var modulesState by mutableStateOf(ModulesUiState(
        unavailableReason = "No modules installed"
    ))
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
        homeState = homeState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            val nativeStatus = NativeBridge.getNativeLibraryStatus()
            val selectedPath = resolveBootImagePath(settingsState.appSettings.selectedBootImagePath)

            if (selectedPath != settingsState.appSettings.selectedBootImagePath && selectedPath != null) {
                settingsRepository.setSelectedBootImagePath(selectedPath)
            }

            val analysis = selectedPath?.let { NativeBridge.analyzeBootImage(it) }
            val level = when {
                !nativeStatus.loaded -> DeviceCompatibilityLevel.Warning
                analysis == null -> DeviceCompatibilityLevel.Unknown
                analysis.success -> DeviceCompatibilityLevel.Ready
                else -> DeviceCompatibilityLevel.Blocked
            }

            homeState = homeState.copy(
                isLoading = false,
                nativeStatus = nativeStatus,
                selectedImagePath = selectedPath,
                analysis = analysis,
                deviceCompatibilityLevel = level,
                errorMessage = analysis?.error
            )
        }
    }

    fun patchBootImage() {
        if (homeState.isPatching) return

        val selectedPath = homeState.selectedImagePath
        if (selectedPath.isNullOrBlank()) {
            homeState = homeState.copy(statusMessage = "Select boot.img first")
            return
        }

        if (!homeState.nativeStatus.loaded) {
            homeState = homeState.copy(statusMessage = "Native library not loaded")
            return
        }

        homeState = homeState.copy(isPatching = true, statusMessage = null, errorMessage = null)

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = appContext.getExternalFilesDir("patched")?.absolutePath
                ?: File(appContext.filesDir, "patched").apply { mkdirs() }.absolutePath

            val result = NativeBridge.patchBootImage(selectedPath, outputDir)
            val analysis = NativeBridge.analyzeBootImage(selectedPath)

            homeState = homeState.copy(
                isPatching = false,
                analysis = analysis,
                lastPatchResult = result,
                statusMessage = if (result.success) {
                    "Patch created: ${result.outputFileName ?: "patched image"}"
                } else {
                    null
                },
                errorMessage = if (result.success) null else (result.error ?: "Patch failed")
            )

            refreshLogs("patch")
        }
    }

    fun setSelectedBootImage(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setSelectedBootImagePath(path)
            refreshHome()
        }
    }

    fun clearHomeMessage() {
        homeState = homeState.copy(statusMessage = null, errorMessage = null)
    }

    fun refreshLogs(category: String = logState.selectedCategory) {
        logState = logState.copy(isLoading = true, selectedCategory = category, errorMessage = null, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            val nativeStatus = NativeBridge.getNativeLibraryStatus()
            if (!nativeStatus.loaded) {
                logState = logState.copy(
                    isLoading = false,
                    entries = emptyList(),
                    errorMessage = nativeStatus.error ?: "Native library not loaded"
                )
                return@launch
            }

            val result = NativeBridge.getNativeLogs(category)
            val entries = result.entries
            val message = when {
                result.error != null -> null
                entries.isEmpty() -> "No logs yet"
                else -> null
            }
            logState = logState.copy(
                isLoading = false,
                entries = entries,
                message = message,
                errorMessage = result.error ?: if (entries.isEmpty() && category !in listOf("native", "patch")) {
                    "Unavailable: $category backend is not connected"
                } else {
                    null
                }
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
            settingsState = settingsState.copy(
                nativeStatus = nativeStatus,
                rustdroidVersion = nativeStatus.version
            )
        }
    }

    fun reloadNativeStatus() {
        refreshSettings()
        settingsState = settingsState.copy(statusMessage = "Native status reloaded")
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
        settingsState = settingsState.copy(statusMessage = message.trim())
    }

    fun clearSettingsMessage() {
        settingsState = settingsState.copy(statusMessage = null)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setLanguageMode(mode)
        }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setUpdateChannel(channel)
        }
    }

    fun setCustomChannel(channel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setCustomChannel(channel)
        }
    }

    private fun resolveBootImagePath(savedPath: String): String? {
        val candidates = buildList {
            if (savedPath.isNotBlank()) add(savedPath)
            add("/root/rustdroid/boot.img")
            add("/sdcard/Download/boot.img")
            add("/storage/emulated/0/Download/boot.img")
        }

        return candidates.firstOrNull { path ->
            runCatching {
                File(path).exists() && File(path).isFile
            }.getOrDefault(false)
        }
    }
}
