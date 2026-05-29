#!/bin/bash
# RustDroid Post-Boot Validation Script (v1.0-alpha)
# Refuses to flash or modify target system/boot partitions.

set -e

# Refuse any flash-related or reboot commands or keywords in arguments
for arg in "$@"; do
    case "$(echo "$arg" | tr '[:upper:]' '[:lower:]')" in
        *flash*|*fastboot*|*reboot*|*block*|*dev/block*)
            echo "Error: Safety boundary violation. Flash-related or reboot commands are strictly forbidden." >&2
            exit 1
            ;;
    esac
done

OUT_DIR="out/post-boot-validation"
mkdir -p "$OUT_DIR"
mkdir -p "$OUT_DIR/runtime-state"
mkdir -p "$OUT_DIR/logs"

check_adb_connected() {
    if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
        return 0
    fi

    if ! command -v adb &> /dev/null; then
        echo "Error: adb command not found." >&2
        exit 1
    fi
    
    local DEV_COUNT
    DEV_COUNT=$(adb devices | tail -n +2 | grep -v -e '^$' | wc -l)
    if [ "$DEV_COUNT" -eq 0 ]; then
        echo "Error: No ADB device or emulator connected." >&2
        exit 1
    fi
}

ACTION="${1:-help}"

case "$ACTION" in
    "check-device")
        check_adb_connected
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mock Device Model: RustDroid Emulator"
            echo "Mock Android Release: 13"
            echo "Mock ABI: aarch64"
        else
            adb devices
            echo "Model: $(adb shell getprop ro.product.model)"
            echo "Release: $(adb shell getprop ro.build.version.release)"
            echo "ABI: $(adb shell getprop ro.product.cpu.abi)"
        fi
        ;;

    "collect-runtime-state")
        check_adb_connected
        echo "Collecting runtime state files..."
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            cat <<EOF > "$OUT_DIR/runtime-state/install_state.json"
{
  "rustdroid_version": "v1.0-alpha",
  "payload_version": 2,
  "first_boot_seen": true,
  "daemon_started": true,
  "daemon_start_timestamp": "UNIX_12345678",
  "runtime_layout_initialized": true,
  "binary_self_check_passed": true,
  "policy_initialized": true,
  "module_mounting_enabled": false,
  "bypass_enabled": false,
  "hiding_enabled": false,
  "last_error": null,
  "safety_scope": {
    "execution_default_enabled": false,
    "module_mounting_enabled": false,
    "hiding_enabled": false,
    "bypass_enabled": false
  }
}
EOF
            cat <<EOF > "$OUT_DIR/runtime-state/config.json"
{
  "execution_enabled": false,
  "dry_run_default": true,
  "module_mounting_enabled": false,
  "manager_ipc_enabled": true,
  "su_ipc_enabled": true,
  "audit_enabled": true,
  "debug_logging": false,
  "allow_auto_flash": false,
  "allow_auto_reboot": false,
  "allow_block_device_write": false,
  "bypass_enabled": false,
  "hiding_enabled": false
}
EOF
        else
            # Try to grab install_state.json and config.json
            adb shell "su -c 'cat /data/adb/rustdroid/install_state.json'" > "$OUT_DIR/runtime-state/install_state.json" 2>/dev/null || rm -f "$OUT_DIR/runtime-state/install_state.json"
            adb shell "su -c 'cat /data/adb/rustdroid/config.json'" > "$OUT_DIR/runtime-state/config.json" 2>/dev/null || rm -f "$OUT_DIR/runtime-state/config.json"
        fi
        echo "Runtime state collection finished."
        ;;

    "run-daemon-self-check")
        check_adb_connected
        echo "Running daemon self-check..."
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "=== RustDroid Daemon Self Check (Mock) ==="
            echo "Result: PASSED"
        else
            adb shell "su -c '/data/adb/rustdroid/bin/rustdroidd --self-check'"
        fi
        ;;

    "run-su-self-check")
        check_adb_connected
        echo "Running SU self-check..."
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "=== RustDroid SU Client Self Check (Mock) ==="
            echo "Result: PASSED"
        else
            adb shell "su -c '/data/adb/rustdroid/bin/su --self-check'"
        fi
        ;;

    "run-su-dry-run")
        check_adb_connected
        echo "Running SU client dry-run..."
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "uid=0(root) gid=0(root) groups=0(root) (Mocked Dry Run)"
        else
            adb shell "su -c '/data/adb/rustdroid/bin/su --dry-run --command id'"
        fi
        ;;

    "collect-logs")
        check_adb_connected
        echo "Collecting device logs..."
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Staging mock logs..."
            echo "[UNIX_123456] Event: Startup, Details: mock startup details" > "$OUT_DIR/logs/daemon.log"
            echo "[UNIX_123456] UID: 0, PID: 123, Package: shell, Allowed: true" > "$OUT_DIR/logs/su.log"
            cat <<EOF > "$OUT_DIR/logs/first_boot.log"
=== RustDroid Daemon First Boot Log ===
Daemon Start Timestamp: UNIX_12345678
Process Identity: UID=0, GID=0
Runtime Directory Status: Initialized successfully
Binary Self-Check Status: Passed
Config Loaded: execution_enabled=false, dry_run_default=true, module_mounting_enabled=false
Safety Scope Summary:
- execution_enabled: false
- module_mounting_enabled: false
- bypass_enabled: false
- hiding_enabled: false
========================================
EOF
            echo "=== RustDroid Daemon Self Check === Passed" > "$OUT_DIR/logs/self_check.log"
        else
            adb shell "su -c 'cat /data/adb/rustdroid/logs/daemon.log'" > "$OUT_DIR/logs/daemon.log" 2>/dev/null || rm -f "$OUT_DIR/logs/daemon.log"
            adb shell "su -c 'cat /data/adb/rustdroid/logs/su.log'" > "$OUT_DIR/logs/su.log" 2>/dev/null || rm -f "$OUT_DIR/logs/su.log"
            adb shell "su -c 'cat /data/adb/rustdroid/logs/first_boot.log'" > "$OUT_DIR/logs/first_boot.log" 2>/dev/null || rm -f "$OUT_DIR/logs/first_boot.log"
            adb shell "su -c 'cat /data/adb/rustdroid/logs/self_check.log'" > "$OUT_DIR/logs/self_check.log" 2>/dev/null || rm -f "$OUT_DIR/logs/self_check.log"
        fi
        echo "Logs collection finished."
        ;;

    "generate-report")
        echo "Compiling validation report..."
        
        # Check files existence
        dev_connected=false
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            dev_connected=true
        else
            if adb devices | tail -n +2 | grep -v -e '^$' | wc -l &>/dev/null; then
                dev_connected=true
            fi
        fi

        runtime_layout_exists=false
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            runtime_layout_exists=true
        else
            if adb shell "su -c '[ -d /data/adb/rustdroid ]'" &>/dev/null; then
                runtime_layout_exists=true
            fi
        fi

        install_state_exists=false
        if [ -f "$OUT_DIR/runtime-state/install_state.json" ]; then
            install_state_exists=true
        fi

        config_exists=false
        if [ -f "$OUT_DIR/runtime-state/config.json" ]; then
            config_exists=true
        fi

        daemon_log_exists=false
        if [ -f "$OUT_DIR/logs/daemon.log" ]; then
            daemon_log_exists=true
        fi

        first_boot_log_exists=false
        if [ -f "$OUT_DIR/logs/first_boot.log" ]; then
            first_boot_log_exists=true
        fi

        # Default checks to true under mock, otherwise test commands
        su_self_check_passed=false
        daemon_self_check_passed=false
        su_dry_run_passed=false
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            su_self_check_passed=true
            daemon_self_check_passed=true
            su_dry_run_passed=true
        else
            if adb shell "su -c '/data/adb/rustdroid/bin/su --self-check'" &>/dev/null; then
                su_self_check_passed=true
            fi
            if adb shell "su -c '/data/adb/rustdroid/bin/rustdroidd --self-check'" &>/dev/null; then
                daemon_self_check_passed=true
            fi
            if adb shell "su -c '/data/adb/rustdroid/bin/su --dry-run --command id'" &>/dev/null; then
                su_dry_run_passed=true
            fi
        fi

        # Read fields from config.json if available
        execution_enabled=false
        module_mounting_enabled=false
        bypass_enabled=false
        hiding_enabled=false
        if [ "$config_exists" = "true" ]; then
            execution_enabled=$(jq -r '.execution_enabled' "$OUT_DIR/runtime-state/config.json" 2>/dev/null || echo false)
            module_mounting_enabled=$(jq -r '.module_mounting_enabled' "$OUT_DIR/runtime-state/config.json" 2>/dev/null || echo false)
            bypass_enabled=$(jq -r '.bypass_enabled' "$OUT_DIR/runtime-state/config.json" 2>/dev/null || echo false)
            hiding_enabled=$(jq -r '.hiding_enabled' "$OUT_DIR/runtime-state/config.json" 2>/dev/null || echo false)
        fi

        warnings="[]"
        errors="[]"
        if [ "$install_state_exists" = "false" ]; then
            errors=$(echo "$errors" | jq '. + ["install_state.json is missing"]')
        fi
        if [ "$config_exists" = "false" ]; then
            errors=$(echo "$errors" | jq '. + ["config.json is missing"]')
        fi
        if [ "$first_boot_log_exists" = "false" ]; then
            warnings=$(echo "$warnings" | jq '. + ["first_boot.log is missing"]')
        fi

        # Build json report
        jq -n \
            --argjson dev_connected "$dev_connected" \
            --argjson runtime_layout_exists "$runtime_layout_exists" \
            --argjson install_state_exists "$install_state_exists" \
            --argjson config_exists "$config_exists" \
            --argjson daemon_log_exists "$daemon_log_exists" \
            --argjson first_boot_log_exists "$first_boot_log_exists" \
            --argjson su_self_check_passed "$su_self_check_passed" \
            --argjson daemon_self_check_passed "$daemon_self_check_passed" \
            --argjson su_dry_run_passed "$su_dry_run_passed" \
            --argjson execution_enabled "$execution_enabled" \
            --argjson module_mounting_enabled "$module_mounting_enabled" \
            --argjson bypass_enabled "$bypass_enabled" \
            --argjson hiding_enabled "$hiding_enabled" \
            --argjson warnings "$warnings" \
            --argjson errors "$errors" \
            '{
                device_connected: $dev_connected,
                runtime_layout_exists: $runtime_layout_exists,
                install_state_exists: $install_state_exists,
                config_exists: $config_exists,
                daemon_log_exists: $daemon_log_exists,
                first_boot_log_exists: $first_boot_log_exists,
                su_self_check_passed: $su_self_check_passed,
                daemon_self_check_passed: $daemon_self_check_passed,
                su_dry_run_passed: $su_dry_run_passed,
                execution_enabled: $execution_enabled,
                module_mounting_enabled: $module_mounting_enabled,
                bypass_enabled: $bypass_enabled,
                hiding_enabled: $hiding_enabled,
                boot_partition_modified_by_script: false,
                reboot_performed_by_script: false,
                flash_performed_by_script: false,
                warnings: $warnings,
                errors: $errors
            }' > "$OUT_DIR/post_boot_report.json"
        
        echo "Validation report generated under: $OUT_DIR/post_boot_report.json"
        ;;

    "clean-local-reports")
        echo "Cleaning local post-boot-validation reports..."
        rm -rf "$OUT_DIR"
        echo "Clean completed."
        ;;

    *)
        echo "RustDroid Post-Boot Validation Tool (v1.0-alpha)"
        echo "Usage: $0 <action>"
        echo ""
        echo "Actions:"
        echo "  check-device"
        echo "  collect-runtime-state"
        echo "  run-daemon-self-check"
        echo "  run-su-self-check"
        echo "  run-su-dry-run"
        echo "  collect-logs"
        echo "  generate-report"
        echo "  clean-local-reports"
        ;;
esac

echo ""
echo "RustDroid v1.0-alpha validates runtime layout and first-boot logs only."
echo "It did not flash any device."
echo "It did not reboot any device."
echo "It did not modify boot partitions."
echo "It did not bypass Android security."
echo "It did not hide root."
echo "Module mounting is not implemented yet."
echo "Manual boot validation requires an unlocked test device or controlled emulator."
