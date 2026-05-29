package com.rustdroid.manager.ui

/**
 * Simple status levels for user-facing summaries.
 */
enum class StatusLevel {
    Ready,
    Warning,
    Blocked,
    Unsupported,
    Unavailable,
    Unknown;

    companion object {
        fun fromString(s: String): StatusLevel = when (s.lowercase()) {
            "ready", "success", "clean", "safe", "supported",
            "supportedforofflinepatch" -> Ready
            "warning", "warnings" -> Warning
            "blocked", "failed", "rejected" -> Blocked
            "unsupported", "not_supported", "notsupported" -> Unsupported
            "unavailable", "error" -> Unavailable
            else -> Unknown
        }
    }
}

/** State for the Home screen. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val nativeLoaded: Boolean = false,
    val rustdroidStatus: String = "Unknown",
    val nativeBridgeStatus: String = "Unavailable",
    val rootStatus: String = "Unknown",
    val daemonStatus: String = "Offline",
    val deviceCompatibility: StatusLevel = StatusLevel.Unknown,
    val runtimeCompatibility: StatusLevel = StatusLevel.Unknown,
    val releaseReadiness: String = "Unknown",
    val errorMessage: String? = null
)

/** A single superuser policy entry. */
data class SuperuserEntry(
    val uid: Int,
    val packageName: String,
    val state: String,
    val ruleType: String
)

/** State for the Superuser screen. */
data class SuperuserUiState(
    val isLoading: Boolean = true,
    val entries: List<SuperuserEntry> = emptyList(),
    val errorMessage: String? = null
)

/** A single installed module entry. */
data class ModuleEntry(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean
)

/** State for the Modules screen. */
data class ModulesUiState(
    val isLoading: Boolean = true,
    val modules: List<ModuleEntry> = emptyList(),
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

/** A single log line. */
data class LogLine(
    val timestamp: String,
    val message: String
)

/** State for the Log screen. */
data class LogUiState(
    val isLoading: Boolean = true,
    val selectedLog: String = "su.log",
    val logLines: List<String> = emptyList(),
    val errorMessage: String? = null
)

/** State for the Settings screen. */
data class SettingsUiState(
    val nativeLoaded: Boolean = false,
    val appVersion: String = "1.0.0",
    val rustdroidVersion: String = "Unknown",
    val statusMessage: String? = null
)
