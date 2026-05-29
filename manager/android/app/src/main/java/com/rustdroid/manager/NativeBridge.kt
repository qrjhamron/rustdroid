package com.rustdroid.manager

import android.util.Log

/**
 * NativeBridge: JNI bridge to the Rust rustdroid_core library.
 *
 * Production paths never return mock/fake data.
 * If the native library fails to load, all calls return a structured error JSON
 * with status "unavailable" so the UI can display a clean error state.
 */
object NativeBridge {
    private const val TAG = "RustDroid"
    private var nativeLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("rustdroid_core")
            nativeLoaded = true
            Log.i(TAG, "Successfully loaded rustdroid_core JNI library")
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
            Log.w(TAG, "Failed to load rustdroid_core native library", e)
        }
    }

    /** Whether the real native library is loaded. */
    fun isNativeLoaded(): Boolean = nativeLoaded

    // ── JNI external declarations ──────────────────────────────────
    private external fun nativeGetRootStatus(): String
    private external fun nativeGetRuntimeStatus(): String
    private external fun nativeGetInstallState(): String
    private external fun nativeGetConfig(): String
    private external fun nativeGetSafetyScope(): String
    private external fun nativeListPolicies(): String
    private external fun nativeSetPolicy(json: String): String
    private external fun nativeRemovePolicy(json: String): String
    private external fun nativeListPendingRequests(): String
    private external fun nativeApprovePendingRequest(json: String): String
    private external fun nativeDenyPendingRequest(json: String): String
    private external fun nativeGetAuditLogTail(json: String): String
    private external fun nativeAuditBootImage(json: String): String
    private external fun nativeVerifyPatchedImage(json: String): String
    private external fun nativeGetPostBootReport(json: String): String
    private external fun nativeGetPayloadMetadata(json: String): String
    private external fun nativeRunSelfCheck(json: String): String
    private external fun nativeValidateModuleZip(json: String): String
    private external fun nativeInstallModule(json: String): String
    private external fun nativeListModules(): String
    private external fun nativeGetModule(json: String): String
    private external fun nativeEnableModule(json: String): String
    private external fun nativeDisableModule(json: String): String
    private external fun nativeRemoveModule(json: String): String
    private external fun nativeScanModule(json: String): String
    private external fun nativeGetInstallLog(json: String): String
    private external fun nativeValidateModuleScripts(json: String): String
    private external fun nativeGetModuleScriptPlan(json: String): String
    private external fun nativeListModuleScripts(json: String): String
    private external fun nativeGetSecurityStatus(): String
    private external fun nativeGetCGlueAudit(): String
    private external fun nativeGetStaticSafetyReport(): String
    private external fun nativeGetUiSafetyScope(): String
    private external fun nativeGetRedactionPolicy(): String
    private external fun nativeValidateNativeBridgeState(json: String): String
    private external fun nativeAnalyzeBootImageCompatibility(json: String): String
    private external fun nativeGetRuntimeCompatibility(json: String): String
    private external fun nativeGetDeviceCompatibilitySummary(json: String): String
    private external fun nativeGetReleaseReadiness(json: String): String
    private external fun nativeGetCompatibilityMatrix(json: String): String
    private external fun nativeExportReportBundle(json: String): String

    // ── Error helpers ──────────────────────────────────────────────

    private fun unavailableJson(reason: String = "Native library not loaded"): String =
        """{"status":"unavailable","error":"$reason"}"""

    private fun errorJson(e: Exception): String =
        """{"status":"error","error":"${e.message?.replace("\"", "'") ?: "unknown"}"}"""

    private inline fun callNative(crossinline block: () -> String): String {
        if (!nativeLoaded) return unavailableJson()
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Native call failed", e)
            errorJson(e)
        }
    }

    // ── Public API (no mock fallbacks) ─────────────────────────────

    fun getRootStatus(): String = callNative { nativeGetRootStatus() }
    fun getRuntimeStatus(): String = callNative { nativeGetRuntimeStatus() }
    fun getInstallState(): String = callNative { nativeGetInstallState() }
    fun getConfig(): String = callNative { nativeGetConfig() }
    fun getSafetyScope(): String = callNative { nativeGetSafetyScope() }

    fun listPolicies(): String = callNative { nativeListPolicies() }
    fun setPolicy(json: String): String = callNative { nativeSetPolicy(json) }
    fun removePolicy(json: String): String = callNative { nativeRemovePolicy(json) }

    fun listPendingRequests(): String = callNative { nativeListPendingRequests() }
    fun approvePendingRequest(json: String): String = callNative { nativeApprovePendingRequest(json) }
    fun denyPendingRequest(json: String): String = callNative { nativeDenyPendingRequest(json) }

    fun getAuditLogTail(json: String): String = callNative { nativeGetAuditLogTail(json) }
    fun auditBootImage(json: String): String = callNative { nativeAuditBootImage(json) }
    fun verifyPatchedImage(json: String): String = callNative { nativeVerifyPatchedImage(json) }
    fun getPostBootReport(json: String): String = callNative { nativeGetPostBootReport(json) }
    fun getPayloadMetadata(json: String): String = callNative { nativeGetPayloadMetadata(json) }
    fun runSelfCheck(json: String): String = callNative { nativeRunSelfCheck(json) }

    fun validateModuleZip(json: String): String = callNative { nativeValidateModuleZip(json) }
    fun installModule(json: String): String = callNative { nativeInstallModule(json) }
    fun listModules(): String = callNative { nativeListModules() }
    fun getModule(json: String): String = callNative { nativeGetModule(json) }
    fun enableModule(json: String): String = callNative { nativeEnableModule(json) }
    fun disableModule(json: String): String = callNative { nativeDisableModule(json) }
    fun removeModule(json: String): String = callNative { nativeRemoveModule(json) }
    fun scanModule(json: String): String = callNative { nativeScanModule(json) }
    fun getInstallLog(json: String): String = callNative { nativeGetInstallLog(json) }
    fun validateModuleScripts(json: String): String = callNative { nativeValidateModuleScripts(json) }
    fun getModuleScriptPlan(json: String): String = callNative { nativeGetModuleScriptPlan(json) }
    fun listModuleScripts(json: String): String = callNative { nativeListModuleScripts(json) }

    fun getSecurityStatus(): String = callNative { nativeGetSecurityStatus() }
    fun getCGlueAudit(): String = callNative { nativeGetCGlueAudit() }
    fun getStaticSafetyReport(): String = callNative { nativeGetStaticSafetyReport() }
    fun getUiSafetyScope(): String = callNative { nativeGetUiSafetyScope() }
    fun getRedactionPolicy(): String = callNative { nativeGetRedactionPolicy() }
    fun validateNativeBridgeState(json: String): String = callNative { nativeValidateNativeBridgeState(json) }

    fun analyzeBootImageCompatibility(json: String): String = callNative { nativeAnalyzeBootImageCompatibility(json) }
    fun getRuntimeCompatibility(json: String): String = callNative { nativeGetRuntimeCompatibility(json) }
    fun getDeviceCompatibilitySummary(json: String): String = callNative { nativeGetDeviceCompatibilitySummary(json) }
    fun getReleaseReadiness(json: String): String = callNative { nativeGetReleaseReadiness(json) }
    fun getCompatibilityMatrix(json: String): String = callNative { nativeGetCompatibilityMatrix(json) }
    fun exportReportBundle(json: String): String = callNative { nativeExportReportBundle(json) }
}
