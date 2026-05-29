package com.rustdroid.manager

import android.util.Log

object NativeBridge {
    private var isMock: Boolean = false

    init {
        try {
            System.loadLibrary("rustdroid_core")
            Log.i("RustDroid", "Successfully loaded rustdroid_core JNI library")
        } catch (e: UnsatisfiedLinkError) {
            isMock = true
            Log.w("RustDroid", "Failed to load rustdroid_core, falling back to mock mode", e)
        }
    }

    fun isMockMode(): Boolean = isMock

    // Declare JNI external methods
    external fun nativeGetRootStatus(): String
    external fun nativeGetRuntimeStatus(): String
    external fun nativeGetInstallState(): String
    external fun nativeGetConfig(): String
    external fun nativeGetSafetyScope(): String
    external fun nativeListPolicies(): String
    external fun nativeSetPolicy(json: String): String
    external fun nativeRemovePolicy(json: String): String
    external fun nativeListPendingRequests(): String
    external fun nativeApprovePendingRequest(json: String): String
    external fun nativeDenyPendingRequest(json: String): String
    external fun nativeGetAuditLogTail(json: String): String
    external fun nativeAuditBootImage(json: String): String
    external fun nativeVerifyPatchedImage(json: String): String
    external fun nativeGetPostBootReport(json: String): String
    external fun nativeGetPayloadMetadata(json: String): String
    external fun nativeRunSelfCheck(json: String): String

    external fun nativeValidateModuleZip(json: String): String
    external fun nativeInstallModule(json: String): String
    external fun nativeListModules(): String
    external fun nativeGetModule(json: String): String
    external fun nativeEnableModule(json: String): String
    external fun nativeDisableModule(json: String): String
    external fun nativeRemoveModule(json: String): String
    external fun nativeScanModule(json: String): String
    external fun nativeGetInstallLog(json: String): String

    external fun nativeValidateModuleScripts(json: String): String
    external fun nativeGetModuleScriptPlan(json: String): String
    external fun nativeListModuleScripts(json: String): String

    // v1.4 Security Dashboard JNI methods
    external fun nativeGetSecurityStatus(): String
    external fun nativeGetCGlueAudit(): String
    external fun nativeGetStaticSafetyReport(): String
    external fun nativeGetUiSafetyScope(): String
    external fun nativeGetRedactionPolicy(): String
    external fun nativeValidateNativeBridgeState(json: String): String

    // v1.5 Compatibility Matrix JNI methods
    external fun nativeAnalyzeBootImageCompatibility(json: String): String
    external fun nativeGetRuntimeCompatibility(json: String): String
    external fun nativeGetDeviceCompatibilitySummary(json: String): String
    external fun nativeGetReleaseReadiness(json: String): String
    external fun nativeGetCompatibilityMatrix(json: String): String
    external fun nativeExportReportBundle(json: String): String

    // Wrapper helper methods with Mock fallback logic
    fun getRootStatus(): String {
        return if (isMock) {
            """{"status":"mock","is_patched":false,"selinux_mode":"mock-enforcing","version":"v1.4-offline-mock","notice":"Mock mode: no real RustDroid daemon connected"}"""
        } else {
            try { nativeGetRootStatus() } catch (e: Exception) { e.toString() }
        }
    }

    fun getRuntimeStatus(): String {
        return if (isMock) {
            """{"status":"success","daemon_reachable":false,"daemon_responding":false,"socket_path":"/data/adb/rustdroid/rustdroidd.sock","execution_enabled":false,"module_mounting_enabled":false,"bypass_enabled":false,"hiding_enabled":false}"""
        } else {
            try { nativeGetRuntimeStatus() } catch (e: Exception) { e.toString() }
        }
    }

    fun getInstallState(): String {
        return if (isMock) {
            """{"status":"success","rustdroid_version":"v1.4-mock","payload_version":2,"first_boot_seen":true,"daemon_started":false,"daemon_start_timestamp":"N/A","runtime_layout_initialized":false,"binary_self_check_passed":false,"policy_initialized":false,"module_mounting_enabled":false,"bypass_enabled":false,"hiding_enabled":false}"""
        } else {
            try { nativeGetInstallState() } catch (e: Exception) { e.toString() }
        }
    }

    fun getConfig(): String {
        return if (isMock) {
            """{"status":"success","execution_enabled":false,"dry_run_default":true,"module_mounting_enabled":false,"manager_ipc_enabled":true,"su_ipc_enabled":true,"audit_enabled":true,"debug_logging":false,"allow_auto_flash":false,"allow_auto_reboot":false,"allow_block_device_write":false,"bypass_enabled":false,"hiding_enabled":false}"""
        } else {
            try { nativeGetConfig() } catch (e: Exception) { e.toString() }
        }
    }

    fun getSafetyScope(): String {
        return if (isMock) {
            """{"status":"success","play_integrity_bypass_supported":false,"banking_bypass_supported":false,"anti_cheat_evasion_supported":false,"root_hiding_supported":false,"process_hiding_supported":false,"file_hiding_supported":false,"kprobe_hiding_supported":false,"syscall_hiding_supported":false,"attestation_manipulation_supported":false,"stealth_behavior_supported":false,"selinux_weakening_supported":false,"pivot_root_supported":false,"exploit_privilege_escalation_supported":false,"auto_flash_supported":false,"auto_reboot_supported":false,"module_mounting_implemented":false,"safety_statement":"RustDroid operates under a strict auditable security scope. No bypasses, root hiding, stealth features, or exploit-based privilege escalations are supported or implemented. Boot image flashing must be performed manually by the user."}"""
        } else {
            try { nativeGetSafetyScope() } catch (e: Exception) { e.toString() }
        }
    }

    fun listPolicies(): String {
        return if (isMock) {
            """{"status":"success","policies":[{"uid":10085,"package_name":"com.mock.terminal","state":"Allow","rule_type":"Always","created_at":1715000000,"expires_at":null,"execution_policy":{"allow_shell":false,"allow_command":true,"require_tty":false,"max_runtime_ms":10000,"capture_output":true}}]}"""
        } else {
            try { nativeListPolicies() } catch (e: Exception) { e.toString() }
        }
    }

    fun setPolicy(json: String): String {
        return if (isMock) {
            """{"status":"success"}"""
        } else {
            try { nativeSetPolicy(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun removePolicy(json: String): String {
        return if (isMock) {
            """{"status":"success"}"""
        } else {
            try { nativeRemovePolicy(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun listPendingRequests(): String {
        return if (isMock) {
            """{"status":"success","requests":[{"request_id":"mock_req_1","verified_identity":{"verified_uid":10085,"verified_pid":4567,"verified_gid":10085,"claimed_package":"com.mock.terminal"},"command":{"args":["id"],"env":[]},"created_at":1715000000,"timeout_secs":30}]}"""
        } else {
            try { nativeListPendingRequests() } catch (e: Exception) { e.toString() }
        }
    }

    fun approvePendingRequest(json: String): String {
        return if (isMock) {
            """{"status":"success"}"""
        } else {
            try { nativeApprovePendingRequest(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun denyPendingRequest(json: String): String {
        return if (isMock) {
            """{"status":"success"}"""
        } else {
            try { nativeDenyPendingRequest(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getAuditLogTail(json: String): String {
        if (isMock) {
            val logName = if (json.contains("su.log")) "su.log"
                else if (json.contains("daemon.log")) "daemon.log"
                else if (json.contains("first_boot.log")) "first_boot.log"
                else if (json.contains("self_check.log")) "self_check.log"
                else "module.log"
            return when (logName) {
                "su.log" -> """{"status":"success","log_name":"su.log","lines":"[UNIX_1716900042] UID: 10085, PID: 4567, Package: com.mock.terminal, Allowed: true, Details: Command: id, Session: e19f...\n[UNIX_1716900050] UID: 10100, PID: 5001, Package: com.untrusted.app, Allowed: false, Details: Shell denied"}"""
                "daemon.log" -> """{"status":"success","log_name":"daemon.log","lines":"[UNIX_1716900000] Event: Startup, Details: Daemon initialized socket /data/adb/rustdroid/rustdroidd.sock\n[UNIX_1716900040] Event: RequestReceived, Details: UID: 10085, PID: 4567"}"""
                "first_boot.log" -> """{"status":"success","log_name":"first_boot.log","lines":"[UNIX_1716800000] Event: FirstBoot, Details: Runtime layout initialized\n[UNIX_1716800001] Event: SelfCheck, Details: Binary integrity verified"}"""
                "self_check.log" -> """{"status":"success","log_name":"self_check.log","lines":"[UNIX_1716900000] Check: daemon_binary OK\n[UNIX_1716900001] Check: su_binary OK\n[UNIX_1716900002] Check: policy_engine OK"}"""
                "module.log" -> """{"status":"success","log_name":"module.log","lines":"[UNIX_1716900060] Module: hello-mock, Action: install, Success: true, Details: Installed v1.0"}"""
                else -> """{"status":"success","log_name":"$logName","lines":"No log data available."}"""
            }
        } else {
            return try { nativeGetAuditLogTail(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun auditBootImage(json: String): String {
        return if (isMock) {
            """{"status":"success","image_path":"/sdcard/Download/boot.img","is_valid":true,"compression_before":"RawCpio","compression_after":"RawCpio","decompressed_ramdisk_size":1245000,"recompressed_ramdisk_size":1245000,"already_patched":false,"warnings":[]}"""
        } else {
            try { nativeAuditBootImage(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun verifyPatchedImage(json: String): String {
        return if (isMock) {
            """{"status":"success","patched_image_path":"/sdcard/Download/boot_patched.img","is_valid":true,"files_present":["init","rustdroidd"],"files_missing":[],"init_import_count":1,"forbidden_strings_found":[],"safety_scope_valid":true,"safety_scope":{"execution_default_enabled":false,"module_mounting_enabled":false,"hiding_enabled":false,"bypass_enabled":false},"flash_performed":false,"errors":[],"compression_before":"RawCpio","compression_after":"RawCpio"}"""
        } else {
            try { nativeVerifyPatchedImage(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getPostBootReport(json: String): String {
        return if (isMock) {
            """{"status":"success","device_connected":true,"runtime_layout_exists":true,"install_state_exists":true,"config_exists":true,"daemon_self_check_passed":true,"su_self_check_passed":true,"su_dry_run_passed":true,"flash_performed_by_script":false,"reboot_performed_by_script":false,"boot_partition_modified_by_script":false,"notice":"This is a post-boot validation summary report showing that no partition modification or reboot was automated."}"""
        } else {
            try { nativeGetPostBootReport(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getPayloadMetadata(json: String): String {
        return if (isMock) {
            """{"status":"success","rustdroid_version":"v1.4-mock","payload_version":2,"target_arch":"aarch64","safety_scope":{"execution_default_enabled":false,"module_mounting_enabled":false,"hiding_enabled":false,"bypass_enabled":false}}"""
        } else {
            try { nativeGetPayloadMetadata(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun runSelfCheck(json: String): String {
        return if (isMock) {
            """{"status":"success","daemon_ok":true,"su_ok":true,"policy_engine_ok":true,"sandbox_check":"passed","safety_scope_enforced":true}"""
        } else {
            try { nativeRunSelfCheck(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun validateModuleZip(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"is_valid":true,"error":null,"warnings":[],"props":{"id":"hello-mock","name":"Mock Module","version":"1.0","versionCode":"1","author":"Author","description":"Mocking"},"safety_report":{"safe":true,"forbidden_strings_found":[],"suspicious_paths_found":[],"warnings":[]}}}"""
        } else {
            try { nativeValidateModuleZip(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun installModule(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"success":true,"error":null,"warnings":[],"module_id":"hello-mock","files_count":5,"install_log":"Mock install log: Extracting... Done."}}"""
        } else {
            try { nativeInstallModule(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun listModules(): String {
        return if (isMock) {
            """{"status":"success","modules":[{"id":"hello-mock","name":"Mock Module","version":"1.0","version_code":"1","author":"Author","description":"Mocking","installed_at":1715000000,"enabled":false,"safe_mode_disabled":false,"requires_execution":true,"requires_mounting":true,"requires_reboot":false,"install_source_hash":"mock_hash","files_count":5,"scripts_present":["post-fs-data.sh"],"safety_scan":"passed","warnings":[],"script_validation_status":"unvalidated","script_hard_errors_count":0,"script_warnings_count":0}]}"""
        } else {
            try { nativeListModules() } catch (e: Exception) { e.toString() }
        }
    }

    fun getModule(json: String): String {
        return if (isMock) {
            """{"status":"success","module":{"id":"hello-mock","name":"Mock Module","version":"1.0","version_code":"1","author":"Author","description":"Mocking","installed_at":1715000000,"enabled":false,"safe_mode_disabled":false,"requires_execution":true,"requires_mounting":true,"requires_reboot":false,"install_source_hash":"mock_hash","files_count":5,"scripts_present":["post-fs-data.sh"],"safety_scan":"passed","warnings":[]}}"""
        } else {
            try { nativeGetModule(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun enableModule(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"success":true,"module_id":"hello-mock","enabled":true,"safe_mode_disabled":false,"error":null}}"""
        } else {
            try { nativeEnableModule(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun disableModule(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"success":true,"module_id":"hello-mock","enabled":false,"safe_mode_disabled":false,"error":null}}"""
        } else {
            try { nativeDisableModule(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun removeModule(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"success":true,"module_id":"hello-mock","error":null}}"""
        } else {
            try { nativeRemoveModule(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun scanModule(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"safe":true,"forbidden_strings_found":[],"suspicious_paths_found":[],"warnings":[]}}"""
        } else {
            try { nativeScanModule(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getInstallLog(json: String): String {
        return if (isMock) {
            val moduleId = org.json.JSONObject(json).optString("module_id")
            """{"status":"success","install_log":"Mock install log for $moduleId:\n[12:00:00] Initializing...\n[12:00:01] Validating module.prop...\n[12:00:02] Extracting files...\n[12:00:03] Installation completed successfully!"}"""
        } else {
            try { nativeGetInstallLog(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun validateModuleScripts(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"script_name":"Combined Scripts","is_valid":true,"hard_errors":[],"warnings":[],"classified_actions":[]}}"""
        } else {
            try { nativeValidateModuleScripts(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getModuleScriptPlan(json: String): String {
        return if (isMock) {
            """{"status":"success","plan":{"module_id":"hello-mock","scripts_found":["post-fs-data.sh"],"scripts_valid":true,"hard_errors":[],"warnings":[],"classified_actions":[],"boot_stage_order":["post-fs-data.sh","service.sh"],"execution_enabled":false,"mounting_enabled":false,"dry_run_only":true,"safe_to_execute_later":true,"reason":"Mock verification successful"}}"""
        } else {
            try { nativeGetModuleScriptPlan(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun listModuleScripts(json: String): String {
        return if (isMock) {
            """{"status":"success","scripts":[{"script_name":"post-fs-data.sh","exists":true,"size_bytes":150}]}"""
        } else {
            try { nativeListModuleScripts(json) } catch (e: Exception) { e.toString() }
        }
    }

    // v1.4 Security Dashboard API wrappers with mock fallbacks
    fun getSecurityStatus(): String {
        return if (isMock) {
            """{"status":"success","security":{"selinux_read_only":true,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"auto_flash_enabled":false,"auto_reboot_enabled":false,"block_device_write_enabled":false,"jni_bridge_loaded":false,"mock_mode":true,"protocol_version":1,"rustdroid_version":"v1.4-mock"}}"""
        } else {
            try { nativeGetSecurityStatus() } catch (e: Exception) { e.toString() }
        }
    }

    fun getCGlueAudit(): String {
        return if (isMock) {
            """{"status":"success","c_glue_audit":{"header_file":"rustdroid_c.h","files":[{"file":"android_glue.c","status":"safe","description":"read-only property access","forbidden_symbols_found":[]},{"file":"mount_glue.c","status":"disabled","description":"bind mount disabled in v1.4","forbidden_symbols_found":[]},{"file":"selinux_glue.c","status":"read-only","description":"SELinux read-only inspection","forbidden_symbols_found":[]},{"file":"process_glue.c","status":"restricted","description":"credential switching only","forbidden_symbols_found":[]}],"forbidden_checks":[],"overall_status":"safe","mount_glue_disabled":true,"selinux_glue_read_only":true,"process_glue_safe":true,"android_glue_safe":true}}"""
        } else {
            try { nativeGetCGlueAudit() } catch (e: Exception) { e.toString() }
        }
    }

    fun getStaticSafetyReport(): String {
        return if (isMock) {
            """{"status":"success","static_safety":{"scan_timestamp":0,"scanned_directories":["rust/","c/","manager/android/","scripts/"],"forbidden_patterns_checked":["setenforce","system(","popen("],"violations_found":0,"overall_result":"clean","mount_glue_status":"disabled","selinux_glue_status":"read-only","script_execution_status":"not_implemented","module_mounting_status":"not_implemented"}}"""
        } else {
            try { nativeGetStaticSafetyReport() } catch (e: Exception) { e.toString() }
        }
    }

    fun getUiSafetyScope(): String {
        return if (isMock) {
            """{"status":"success","ui_safety":{"safety_badges":{"bypass":false,"hiding":false,"module_mounting":false,"script_execution":false,"auto_flash":false,"auto_reboot":false},"safety_warning":"RustDroid does not bypass Android security and does not hide root.","dangerous_capabilities":{"selinux_modification":false,"block_device_write":false,"automatic_flash":false,"automatic_reboot":false,"script_execution":false,"module_mounting":false,"process_hiding":false,"file_hiding":false,"attestation_manipulation":false}}}"""
        } else {
            try { nativeGetUiSafetyScope() } catch (e: Exception) { e.toString() }
        }
    }

    fun getRedactionPolicy(): String {
        return if (isMock) {
            """{"status":"success","redaction":{"session_tokens":"first_4_chars_only","command_lines":"basename_and_arg_count_only","package_claims":"marked_untrusted_unless_verified","selinux_context":"read_only_display","logs":"redacted_by_default","debug_mode":"explicit_opt_in_required","token_example":"exam...","command_example":"ls (2 args)"}}"""
        } else {
            try { nativeGetRedactionPolicy() } catch (e: Exception) { e.toString() }
        }
    }

    fun validateNativeBridgeState(json: String): String {
        return if (isMock) {
            """{"status":"success","bridge_state":{"loaded":false,"mock_mode":true,"library_name":"mock","execution_enabled":false,"dangerous_capabilities_active":false,"safety_scope_enforced":true}}"""
        } else {
            try { nativeValidateNativeBridgeState(json) } catch (e: Exception) { e.toString() }
        }
    }

    // v1.5 Compatibility Matrix API wrappers
    fun analyzeBootImageCompatibility(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"report_version":1,"generated_at":0,"device_source":"mock_analysis","android_release":"14","sdk_version":34,"device_model":"Mock Device","device_brand":"Mock","device_product":"mock","device_codename":"mock","cpu_arch":"aarch64","abi_list":"arm64-v8a","kernel_release":"6.1.0-mock","boot_image_header_version":4,"image_type":"init_boot","ramdisk_compression":"Gzip","ramdisk_roundtrip_supported":true,"cpio_valid":true,"init_import_supported":true,"payload_arch_supported":true,"selinux_context_readable":true,"runtime_layout_supported":true,"adb_validation_supported":true,"manual_boot_validation_supported":true,"cloud_phone_limited":false,"compatibility_level":"SupportedForOfflinePatch","blockers":[],"warnings":[],"recommendations":["Mock: Verify patched image before flashing"],"safety_scope":{"auto_flash":false,"auto_reboot":false,"block_device_write":false,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"manual_validation_only":true}},"input_hash":"mock_hash"}"""
        } else {
            try { nativeAnalyzeBootImageCompatibility(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getRuntimeCompatibility(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"report_version":1,"generated_at":0,"runtime_layout_exists":false,"config_exists":false,"install_state_exists":false,"logs_dir_exists":false,"daemon_self_check_passed":null,"su_self_check_passed":null,"execution_enabled":false,"module_mounting_enabled":false,"bypass_enabled":false,"hiding_enabled":false,"c_glue_audit_status":"safe","static_safety_status":"clean","safety_scope":{"auto_flash":false,"auto_reboot":false,"block_device_write":false,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"manual_validation_only":true}}}"""
        } else {
            try { nativeGetRuntimeCompatibility(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getDeviceCompatibilitySummary(json: String): String {
        return if (isMock) {
            """{"status":"success","summary":{"android_release":"14","sdk_version":34,"device_model":"Mock Device","cpu_arch":"aarch64","cloud_phone":false,"arch_supported":true,"compatibility_level":"SupportedForOfflinePatch","blockers":[],"warnings":[],"recommendations":["Back up your boot image before patching"],"safety_scope":{"auto_flash":false,"auto_reboot":false,"block_device_write":false,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"manual_validation_only":true}}}"""
        } else {
            try { nativeGetDeviceCompatibilitySummary(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getReleaseReadiness(json: String): String {
        return if (isMock) {
            """{"status":"success","report":{"report_version":1,"generated_at":0,"tests_passed":false,"warnings_zero":false,"security_scan_clean":false,"c_glue_audit_clean":true,"android_arm64_build_passed":false,"android_manager_build_passed":false,"payload_packaged":false,"metadata_hashes_present":false,"safety_scope_valid":true,"no_auto_flash":true,"no_auto_reboot":true,"no_block_device_write":true,"no_bypass":true,"no_root_hiding":true,"no_module_mounting":true,"no_script_execution":true,"readiness_level":"Unknown","blockers":["Release gate not run yet"],"safety_scope":{"auto_flash":false,"auto_reboot":false,"block_device_write":false,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"manual_validation_only":true}}}"""
        } else {
            try { nativeGetReleaseReadiness(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun getCompatibilityMatrix(json: String): String {
        return if (isMock) {
            """{"status":"success","matrix":{"supported_architectures":["aarch64"],"supported_compressions":["RawCpio","Gzip","LZ4","LZ4Legacy"],"supported_header_versions":[0,1,2,3,4],"min_sdk_version":26,"supported_image_types":["boot","init_boot"],"supported_patch_modes":["offline_patch_as_file"],"not_supported":{"auto_flash":false,"auto_reboot":false,"module_mounting":false,"script_execution":false,"root_hiding":false,"bypass":false,"block_device_write":false},"safety_scope":{"auto_flash":false,"auto_reboot":false,"block_device_write":false,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"manual_validation_only":true},"version":"v1.5"}}"""
        } else {
            try { nativeGetCompatibilityMatrix(json) } catch (e: Exception) { e.toString() }
        }
    }

    fun exportReportBundle(json: String): String {
        return if (isMock) {
            """{"status":"success","bundle":{"output_dir":"out/compatibility","included_files":["compatibility_summary.json","boot_image_compatibility.json","runtime_compatibility.json"],"excluded_items":["Full session tokens (redacted)","Boot images (not requested)"],"redaction_applied":true,"safety_scope":{"auto_flash":false,"auto_reboot":false,"block_device_write":false,"bypass_enabled":false,"hiding_enabled":false,"module_mounting_enabled":false,"script_execution_enabled":false,"manual_validation_only":true}}}"""
        } else {
            try { nativeExportReportBundle(json) } catch (e: Exception) { e.toString() }
        }
    }
}
