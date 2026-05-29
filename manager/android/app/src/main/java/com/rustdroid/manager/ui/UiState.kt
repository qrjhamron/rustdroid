package com.rustdroid.manager.ui

import com.rustdroid.manager.BootImageAnalysisResult
import com.rustdroid.manager.BootPatchResult
import com.rustdroid.manager.NativeLibraryStatus
import com.rustdroid.manager.NativeLogEntry
import com.rustdroid.manager.data.AppSettings
import com.rustdroid.manager.data.LanguageMode
import com.rustdroid.manager.data.ThemeMode
import com.rustdroid.manager.data.UpdateChannel

enum class DeviceCompatibilityLevel {
    Ready,
    Warning,
    Unknown,
    Blocked
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val isPatching: Boolean = false,
    val nativeStatus: NativeLibraryStatus = NativeLibraryStatus(
        loaded = false,
        libraryName = "rustdroid_native",
        abi = "unknown",
        version = "Unavailable",
        error = "Native library not loaded"
    ),
    val selectedImagePath: String? = null,
    val analysis: BootImageAnalysisResult? = null,
    val lastPatchResult: BootPatchResult? = null,
    val deviceCompatibilityLevel: DeviceCompatibilityLevel = DeviceCompatibilityLevel.Unknown,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class SuperuserAppEntry(
    val appName: String,
    val packageName: String,
    val state: String,
    val lastUsed: String
)

data class SuperuserUiState(
    val isLoading: Boolean = false,
    val entries: List<SuperuserAppEntry> = emptyList(),
    val unavailableReason: String? = "Unavailable"
)

data class ModuleUiEntry(
    val name: String,
    val version: String,
    val enabled: Boolean
)

data class ModulesUiState(
    val isLoading: Boolean = false,
    val modules: List<ModuleUiEntry> = emptyList(),
    val unavailableReason: String? = "Unavailable"
)

data class LogUiState(
    val isLoading: Boolean = false,
    val selectedCategory: String = "native",
    val entries: List<NativeLogEntry> = emptyList(),
    val message: String? = null,
    val errorMessage: String? = null
)

data class SettingsUiState(
    val appSettings: AppSettings = AppSettings(),
    val nativeStatus: NativeLibraryStatus = NativeLibraryStatus(
        loaded = false,
        libraryName = "rustdroid_native",
        abi = "unknown",
        version = "Unavailable",
        error = "Native library not loaded"
    ),
    val appVersion: String = "1.0.0",
    val rustdroidVersion: String = "Unavailable",
    val statusMessage: String? = null
)

val LogCategories = listOf("su", "daemon", "first_boot", "self_check", "module", "native", "patch")

val ThemeOptions = ThemeMode.entries.toList()
val LanguageOptions = LanguageMode.entries.toList()
val ChannelOptions = UpdateChannel.entries.toList()
