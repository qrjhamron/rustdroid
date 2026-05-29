package com.rustdroid.manager

import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "RustDroid"

data class NativeLibraryStatus(
    val loaded: Boolean,
    val libraryName: String,
    val abi: String,
    val version: String,
    val error: String?
)

data class BootImageAnalysisResult(
    val success: Boolean,
    val path: String,
    val format: String,
    val headerVersion: Int,
    val pageSize: Int,
    val kernelSize: Int,
    val ramdiskSize: Int,
    val kernelDetected: Boolean,
    val ramdiskDetected: Boolean,
    val avbFooterDetected: Boolean,
    val patchStatus: String,
    val error: String?
)

data class BootPatchResult(
    val success: Boolean,
    val outputPath: String?,
    val outputFileName: String?,
    val outputSha256: String?,
    val manualFlashWarning: String,
    val error: String?
)

data class NativeLogEntry(
    val timestamp: String,
    val category: String,
    val level: String,
    val message: String
)

data class NativeLogQueryResult(
    val entries: List<NativeLogEntry>,
    val error: String?
)

object NativeBridge {
    private const val LIB_NAME = "rustdroid_native"

    private var nativeLoaded = false
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary(LIB_NAME)
            nativeLoaded = true
            Log.i(TAG, "Loaded native library: $LIB_NAME")
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
            loadError = e.message ?: "Unknown load error"
            Log.e(TAG, "Failed to load native library: $LIB_NAME", e)
        }
    }

    private external fun nativeGetLibraryStatusJson(): String
    private external fun nativeGetLibraryVersion(): String
    private external fun nativeAnalyzeBootImage(path: String): String
    private external fun nativePatchBootImage(inputPath: String, outputDir: String): String
    private external fun nativeGetLogs(category: String): String
    private external fun nativeClearLogs(category: String): Boolean

    fun getNativeLibraryStatus(): NativeLibraryStatus {
        val fallback = NativeLibraryStatus(
            loaded = false,
            libraryName = LIB_NAME,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            version = "Unavailable",
            error = loadError ?: "Native library not loaded"
        )
        if (!nativeLoaded) return fallback

        return try {
            val json = JSONObject(nativeGetLibraryStatusJson())
            NativeLibraryStatus(
                loaded = json.optBoolean("loaded", true),
                libraryName = json.optString("libraryName", LIB_NAME),
                abi = json.optString("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
                version = json.optString("version", nativeGetLibraryVersion()),
                error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            fallback.copy(error = "Failed to parse native status: ${e.message}")
        }
    }

    fun getNativeVersion(): String {
        if (!nativeLoaded) return "Unavailable"
        return try {
            nativeGetLibraryVersion()
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    fun analyzeBootImage(path: String): BootImageAnalysisResult {
        if (!nativeLoaded) {
            return BootImageAnalysisResult(
                success = false,
                path = path,
                format = "unknown",
                headerVersion = 0,
                pageSize = 0,
                kernelSize = 0,
                ramdiskSize = 0,
                kernelDetected = false,
                ramdiskDetected = false,
                avbFooterDetected = false,
                patchStatus = "Failed",
                error = loadError ?: "Native library not loaded"
            )
        }

        return try {
            val json = JSONObject(nativeAnalyzeBootImage(path))
            BootImageAnalysisResult(
                success = json.optBoolean("success", false),
                path = json.optString("path", path),
                format = json.optString("format", "unknown"),
                headerVersion = json.optInt("headerVersion", 0),
                pageSize = json.optInt("pageSize", 0),
                kernelSize = json.optInt("kernelSize", 0),
                ramdiskSize = json.optInt("ramdiskSize", 0),
                kernelDetected = json.optBoolean("kernelDetected", false),
                ramdiskDetected = json.optBoolean("ramdiskDetected", false),
                avbFooterDetected = json.optBoolean("avbFooterDetected", false),
                patchStatus = json.optString("patchStatus", "Failed"),
                error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            BootImageAnalysisResult(
                success = false,
                path = path,
                format = "unknown",
                headerVersion = 0,
                pageSize = 0,
                kernelSize = 0,
                ramdiskSize = 0,
                kernelDetected = false,
                ramdiskDetected = false,
                avbFooterDetected = false,
                patchStatus = "Failed",
                error = "Analyze failed: ${e.message}"
            )
        }
    }

    fun patchBootImage(inputPath: String, outputDir: String): BootPatchResult {
        if (!nativeLoaded) {
            return BootPatchResult(
                success = false,
                outputPath = null,
                outputFileName = null,
                outputSha256 = null,
                manualFlashWarning = "Flashing is manual. Keep your original boot.img safe.",
                error = loadError ?: "Native library not loaded"
            )
        }

        return try {
            val json = JSONObject(nativePatchBootImage(inputPath, outputDir))
            BootPatchResult(
                success = json.optBoolean("success", false),
                outputPath = json.optString("outputPath").takeIf { it.isNotBlank() && it != "null" },
                outputFileName = json.optString("outputFileName").takeIf { it.isNotBlank() && it != "null" },
                outputSha256 = json.optString("outputSha256").takeIf { it.isNotBlank() && it != "null" },
                manualFlashWarning = json.optString(
                    "manualFlashWarning",
                    "Flashing is manual. Never overwrite your original boot.img."
                ),
                error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            BootPatchResult(
                success = false,
                outputPath = null,
                outputFileName = null,
                outputSha256 = null,
                manualFlashWarning = "Flashing is manual. Keep your original boot.img safe.",
                error = "Patch failed: ${e.message}"
            )
        }
    }

    fun getNativeLogs(category: String): NativeLogQueryResult {
        if (!nativeLoaded) {
            return NativeLogQueryResult(
                entries = emptyList(),
                error = loadError ?: "Native library not loaded"
            )
        }

        return try {
            val json = JSONObject(nativeGetLogs(category))
            val status = json.optString("status", "error")
            if (status != "success") {
                return NativeLogQueryResult(
                    entries = emptyList(),
                    error = json.optString("error", "Failed to load logs")
                )
            }

            val arr = json.optJSONArray("entries") ?: JSONArray()
            val entries = buildList {
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    add(
                        NativeLogEntry(
                            timestamp = row.optString("timestamp", ""),
                            category = row.optString("category", category),
                            level = row.optString("level", "info"),
                            message = row.optString("message", "")
                        )
                    )
                }
            }
            NativeLogQueryResult(entries = entries, error = null)
        } catch (e: Exception) {
            NativeLogQueryResult(emptyList(), "Failed to parse logs: ${e.message}")
        }
    }

    fun clearNativeLogs(category: String): Boolean {
        if (!nativeLoaded) return false
        return try {
            nativeClearLogs(category)
        } catch (_: Exception) {
            false
        }
    }
}
