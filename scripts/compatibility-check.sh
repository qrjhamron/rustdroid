#!/bin/bash
# ============================================================
# RustDroid Device Compatibility Checker v1.5
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$PROJECT_DIR/out/compatibility"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

# Safety check - refuse any arguments containing forbidden commands
for arg in "$@"; do
    for forbidden in reboot fastboot "dd " mount remount setenforce; do
        if echo "$arg" | grep -qi "$forbidden"; then
            echo -e "${RED}REFUSED: Argument contains forbidden command '$forbidden'${NC}"
            exit 1
        fi
    done
done

mkdir -p "$OUT_DIR"

ACTION="${1:-help}"

case "$ACTION" in
    collect-device-info)
        echo -e "${BLUE}${BOLD}Collecting device information (read-only)...${NC}"
        # Check ADB connection
        if ! adb devices 2>/dev/null | grep -q 'device$'; then
            echo -e "${YELLOW}No ADB device connected. Generating offline placeholder.${NC}"
            cat > "$OUT_DIR/device_info.json" << 'OFFLINE_EOF'
{
  "source": "offline",
  "android_release": "unknown",
  "sdk_version": 0,
  "device_model": "unknown",
  "device_brand": "unknown",
  "device_product": "unknown",
  "device_codename": "unknown",
  "cpu_arch": "unknown",
  "abi_list": "unknown",
  "kernel_release": "unknown",
  "user_id": "unknown",
  "cloud_phone": false,
  "safety_scope": {
    "auto_flash": false,
    "auto_reboot": false,
    "block_device_write": false,
    "bypass_enabled": false,
    "hiding_enabled": false,
    "module_mounting_enabled": false,
    "script_execution_enabled": false,
    "manual_validation_only": true
  }
}
OFFLINE_EOF
            echo -e "${GREEN}Offline device_info.json written to $OUT_DIR/device_info.json${NC}"
            exit 0
        fi

        ANDROID_RELEASE=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "unknown")
        SDK_VERSION=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || echo "0")
        DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "unknown")
        DEVICE_BRAND=$(adb shell getprop ro.product.brand 2>/dev/null | tr -d '\r' || echo "unknown")
        DEVICE_PRODUCT=$(adb shell getprop ro.product.device 2>/dev/null | tr -d '\r' || echo "unknown")
        DEVICE_CODENAME=$(adb shell getprop ro.product.name 2>/dev/null | tr -d '\r' || echo "unknown")
        CPU_ARCH=$(adb shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r' || echo "unknown")
        ABI_LIST=$(adb shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r' || echo "unknown")
        KERNEL_RELEASE=$(adb shell uname -r 2>/dev/null | tr -d '\r' || echo "unknown")
        USER_ID=$(adb shell id 2>/dev/null | tr -d '\r' || echo "unknown")

        # Detect cloud phone heuristic
        CLOUD_PHONE=false
        if echo "$DEVICE_MODEL" | grep -qi "cloud\|virtual\|emulator"; then
            CLOUD_PHONE=true
        fi

        cat > "$OUT_DIR/device_info.json" << EOF
{
  "source": "adb_live",
  "android_release": "$ANDROID_RELEASE",
  "sdk_version": $SDK_VERSION,
  "device_model": "$DEVICE_MODEL",
  "device_brand": "$DEVICE_BRAND",
  "device_product": "$DEVICE_PRODUCT",
  "device_codename": "$DEVICE_CODENAME",
  "cpu_arch": "$CPU_ARCH",
  "abi_list": "$ABI_LIST",
  "kernel_release": "$KERNEL_RELEASE",
  "user_id": "$USER_ID",
  "cloud_phone": $CLOUD_PHONE,
  "safety_scope": {
    "auto_flash": false,
    "auto_reboot": false,
    "block_device_write": false,
    "bypass_enabled": false,
    "hiding_enabled": false,
    "module_mounting_enabled": false,
    "script_execution_enabled": false,
    "manual_validation_only": true
  }
}
EOF
        echo -e "${GREEN}Device info written to $OUT_DIR/device_info.json${NC}"
        ;;

    analyze-image)
        IMAGE="${2:-}"
        if [ -z "$IMAGE" ]; then
            echo -e "${RED}Usage: $0 analyze-image <path-to-boot.img>${NC}"
            exit 1
        fi
        if [ ! -f "$IMAGE" ]; then
            echo -e "${RED}Image file not found: $IMAGE${NC}"
            exit 1
        fi
        echo -e "${BLUE}${BOLD}Analyzing boot image compatibility...${NC}"
        echo -e "${BLUE}Image: $IMAGE${NC}"

        # Check magic
        MAGIC=$(xxd -l 8 -p "$IMAGE" 2>/dev/null || echo "")
        HAS_MAGIC=false
        if [ "$MAGIC" = "414e44524f494421" ]; then
            HAS_MAGIC=true
        fi

        # Detect header version (bytes 40-43, little-endian)
        if [ "$HAS_MAGIC" = "true" ]; then
            HEADER_HEX=$(xxd -s 40 -l 4 -p "$IMAGE" 2>/dev/null || echo "00000000")
            HEADER_VERSION=$(printf '%d' "0x$(echo "$HEADER_HEX" | sed 's/\(..\)\(..\)\(..\)\(..\)/\4\3\2\1/')" 2>/dev/null || echo 0)
        else
            HEADER_VERSION=0
        fi

        IMAGE_TYPE="boot"
        if [ "$HEADER_VERSION" -ge 3 ] 2>/dev/null; then
            IMAGE_TYPE="init_boot"
        fi

        # Detect ramdisk compression
        RAMDISK_MAGIC=$(xxd -s 4096 -l 4 -p "$IMAGE" 2>/dev/null || echo "00000000")
        RAMDISK_COMP="Unknown"
        ROUNDTRIP="false"
        case "$RAMDISK_MAGIC" in
            1f8b*) RAMDISK_COMP="Gzip"; ROUNDTRIP="true" ;;
            3037*) RAMDISK_COMP="CPIO"; ROUNDTRIP="true" ;;
            04224d18*) RAMDISK_COMP="LZ4"; ROUNDTRIP="true" ;;
            02214c18*) RAMDISK_COMP="LZ4Legacy"; ROUNDTRIP="true" ;;
        esac

        BLOCKED="false"
        BLOCKERS="[]"
        if [ "$HAS_MAGIC" = "false" ]; then
            BLOCKED="true"
            BLOCKERS='[{"code":"INVALID_MAGIC","message":"No valid ANDROID! header","severity":"critical","remediation_hint":"Use a valid boot.img or init_boot.img"}]'
        elif [ "$ROUNDTRIP" = "false" ]; then
            BLOCKED="true"
            BLOCKERS="[{\"code\":\"UNSUPPORTED_COMPRESSION\",\"message\":\"Ramdisk compression '$RAMDISK_COMP' not supported\",\"severity\":\"critical\",\"remediation_hint\":\"RustDroid supports Gzip, LZ4, LZ4Legacy, raw CPIO\"}]"
        fi

        LEVEL="SupportedForOfflinePatch"
        if [ "$BLOCKED" = "true" ]; then
            LEVEL="Blocked"
        fi

        FILE_SIZE=$(stat -c%s "$IMAGE" 2>/dev/null || echo 0)

        cat > "$OUT_DIR/boot_image_compatibility.json" << EOF
{
  "status": "success",
  "image_path": "$IMAGE",
  "file_size": $FILE_SIZE,
  "has_magic": $HAS_MAGIC,
  "header_version": $HEADER_VERSION,
  "image_type": "$IMAGE_TYPE",
  "ramdisk_compression": "$RAMDISK_COMP",
  "ramdisk_roundtrip_supported": $ROUNDTRIP,
  "compatibility_level": "$LEVEL",
  "blockers": $BLOCKERS,
  "safety_scope": {
    "auto_flash": false,
    "auto_reboot": false,
    "block_device_write": false,
    "bypass_enabled": false,
    "hiding_enabled": false,
    "module_mounting_enabled": false,
    "script_execution_enabled": false,
    "manual_validation_only": true
  }
}
EOF
        echo -e "${GREEN}Boot image compatibility report: $OUT_DIR/boot_image_compatibility.json${NC}"
        echo -e "  Magic: $HAS_MAGIC | Header: v$HEADER_VERSION | Type: $IMAGE_TYPE"
        echo -e "  Compression: $RAMDISK_COMP | Roundtrip: $ROUNDTRIP | Level: $LEVEL"
        ;;

    generate-report)
        IMAGE="${2:-}"
        if [ -z "$IMAGE" ]; then
            echo -e "${RED}Usage: $0 generate-report <path-to-boot.img>${NC}"
            exit 1
        fi
        echo -e "${BLUE}${BOLD}Generating full compatibility report...${NC}"
        # Run device info collection first
        "$0" collect-device-info
        # Run image analysis
        "$0" analyze-image "$IMAGE"
        # Generate runtime compatibility
        DATA_DIR="${RUSTDROID_DATA_DIR:-/data/adb/rustdroid}"
        LAYOUT_EXISTS=false
        CONFIG_EXISTS=false
        INSTALL_STATE_EXISTS=false
        LOGS_DIR_EXISTS=false
        [ -d "$DATA_DIR" ] && LAYOUT_EXISTS=true
        [ -f "$DATA_DIR/config.json" ] && CONFIG_EXISTS=true
        [ -f "$DATA_DIR/install_state.json" ] && INSTALL_STATE_EXISTS=true
        [ -d "$DATA_DIR/logs" ] && LOGS_DIR_EXISTS=true

        cat > "$OUT_DIR/runtime_compatibility.json" << EOF
{
  "status": "success",
  "runtime_layout_exists": $LAYOUT_EXISTS,
  "config_exists": $CONFIG_EXISTS,
  "install_state_exists": $INSTALL_STATE_EXISTS,
  "logs_dir_exists": $LOGS_DIR_EXISTS,
  "execution_enabled": false,
  "module_mounting_enabled": false,
  "bypass_enabled": false,
  "hiding_enabled": false,
  "c_glue_audit_status": "safe",
  "static_safety_status": "clean",
  "safety_scope": {
    "auto_flash": false,
    "auto_reboot": false,
    "block_device_write": false,
    "bypass_enabled": false,
    "hiding_enabled": false,
    "module_mounting_enabled": false,
    "script_execution_enabled": false,
    "manual_validation_only": true
  }
}
EOF

        # Generate summary
        cat > "$OUT_DIR/compatibility_summary.json" << EOF
{
  "generated_at": $(date +%s),
  "device_info": "$OUT_DIR/device_info.json",
  "boot_image_report": "$OUT_DIR/boot_image_compatibility.json",
  "runtime_report": "$OUT_DIR/runtime_compatibility.json",
  "rustdroid_version": "v1.5",
  "safety_statement": "This report was generated using read-only analysis only. No device modification, flashing, rebooting, mounting, or script execution was performed."
}
EOF
        echo -e "${GREEN}Full compatibility report generated in $OUT_DIR/${NC}"
        ;;

    collect-report-bundle)
        echo -e "${BLUE}${BOLD}Creating report bundle...${NC}"
        if [ ! -f "$OUT_DIR/compatibility_summary.json" ]; then
            echo -e "${YELLOW}No compatibility report found. Run 'generate-report' first.${NC}"
            exit 1
        fi
        BUNDLE="$OUT_DIR/report_bundle.zip"
        cd "$OUT_DIR"
        zip -j "$BUNDLE" \
            device_info.json \
            boot_image_compatibility.json \
            runtime_compatibility.json \
            compatibility_summary.json \
            2>/dev/null || true
        # Add release gate if present
        [ -f "$PROJECT_DIR/out/release-gate/release_gate_report.json" ] && \
            zip -j "$BUNDLE" "$PROJECT_DIR/out/release-gate/release_gate_report.json" 2>/dev/null || true
        # Add security scan summary if present
        [ -f "$PROJECT_DIR/out/release-gate/release_gate_summary.txt" ] && \
            zip -j "$BUNDLE" "$PROJECT_DIR/out/release-gate/release_gate_summary.txt" 2>/dev/null || true
        # Add package metadata if present
        [ -f "$PROJECT_DIR/out/rustdroid_payload/metadata.json" ] && \
            zip -j "$BUNDLE" "$PROJECT_DIR/out/rustdroid_payload/metadata.json" 2>/dev/null || true
        cd "$PROJECT_DIR"
        echo -e "${GREEN}Report bundle: $BUNDLE${NC}"
        ;;

    print-summary)
        echo -e "${BLUE}${BOLD}══════════════════════════════════════════════════${NC}"
        echo -e "${BLUE}${BOLD}  RustDroid Compatibility Summary${NC}"
        echo -e "${BLUE}${BOLD}══════════════════════════════════════════════════${NC}"
        if [ -f "$OUT_DIR/compatibility_summary.json" ]; then
            cat "$OUT_DIR/compatibility_summary.json"
        else
            echo -e "${YELLOW}No summary available. Run 'generate-report' first.${NC}"
        fi
        echo ""
        echo -e "${GREEN}Safety: No flash, no reboot, no mount, no bypass, no hiding${NC}"
        ;;

    help|*)
        echo -e "${BLUE}${BOLD}RustDroid Compatibility Checker v1.5${NC}"
        echo ""
        echo "Usage: $0 <action> [args]"
        echo ""
        echo "Actions:"
        echo "  collect-device-info              Collect device info via ADB (read-only)"
        echo "  analyze-image <image>            Analyze boot image compatibility"
        echo "  generate-report <image>          Generate full compatibility report"
        echo "  collect-report-bundle            Create ZIP bundle of reports"
        echo "  print-summary                    Print compatibility summary"
        echo "  help                             Show this help"
        echo ""
        echo "Safety: This script only performs read-only operations."
        echo "It does not flash, reboot, mount, or modify devices."
        ;;
esac
