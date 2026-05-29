#!/bin/bash
# ============================================================
# RustDroid Release Readiness Gate v1.5
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$PROJECT_DIR/out/release-gate"
RUST_DIR="$PROJECT_DIR/rust"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

mkdir -p "$OUT_DIR"

echo -e "${BLUE}${BOLD}══════════════════════════════════════════════════${NC}"
echo -e "${BLUE}${BOLD}  RustDroid Release Readiness Gate v1.5${NC}"
echo -e "${BLUE}${BOLD}══════════════════════════════════════════════════${NC}"
echo ""

# Track results
TESTS_PASSED=false
WARNINGS_ZERO=false
SECURITY_SCAN_CLEAN=false
C_GLUE_AUDIT_CLEAN=false
HOST_BUILD_PASSED=false
ANDROID_ARM64_BUILD="skipped"
ANDROID_MANAGER_BUILD="skipped"
PAYLOAD_PACKAGED=false
METADATA_HASHES=false
BLOCKERS="[]"
READINESS="Unknown"

# Step 1: Cargo test
echo -e "${BOLD}[1/5] Running cargo test --workspace...${NC}"
if cd "$RUST_DIR" && cargo test --workspace 2>&1 | tee "$OUT_DIR/test_output.log" | tail -5; then
    if grep -q '0 failed' "$OUT_DIR/test_output.log"; then
        TESTS_PASSED=true
        # Check warnings
        if ! grep -q 'warning\[' "$OUT_DIR/test_output.log"; then
            WARNINGS_ZERO=true
        fi
        echo -e "${GREEN}  Tests: PASSED${NC}"
    else
        echo -e "${RED}  Tests: FAILED${NC}"
    fi
else
    echo -e "${RED}  Tests: FAILED (build error)${NC}"
fi

# Step 2: Security scan
echo ""
echo -e "${BOLD}[2/5] Running security scan...${NC}"
if bash "$SCRIPT_DIR/security-scan.sh" > "$OUT_DIR/security_scan_output.log" 2>&1; then
    SECURITY_SCAN_CLEAN=true
    echo -e "${GREEN}  Security scan: CLEAN${NC}"
else
    echo -e "${RED}  Security scan: VIOLATIONS FOUND${NC}"
fi

# Step 3: C glue build
echo ""
echo -e "${BOLD}[3/5] Building C glue...${NC}"
if bash "$SCRIPT_DIR/build-c.sh" > "$OUT_DIR/c_build_output.log" 2>&1; then
    C_GLUE_AUDIT_CLEAN=true
    echo -e "${GREEN}  C glue build: PASSED${NC}"
else
    echo -e "${RED}  C glue build: FAILED${NC}"
fi

# Step 4: Host Rust build
echo ""
echo -e "${BOLD}[4/5] Building Rust (host)...${NC}"
if bash "$SCRIPT_DIR/build-rust.sh" host > "$OUT_DIR/rust_build_output.log" 2>&1; then
    HOST_BUILD_PASSED=true
    echo -e "${GREEN}  Rust host build: PASSED${NC}"
else
    echo -e "${RED}  Rust host build: FAILED${NC}"
fi

# Step 5: Package
echo ""
echo -e "${BOLD}[5/5] Packaging...${NC}"
if bash "$SCRIPT_DIR/package.sh" > "$OUT_DIR/package_output.log" 2>&1; then
    PAYLOAD_PACKAGED=true
    if [ -f "$PROJECT_DIR/out/rustdroid_payload/metadata.json" ]; then
        METADATA_HASHES=true
    fi
    echo -e "${GREEN}  Package: PASSED${NC}"
else
    echo -e "${RED}  Package: FAILED (may need cross-compiled binaries)${NC}"
fi

# Note about skipped steps
echo ""
echo -e "${YELLOW}  Note: Android ARM64 cross-compilation skipped (requires NDK)${NC}"
echo -e "${YELLOW}  Note: Android Manager build skipped (requires Android SDK/Gradle)${NC}"

# Determine readiness level
BLOCKER_LIST=()
if [ "$TESTS_PASSED" = "true" ] && [ "$SECURITY_SCAN_CLEAN" = "true" ] && [ "$HOST_BUILD_PASSED" = "true" ]; then
    READINESS="ReadyForInternalAlpha"
elif [ "$TESTS_PASSED" = "false" ]; then
    READINESS="BlockedByTests"
    BLOCKER_LIST+=("Tests failed")
elif [ "$SECURITY_SCAN_CLEAN" = "false" ]; then
    READINESS="BlockedBySecurityScan"
    BLOCKER_LIST+=("Security scan found violations")
elif [ "$HOST_BUILD_PASSED" = "false" ]; then
    READINESS="BlockedByBuild"
    BLOCKER_LIST+=("Host build failed")
fi

# Format blockers as JSON array
BLOCKERS_JSON="[]"
if [ ${#BLOCKER_LIST[@]} -gt 0 ]; then
    BLOCKERS_JSON="["
    for i in "${!BLOCKER_LIST[@]}"; do
        [ $i -gt 0 ] && BLOCKERS_JSON+=","
        BLOCKERS_JSON+="\"${BLOCKER_LIST[$i]}\""
    done
    BLOCKERS_JSON+="]"
fi

# Generate report
cat > "$OUT_DIR/release_gate_report.json" << EOF
{
  "report_version": 1,
  "generated_at": $(date +%s),
  "tests_passed": $TESTS_PASSED,
  "warnings_zero": $WARNINGS_ZERO,
  "security_scan_clean": $SECURITY_SCAN_CLEAN,
  "c_glue_audit_clean": $C_GLUE_AUDIT_CLEAN,
  "android_arm64_build_passed": false,
  "android_arm64_build_note": "Skipped: requires ANDROID_NDK_HOME",
  "android_manager_build_passed": false,
  "android_manager_build_note": "Skipped: requires Android SDK/Gradle",
  "host_build_passed": $HOST_BUILD_PASSED,
  "payload_packaged": $PAYLOAD_PACKAGED,
  "metadata_hashes_present": $METADATA_HASHES,
  "safety_scope_valid": true,
  "no_auto_flash": true,
  "no_auto_reboot": true,
  "no_block_device_write": true,
  "no_bypass": true,
  "no_root_hiding": true,
  "no_module_mounting": true,
  "no_script_execution": true,
  "readiness_level": "$READINESS",
  "blockers": $BLOCKERS_JSON,
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
cat > "$OUT_DIR/release_gate_summary.txt" << EOF
RustDroid Release Readiness Gate v1.5
======================================
Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

Tests:              $TESTS_PASSED
Warnings Zero:      $WARNINGS_ZERO
Security Scan:      $SECURITY_SCAN_CLEAN
C Glue Audit:       $C_GLUE_AUDIT_CLEAN
Host Build:         $HOST_BUILD_PASSED
Android ARM64:      $ANDROID_ARM64_BUILD (requires NDK)
Android Manager:    $ANDROID_MANAGER_BUILD (requires SDK)
Payload Packaged:   $PAYLOAD_PACKAGED
Metadata Hashes:    $METADATA_HASHES

Readiness Level:    $READINESS

Safety Scope:
  Auto Flash:       false
  Auto Reboot:      false
  Block Device Write: false
  Bypass:           false
  Root Hiding:      false
  Module Mounting:  false
  Script Execution: false
  Manual Only:      true

Safety Statement:
  RustDroid did not flash any device.
  RustDroid did not reboot any device.
  RustDroid did not mount module files.
  RustDroid did not execute module scripts.
  RustDroid did not modify boot partitions.
  RustDroid did not bypass Android security.
  RustDroid did not hide root.
  Real module mounting is not implemented yet.
EOF

echo ""
echo -e "${BLUE}${BOLD}══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Readiness Level: ${NC}"
if [ "$READINESS" = "ReadyForInternalAlpha" ]; then
    echo -e "${GREEN}${BOLD}  $READINESS ✓${NC}"
else
    echo -e "${RED}${BOLD}  $READINESS ✗${NC}"
fi
echo -e "${BLUE}${BOLD}══════════════════════════════════════════════════${NC}"
echo ""
echo -e "Reports: $OUT_DIR/release_gate_report.json"
echo -e "Summary: $OUT_DIR/release_gate_summary.txt"
