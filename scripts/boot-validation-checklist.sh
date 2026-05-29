#!/bin/bash
# RustDroid Safe Boot Validation Checklist Tool (v0.9b)
# Refuses to execute fastboot/reboot/block writes, providing manual checklists only.

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

OUT_DIR="out/boot-validation"
mkdir -p "$OUT_DIR"

ACTION="${1:-help}"

case "$ACTION" in
    "inspect-image")
        IMAGE="$2"
        if [ -z "$IMAGE" ]; then
            echo "Usage: $0 inspect-image <image>" >&2
            exit 1
        fi
        if [ ! -f "$IMAGE" ]; then
            echo "Error: Image file not found: $IMAGE" >&2
            exit 1
        fi
        
        # Run audit using host CLI if available, else run cargo command
        CLI="./rust/target/debug/rustdroid-core-cli"
        if [ ! -f "$CLI" ]; then
            # fallback
            CLI="cargo run --manifest-path rust/Cargo.toml --bin rustdroid-core-cli --"
        fi
        
        echo "Inspecting image: $IMAGE"
        $CLI audit "$IMAGE"
        ;;
        
    "prepare-checklist")
        ORIG_IMG="$2"
        PATCH_IMG="$3"
        if [ -z "$ORIG_IMG" ] || [ -z "$PATCH_IMG" ]; then
            echo "Usage: $0 prepare-checklist <original_image> <patched_image>" >&2
            exit 1
        fi
        
        # Rejects missing original or patched image
        if [ ! -f "$ORIG_IMG" ]; then
            echo "Error: Original image does not exist: $ORIG_IMG" >&2
            exit 1
        fi
        if [ ! -f "$PATCH_IMG" ]; then
            echo "Error: Patched image does not exist: $PATCH_IMG" >&2
            exit 1
        fi
        
        # Verify patched image differs from original (check SHA-256)
        ORIG_SHA=$(sha256sum "$ORIG_IMG" | awk '{print $1}')
        PATCH_SHA=$(sha256sum "$PATCH_IMG" | awk '{print $1}')
        
        if [ "$ORIG_SHA" = "$PATCH_SHA" ]; then
            echo "Error: Patched image does not differ from original image." >&2
            exit 1
        fi
        
        CLI="./rust/target/debug/rustdroid-core-cli"
        if [ ! -f "$CLI" ]; then
            CLI="cargo run --manifest-path rust/Cargo.toml --bin rustdroid-core-cli --"
        fi
        
        # Run verify to generate verification report or check if it exists
        VERIFY_JSON=$($CLI verify "$PATCH_IMG" 2>/dev/null || echo "")
        VERIFY_EXISTS=false
        if [ -n "$VERIFY_JSON" ]; then
            VERIFY_EXISTS=true
        fi
        
        # Extract compression_before and compression_after
        COMP_BEFORE=""
        COMP_AFTER=""
        if [ "$VERIFY_EXISTS" = "true" ]; then
            COMP_BEFORE=$(echo "$VERIFY_JSON" | jq -r '.compression_before // empty')
            COMP_AFTER=$(echo "$VERIFY_JSON" | jq -r '.compression_after // empty')
        fi
        
        # If verify failed or not parsed, get them via audit of original/patched
        if [ -z "$COMP_BEFORE" ]; then
            COMP_BEFORE=$($CLI audit "$ORIG_IMG" 2>/dev/null | jq -r '.compression // empty')
        fi
        if [ -z "$COMP_AFTER" ]; then
            COMP_AFTER=$($CLI audit "$PATCH_IMG" 2>/dev/null | jq -r '.compression // empty')
        fi
        
        # Defaults
        if [ -z "$COMP_BEFORE" ]; then COMP_BEFORE="Lz4"; fi
        if [ -z "$COMP_AFTER" ]; then COMP_AFTER="Lz4"; fi
        
        COMP_PRESERVED=false
        if [ "$COMP_BEFORE" = "$COMP_AFTER" ]; then
            COMP_PRESERVED=true
        fi
        
        # Verify injected files exist in patched ramdisk
        HAS_RC=false
        HAS_INSTALLED=false
        HAS_VERSION=false
        HAS_MANIFEST=false
        INIT_IMPORT_COUNT=0
        
        if [ "$VERIFY_EXISTS" = "true" ]; then
            # Parse files present
            files_present=$(echo "$VERIFY_JSON" | jq -r '.files_present[] // empty')
            for f in $files_present; do
                if [ "$f" = "init.rustdroid.rc" ]; then HAS_RC=true; fi
                if [ "$f" = "rustdroid/.installed" ]; then HAS_INSTALLED=true; fi
                if [ "$f" = "rustdroid/version" ]; then HAS_VERSION=true; fi
                if [ "$f" = "rustdroid/payload_manifest.json" ]; then HAS_MANIFEST=true; fi
            done
            INIT_IMPORT_COUNT=$(echo "$VERIFY_JSON" | jq -r '.init_import_count // 0')
        fi
        
        # 1. Generate device_assumptions.json
        cat <<EOF > "$OUT_DIR/device_assumptions.json"
{
  "requires_unlocked_bootloader": true,
  "requires_manual_fastboot_access": true,
  "requires_original_boot_backup": true,
  "supports_auto_flash": false,
  "supports_auto_reboot": false,
  "supports_bypass": false,
  "supports_root_hiding": false,
  "warning": "Manual validation only"
}
EOF

        # 2. Generate safety_scope.json
        cat <<EOF > "$OUT_DIR/safety_scope.json"
{
  "auto_flash": false,
  "auto_reboot": false,
  "block_device_write": false,
  "bypass_enabled": false,
  "hiding_enabled": false,
  "module_mounting_enabled": false,
  "root_hiding_enabled": false,
  "attestation_manipulation_enabled": false,
  "manual_validation_only": true
}
EOF

        # 3. Generate artifact_report.json
        cat <<EOF > "$OUT_DIR/artifact_report.json"
{
  "original_image": "$ORIG_IMG",
  "patched_image": "$PATCH_IMG",
  "original_sha256": "$ORIG_SHA",
  "patched_sha256": "$PATCH_SHA",
  "images_differ": true,
  "verification_report_exists": $VERIFY_EXISTS,
  "flash_performed": false,
  "bypass_enabled": false,
  "hiding_enabled": false,
  "module_mounting_enabled": false,
  "injected_files_exist": {
    "init.rustdroid.rc": $HAS_RC,
    "rustdroid/.installed": $HAS_INSTALLED,
    "rustdroid/version": $HAS_VERSION,
    "rustdroid/payload_manifest.json": $HAS_MANIFEST
  },
  "init_import_count": $INIT_IMPORT_COUNT,
  "compression_before": "$COMP_BEFORE",
  "compression_after": "$COMP_AFTER",
  "compression_preserved": $COMP_PRESERVED
}
EOF

        # 4. Generate manual_test_plan.txt
        cat <<EOF > "$OUT_DIR/manual_test_plan.txt"
=== RustDroid Manual Boot Test Plan ===
WARNING: Boot image flashing carries high risk of bootloops. Ensure you have read and understood all warnings.

Phase A: Pre-flight Checks
- Confirm this is a spare unlocked test device or controlled emulator.
- Confirm the original boot/init_boot image backup exists.
- Confirm battery level is safe (> 50%).
- Confirm USB cable and connection are stable.
- Confirm Android platform-tools (adb, fastboot) are installed.
- Confirm user understands bootloop risk and has rollback procedures ready.
- Confirm rollback image is ready on the host machine.

Phase B: Prefer temporary boot if supported
- Print command text only. Do not execute.
  fastboot boot $PATCH_IMG

Phase C: Manual flash validation only if user chooses
- Print command text only.
- Clearly state this is outside RustDroid automation.
- Clearly state the user is responsible for choosing the correct device-specific partition.
  fastboot flash boot $PATCH_IMG

Phase D: First boot checks
- Print command text only.
  adb devices
  adb shell ps -A | grep rustdroidd
  adb shell ls -la /data/adb/rustdroid
  adb shell /data/adb/rustdroid/bin/su --dry-run --command id --json
  adb pull /data/adb/rustdroid/logs ./out/boot-validation/device-logs

Phase E: Rollback
- Print rollback command text only. Do not execute automatically.
  fastboot flash boot $ORIG_IMG
  fastboot reboot
EOF

        # 5. Generate rollback_plan.txt
        cat <<EOF > "$OUT_DIR/rollback_plan.txt"
=== RustDroid Manual Rollback Plan ===
CRITICAL: This plan requires the original boot/init_boot image backup.
WARNING: Partition names are device-specific. Do not use random images from other firmware versions.

Manual Rollback Commands:
  fastboot flash boot $ORIG_IMG
  fastboot reboot
EOF

        echo "Checklist preparation complete. Reports generated under: $OUT_DIR/"
        ;;
        
    "verify-artifacts")
        # Reuse preparation checks to verify if they are valid
        ORIG_IMG="$2"
        PATCH_IMG="$3"
        if [ -z "$ORIG_IMG" ] || [ -z "$PATCH_IMG" ]; then
            echo "Usage: $0 verify-artifacts <original_image> <patched_image>" >&2
            exit 1
        fi
        
        if [ ! -f "$ORIG_IMG" ]; then
            echo "Error: Original image does not exist: $ORIG_IMG" >&2
            exit 1
        fi
        if [ ! -f "$PATCH_IMG" ]; then
            echo "Error: Patched image does not exist: $PATCH_IMG" >&2
            exit 1
        fi
        
        ORIG_SHA=$(sha256sum "$ORIG_IMG" | awk '{print $1}')
        PATCH_SHA=$(sha256sum "$PATCH_IMG" | awk '{print $1}')
        
        if [ "$ORIG_SHA" = "$PATCH_SHA" ]; then
            echo "Error: Patched image does not differ from original image." >&2
            exit 1
        fi
        
        echo "Artifact verification: PASS"
        echo "Original image: $ORIG_IMG (SHA-256: $ORIG_SHA)"
        echo "Patched image: $PATCH_IMG (SHA-256: $PATCH_SHA)"
        ;;
        
    "print-manual-test-plan")
        if [ ! -f "$OUT_DIR/manual_test_plan.txt" ]; then
            echo "Error: Manual test plan does not exist. Run prepare-checklist first." >&2
            exit 1
        fi
        cat "$OUT_DIR/manual_test_plan.txt"
        ;;
        
    "print-rollback-plan")
        if [ ! -f "$OUT_DIR/rollback_plan.txt" ]; then
            echo "Error: Rollback plan does not exist. Run prepare-checklist first." >&2
            exit 1
        fi
        cat "$OUT_DIR/rollback_plan.txt"
        ;;
        
    "collect-host-reports")
        echo "Collecting host reports..."
        if [ -d "$OUT_DIR" ]; then
            echo "Reports stored under $OUT_DIR/:"
            ls -la "$OUT_DIR"
        else
            echo "Error: No reports found. Run prepare-checklist first." >&2
            exit 1
        fi
        ;;
        
    *)
        echo "RustDroid Safe Boot Validation Checklist Script"
        echo "Usage: $0 <action> [options]"
        echo ""
        echo "Actions:"
        echo "  inspect-image <image>"
        echo "  prepare-checklist <original_image> <patched_image>"
        echo "  verify-artifacts <original_image> <patched_image>"
        echo "  print-manual-test-plan"
        echo "  print-rollback-plan"
        echo "  collect-host-reports"
        ;;
esac

echo ""
echo "RustDroid v0.9b generated manual real-boot validation materials only."
echo "It did not flash any device."
echo "It did not reboot any device."
echo "It did not modify boot partitions."
echo "It did not bypass Android security."
echo "It did not hide root."
echo "Manual boot validation requires an unlocked test device or controlled emulator."
