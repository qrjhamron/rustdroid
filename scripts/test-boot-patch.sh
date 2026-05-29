#!/bin/bash
# RustDroid Boot Patch Audit Verification Script
# Generates a mock boot image header, runs it through the Rust patcher,
# and verifies the fail-safe and decompression audits compile.

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$DIR/.."

# 1. Ensure host binaries are compiled for testing
"$DIR/build-rust.sh" host

echo "=== RustDroid: Simulating Boot Patch Flow ==="

WORK_DIR="$ROOT_DIR/out/test_boot"
mkdir -p "$WORK_DIR"

# 2. Generate a mock Android Boot Image (v4 style)
# init_boot has kernel_size = 0, version = 4, ramdisk_size = 1024, page_size = 4096 (fixed)
echo "Generating mock init_boot image (v4 format)..."
python3 -c "
magic = b'ANDROID!'
kernel_size = (0).to_bytes(4, 'little')
ramdisk_size = (1024).to_bytes(4, 'little')
os_version = (34).to_bytes(4, 'little')
header_size = (4096).to_bytes(4, 'little')
reserved = b'\x00' * 16
header_version = (4).to_bytes(4, 'little')
padding = b'\x00' * (4096 - 44)

# Ramdisk starts at offset 4096.
# Inject uncompressed CPIO signature magic: '070701' followed by padding
ramdisk_magic = b'070701' + b'\x00' * 1018

with open('$WORK_DIR/mock_init_boot.img', 'wb') as f:
    f.write(magic + kernel_size + ramdisk_size + os_version + header_size + reserved + header_version + padding + ramdisk_magic)
"

# 3. Run rustdroid-boot verification tests and generate audit report JSON
echo "Running audit verification via Rust test suite..."
cargo test --manifest-path "$ROOT_DIR/rust/Cargo.toml" --package rustdroid-boot

# 4. Print structured JSON audit result
REPORT_FILE="$WORK_DIR/audit_report.json"
if [ -f "$REPORT_FILE" ]; then
    echo ""
    echo "=== RustDroid Boot Audit JSON Report ==="
    cat "$REPORT_FILE"
    echo ""
else
    echo "Error: Audit report JSON was not generated."
    exit 1
fi

# 5. Start live v0.3 host-side dry-run IPC demo
echo "=== RustDroid v0.3: Live Host-Side Dry-Run IPC Demo ==="
DAEMON_BIN="$ROOT_DIR/rust/target/debug/rustdroidd"
SU_BIN="$ROOT_DIR/rust/target/debug/su"
TEST_SOCKET="/tmp/rustdroidd.sock"
TEST_DATA="/tmp/rustdroid"

rm -rf "$TEST_DATA" "$TEST_SOCKET"

echo "1. Starting rustdroidd daemon in foreground (backgrounded in bash)..."
"$DAEMON_BIN" --foreground --dry-run --socket "$TEST_SOCKET" --data-dir "$TEST_DATA" > "$WORK_DIR/daemon_stdout.log" 2>&1 &
DAEMON_PID=$!

# Wait 1s for the socket server to bind cleanly
sleep 1

echo "2. Running su CLI client in dry-run mode targeting local socket..."
"$SU_BIN" --dry-run --socket "$TEST_SOCKET" --command "id"

echo "3. Terminating background daemon..."
kill "$DAEMON_PID" || true
wait "$DAEMON_PID" 2>/dev/null || true

echo "4. Reading daemon audit logs generated under $TEST_DATA/logs/su.log..."
if [ -f "$TEST_DATA/logs/su.log" ]; then
    cat "$TEST_DATA/logs/su.log"
else
    echo "Error: su audit log file not found."
    exit 1
fi
echo ""
echo "Note: This is dry-run IPC validation, NOT real Android privilege root execution."
echo "=== v0.3 IPC Demo Completed Successfully ==="

# 6. Clear disclaimer
echo ""
echo "=== Disclaimer ==="
echo "Note: This is the MVP v0.3 boot patching and IPC foundation."
echo "It verifies headers, plans safe patching, and tests versioned Socket IPC."
echo "Full real-device booting and patching is not yet supported in this stage."
echo "============================================="
echo "=== Boot Patch verification completed successfully! ==="
