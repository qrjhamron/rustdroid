package com.rustdroid.manager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rustdroid.manager.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel : ViewModel() {

    // ── Home ───────────────────────────────────────────────────────
    var homeState by mutableStateOf(HomeUiState())
        private set

    fun refreshHome() {
        homeState = homeState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val native = NativeBridge.isNativeLoaded()

                val rootJson = safeJson(NativeBridge.getRootStatus())
                val runtimeJson = safeJson(NativeBridge.getRuntimeStatus())
                val securityJson = safeJson(NativeBridge.getSecurityStatus())
                val compatJson = safeJson(NativeBridge.getDeviceCompatibilitySummary("{}"))
                val runtimeCompatJson = safeJson(NativeBridge.getRuntimeCompatibility("{}"))
                val readinessJson = safeJson(NativeBridge.getReleaseReadiness("{}"))

                val security = securityJson.optJSONObject("security")
                val daemonReachable = runtimeJson.optBoolean("daemon_responding", false)
                val compatSummary = compatJson.optJSONObject("summary")
                val readinessReport = readinessJson.optJSONObject("report")

                val deviceLevel = compatSummary?.optString("compatibility_level", "")
                    ?.let { StatusLevel.fromString(it) } ?: StatusLevel.Unknown
                val runtimeLevel = if (runtimeCompatJson.optString("status") == "unavailable")
                    StatusLevel.Unavailable
                else if (runtimeCompatJson.has("report"))
                    StatusLevel.Ready
                else StatusLevel.Unknown

                homeState = HomeUiState(
                    isLoading = false,
                    nativeLoaded = native,
                    rustdroidStatus = if (native) "Active" else "Unavailable",
                    nativeBridgeStatus = if (native) "Loaded" else "Not loaded",
                    rootStatus = if (rootJson.optString("status") == "unavailable") "Unavailable"
                        else if (rootJson.optBoolean("is_patched", false)) "Patched" else "Not patched",
                    daemonStatus = if (daemonReachable) "Connected" else "Offline",
                    deviceCompatibility = deviceLevel,
                    runtimeCompatibility = runtimeLevel,
                    releaseReadiness = readinessReport?.optString("readiness_level", "Unknown") ?: "Unknown",
                    errorMessage = null
                )
            } catch (e: Exception) {
                homeState = homeState.copy(
                    isLoading = false,
                    errorMessage = "Failed to load status: ${e.message}"
                )
            }
        }
    }

    // ── Superuser ──────────────────────────────────────────────────
    var superuserState by mutableStateOf(SuperuserUiState())
        private set

    fun refreshSuperuser() {
        superuserState = superuserState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = safeJson(NativeBridge.listPolicies())
                if (json.optString("status") == "unavailable" || json.optString("status") == "error") {
                    superuserState = SuperuserUiState(
                        isLoading = false,
                        errorMessage = json.optString("error", "Unavailable")
                    )
                    return@launch
                }
                val arr = json.optJSONArray("policies") ?: JSONArray()
                val entries = mutableListOf<SuperuserEntry>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    entries.add(
                        SuperuserEntry(
                            uid = p.optInt("uid", -1),
                            packageName = p.optString("package_name", "unknown"),
                            state = p.optString("state", "Unknown"),
                            ruleType = p.optString("rule_type", "Unknown")
                        )
                    )
                }
                superuserState = SuperuserUiState(isLoading = false, entries = entries)
            } catch (e: Exception) {
                superuserState = SuperuserUiState(
                    isLoading = false,
                    errorMessage = "Failed to load policies: ${e.message}"
                )
            }
        }
    }

    fun revokePolicy(uid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NativeBridge.removePolicy(JSONObject().apply { put("uid", uid) }.toString())
            } catch (_: Exception) {}
            refreshSuperuser()
        }
    }

    // ── Modules ────────────────────────────────────────────────────
    var modulesState by mutableStateOf(ModulesUiState())
        private set

    fun refreshModules() {
        modulesState = modulesState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = safeJson(NativeBridge.listModules())
                if (json.optString("status") == "unavailable" || json.optString("status") == "error") {
                    modulesState = ModulesUiState(
                        isLoading = false,
                        errorMessage = json.optString("error", "Unavailable")
                    )
                    return@launch
                }
                val arr = json.optJSONArray("modules") ?: JSONArray()
                val modules = mutableListOf<ModuleEntry>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    modules.add(
                        ModuleEntry(
                            id = m.optString("id", ""),
                            name = m.optString("name", "Unknown"),
                            version = m.optString("version", "?"),
                            author = m.optString("author", "?"),
                            description = m.optString("description", ""),
                            enabled = m.optBoolean("enabled", false)
                        )
                    )
                }
                modulesState = ModulesUiState(isLoading = false, modules = modules)
            } catch (e: Exception) {
                modulesState = ModulesUiState(
                    isLoading = false,
                    errorMessage = "Failed to load modules: ${e.message}"
                )
            }
        }
    }

    fun toggleModule(moduleId: String, enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("module_id", moduleId)
                    put("force", false)
                }.toString()
                if (enable) NativeBridge.enableModule(payload)
                else NativeBridge.disableModule(payload)
            } catch (_: Exception) {}
            refreshModules()
        }
    }

    fun removeModule(moduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply { put("module_id", moduleId) }.toString()
                val result = safeJson(NativeBridge.removeModule(payload))
                val report = result.optJSONObject("report")
                if (report?.optBoolean("success", false) == true) {
                    modulesState = modulesState.copy(statusMessage = "Module removed.")
                } else {
                    modulesState = modulesState.copy(
                        statusMessage = "Removal failed: ${report?.optString("error", "unknown")}"
                    )
                }
            } catch (e: Exception) {
                modulesState = modulesState.copy(statusMessage = "Error: ${e.message}")
            }
            refreshModules()
        }
    }

    fun installModuleFromPath(zipPath: String) {
        modulesState = modulesState.copy(statusMessage = "Installing…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val validatePayload = JSONObject().apply { put("zip_path", zipPath) }.toString()
                val valResult = safeJson(NativeBridge.validateModuleZip(validatePayload))
                if (valResult.optString("status") != "success") {
                    modulesState = modulesState.copy(statusMessage = "Validation failed: ${valResult.optString("error")}")
                    return@launch
                }
                val report = valResult.optJSONObject("report")
                if (report?.optBoolean("is_valid", false) != true) {
                    modulesState = modulesState.copy(statusMessage = "Invalid module: ${report?.optString("error")}")
                    return@launch
                }
                val installPayload = JSONObject().apply { put("zip_path", zipPath) }.toString()
                val installResult = safeJson(NativeBridge.installModule(installPayload))
                val installReport = installResult.optJSONObject("report")
                if (installReport?.optBoolean("success", false) == true) {
                    modulesState = modulesState.copy(statusMessage = "Module installed successfully.")
                } else {
                    modulesState = modulesState.copy(
                        statusMessage = "Install failed: ${installReport?.optString("error", "unknown")}"
                    )
                }
            } catch (e: Exception) {
                modulesState = modulesState.copy(statusMessage = "Error: ${e.message}")
            }
            refreshModules()
        }
    }

    fun clearModulesStatusMessage() {
        modulesState = modulesState.copy(statusMessage = null)
    }

    // ── Log ────────────────────────────────────────────────────────
    var logState by mutableStateOf(LogUiState())
        private set

    fun loadLog(logName: String = logState.selectedLog) {
        logState = logState.copy(isLoading = true, selectedLog = logName, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("log_name", logName)
                    put("tail_lines", 50)
                }.toString()
                val json = safeJson(NativeBridge.getAuditLogTail(payload))
                if (json.optString("status") == "unavailable" || json.optString("status") == "error") {
                    logState = logState.copy(
                        isLoading = false,
                        logLines = emptyList(),
                        errorMessage = json.optString("error", "Logs unavailable")
                    )
                    return@launch
                }
                val raw = json.optString("lines", "")
                val lines = if (raw.isBlank()) emptyList() else raw.split("\n")
                logState = logState.copy(isLoading = false, logLines = lines)
            } catch (e: Exception) {
                logState = logState.copy(
                    isLoading = false,
                    errorMessage = "Failed to load log: ${e.message}"
                )
            }
        }
    }

    // ── Settings ───────────────────────────────────────────────────
    var settingsState by mutableStateOf(SettingsUiState())
        private set

    fun refreshSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val native = NativeBridge.isNativeLoaded()
                val secJson = safeJson(NativeBridge.getSecurityStatus())
                val security = secJson.optJSONObject("security")
                settingsState = SettingsUiState(
                    nativeLoaded = native,
                    rustdroidVersion = security?.optString("rustdroid_version", "Unknown") ?: "Unknown"
                )
            } catch (_: Exception) {
                settingsState = SettingsUiState(nativeLoaded = NativeBridge.isNativeLoaded())
            }
        }
    }

    fun refreshNativeStatus() {
        settingsState = settingsState.copy(statusMessage = "Refreshing…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NativeBridge.validateNativeBridgeState("{}")
                settingsState = settingsState.copy(statusMessage = "Native status refreshed.")
            } catch (e: Exception) {
                settingsState = settingsState.copy(statusMessage = "Error: ${e.message}")
            }
        }
    }

    fun exportReportBundle() {
        settingsState = settingsState.copy(statusMessage = "Exporting…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = safeJson(NativeBridge.exportReportBundle("{}"))
                if (result.optString("status") == "success") {
                    settingsState = settingsState.copy(statusMessage = "Report exported.")
                } else {
                    settingsState = settingsState.copy(
                        statusMessage = result.optString("error", "Export failed.")
                    )
                }
            } catch (e: Exception) {
                settingsState = settingsState.copy(statusMessage = "Error: ${e.message}")
            }
        }
    }

    fun clearSettingsMessage() {
        settingsState = settingsState.copy(statusMessage = null)
    }

    // ── Helpers ────────────────────────────────────────────────────
    private fun safeJson(raw: String): JSONObject {
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject().apply {
                put("status", "error")
                put("error", "Invalid response")
            }
        }
    }
}
