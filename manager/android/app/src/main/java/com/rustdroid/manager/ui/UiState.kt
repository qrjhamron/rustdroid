package com.rustdroid.manager.ui

import com.rustdroid.manager.NativeLogEntry
import com.rustdroid.manager.data.AppSettings

enum class NativeStatusLevel { Ready, Unavailable }

enum class BootImageStatus {
    Valid,
    Unsupported,
    AlreadyPatched,
    UnknownFormat,
    MissingRamdisk,
    Patchable
}

enum class PatchFlowState {
    Idle,
    Patching,
    Success,
    Failed
}

data class NativeStatusUiState(
    val level: NativeStatusLevel = NativeStatusLevel.Unavailable,
    val label: String = "Native unavailable",
    val libraryName: String = "rustdroid_native",
    val abi: String = "unknown",
    val version: String = "Unavailable",
    val error: String? = null
)

data class BootImageTechnicalDetails(
    val headerVersion: String = "Unknown",
    val kernelDetected: Boolean = false,
    val ramdiskDetected: Boolean = false,
    val avbFooterDetected: Boolean = false,
    val fileSize: String = "Unknown",
    val sha256: String = "Unavailable",
    val nativeParserResult: String = "Unavailable"
)

data class BootImageUiState(
    val fileName: String? = null,
    val filePath: String? = null,
    val status: BootImageStatus? = null,
    val statusLabel: String? = null,
    val formatChip: String? = null,
    val technicalDetails: BootImageTechnicalDetails? = null
)

data class PatchUiState(
    val flowState: PatchFlowState = PatchFlowState.Idle,
    val currentStep: String = "Ready",
    val logLines: List<String> = emptyList(),
    val outputFileName: String? = null,
    val outputPath: String? = null,
    val sha256: String? = null,
    val error: String? = null,
    val warning: String = "Flashing is manual. Verify the image before use."
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val nativeStatus: NativeStatusUiState = NativeStatusUiState(),
    val bootImage: BootImageUiState = BootImageUiState(),
    val patch: PatchUiState = PatchUiState()
) {
    val canPatch: Boolean
        get() = bootImage.filePath != null &&
                patch.flowState != PatchFlowState.Patching

    val patchEngineLabel: String
        get() = if (canPatch) "Ready" else "Blocked"

    val patchDisabledReason: String?
        get() = when {
            bootImage.filePath == null -> "Select a boot image first."
            patch.flowState == PatchFlowState.Patching -> "Patching is already in progress."
            else -> null
        }
}

data class SuperuserAppEntry(
    val appName: String,
    val packageName: String,
    val state: String,
    val lastUsed: String
)

data class SuperuserUiState(
    val isLoading: Boolean = false,
    val entries: List<SuperuserAppEntry> = emptyList(),
    val isEmpty: Boolean = true,
    val backendError: String? = null
)

data class ModuleUiEntry(
    val name: String,
    val version: String,
    val enabled: Boolean
)

data class ModulesUiState(
    val isLoading: Boolean = false,
    val modules: List<ModuleUiEntry> = emptyList(),
    val isEmpty: Boolean = true,
    val isSupported: Boolean = false,
    val backendError: String? = null
)

data class LogUiState(
    val isLoading: Boolean = false,
    val selectedCategory: String = "patch",
    val entries: List<NativeLogEntry> = emptyList(),
    val isEmpty: Boolean = true,
    val errorMessage: String? = null
)

data class SettingsUiState(
    val appSettings: AppSettings = AppSettings(),
    val nativeStatus: NativeStatusUiState = NativeStatusUiState(),
    val appVersion: String = "1.0.0",
    val rustdroidVersion: String = "Unavailable",
    val diagnosticsMessage: String? = null
)

val LogCategories = listOf(
    "patch" to "Patch",
    "native" to "Native",
    "su" to "Superuser",
    "module" to "Modules",
    "daemon" to "System"
)
