#!/bin/bash
# RustDroid ADB Userspace Validation Script (v0.8)
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

# Path safety verification helper
validate_path_safety() {
    local target_path="$1"
    if [[ "$target_path" == *..* ]]; then
        echo "Error: Path safety violation - Path traversal detected: $target_path" >&2
        exit 1
    fi
    if [[ "$target_path" != "/data/local/tmp/rustdroid-test"* ]]; then
        echo "Error: Path safety violation - Path $target_path must reside under /data/local/tmp/rustdroid-test/" >&2
        exit 1
    fi
}

check_adb_connected() {
    if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
        return 0
    fi

    if ! command -v adb &> /dev/null; then
        echo "Error: adb command not found. Please install Android SDK platform tools." >&2
        exit 1
    fi
    
    local DEV_COUNT
    DEV_COUNT=$(adb devices | tail -n +2 | grep -v -e '^$' | wc -l)
    if [ "$DEV_COUNT" -eq 0 ]; then
        echo "Error: No ADB device or emulator connected." >&2
        exit 1
    fi
}

print_device_info() {
    if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
        echo "Mock Device Model: RustDroid Emulator"
        echo "Mock Android Release: 13"
        echo "Mock ABI: aarch64"
        return 0
    fi
    local model
    local os_release
    local abi
    model=$(adb shell getprop ro.product.model)
    os_release=$(adb shell getprop ro.build.version.release)
    abi=$(adb shell getprop ro.product.cpu.abi)
    echo "Connected Device Info:"
    echo "  Model: $model"
    echo "  Android version: $os_release"
    echo "  ABI: $abi"
}

confirm_action() {
    local prompt="$1"
    if [ "$RUSTDROID_AUTO_CONFIRM" = "1" ]; then
        return 0
    fi
    read -p "$prompt [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Operation aborted by user."
        exit 1
    fi
}

ACTION="${1:-help}"

case "$ACTION" in
    "check-device")
        check_adb_connected
        print_device_info
        ;;
    "push-payload")
        check_adb_connected
        print_device_info
        confirm_action "Do you want to push RustDroid v0.8 test binaries to the device?"
        
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking payload push success."
            exit 0
        fi

        adb shell mkdir -p /data/local/tmp/rustdroid-test
        adb shell mkdir -p /data/local/tmp/rustdroid-test/bin
        adb shell mkdir -p /data/local/tmp/rustdroid-test/init
        adb shell mkdir -p /data/local/tmp/rustdroid-test/images

        echo "Pushing binaries to device..."
        adb push out/rustdroid_payload/bin/rustdroidd /data/local/tmp/rustdroid-test/bin/
        adb push out/rustdroid_payload/bin/su /data/local/tmp/rustdroid-test/bin/
        adb push out/rustdroid_payload/bin/rustdroid-core-cli /data/local/tmp/rustdroid-test/bin/
        adb push out/rustdroid_payload/init/init.rustdroid.rc /data/local/tmp/rustdroid-test/init/
        adb push out/rustdroid_payload/metadata.json /data/local/tmp/rustdroid-test/

        adb shell chmod 0755 /data/local/tmp/rustdroid-test/bin/rustdroidd
        adb shell chmod 0755 /data/local/tmp/rustdroid-test/bin/su
        adb shell chmod 0755 /data/local/tmp/rustdroid-test/bin/rustdroid-core-cli

        echo "Performing on-device binary self-checks..."
        adb shell /data/local/tmp/rustdroid-test/bin/rustdroidd --self-check
        adb shell /data/local/tmp/rustdroid-test/bin/su --self-check
        ;;
    "run-daemon-dry-run")
        check_adb_connected
        
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking daemon dry-run start."
            exit 0
        fi

        adb shell mkdir -p /data/local/tmp/rustdroid-test/data
        adb shell mkdir -p /data/local/tmp/rustdroid-test/logs

        echo "Launching daemon dry-run in background on device..."
        adb shell "nohup /data/local/tmp/rustdroid-test/bin/rustdroidd --foreground --dry-run --socket /data/local/tmp/rustdroid-test/rustdroidd.sock --data-dir /data/local/tmp/rustdroid-test/data > /data/local/tmp/rustdroid-test/logs/daemon_dry_run.log 2>&1 &"
        sleep 1
        echo "Daemon logs are active in device under: /data/local/tmp/rustdroid-test/logs/daemon_dry_run.log"
        ;;
    "run-su-dry-run")
        check_adb_connected
        
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking su dry-run execution."
            exit 0
        fi

        echo "Executing su client dry-run on device..."
        adb shell "/data/local/tmp/rustdroid-test/bin/su --dry-run --socket /data/local/tmp/rustdroid-test/rustdroidd.sock --command id --json"
        ;;
    "run-su-execute-test-if-rooted")
        check_adb_connected
        
        # Check for user confirmation
        local allowed=0
        for opt in "$@"; do
            if [ "$opt" = "--allow-rooted-execution-test" ]; then
                allowed=1
            fi
        done
        
        if [ $allowed -ne 1 ]; then
            echo "Error: Action requires explicit safety parameter '--allow-rooted-execution-test' to verify execution." >&2
            exit 1
        fi

        echo "Validating userspace execution only, not boot integration."
        
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking root command execution."
            echo "uid=0(root) gid=0(root) groups=0(root)"
            exit 0
        fi

        echo "Executing root validation command via device su..."
        if adb shell "su -c 'id'" &>/dev/null; then
            adb shell "su -c 'id'"
            adb shell "su -c 'echo rustdroid'"
        else
            echo "Warning: Device is not currently rooted via standard su. Skipping harmless command execution test."
        fi
        ;;
    "patch-image-file")
        check_adb_connected
        local input_img="$2"
        if [ -z "$input_img" ]; then
            echo "Usage: scripts/adb-validate.sh patch-image-file <local_boot_image>" >&2
            exit 1
        fi
        if [ ! -f "$input_img" ]; then
            echo "Error: Local image file not found: $input_img" >&2
            exit 1
        fi

        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking patch-image-file action."
            exit 0
        fi

        adb shell mkdir -p /data/local/tmp/rustdroid-test/images
        
        # Check path safety before uploading
        validate_path_safety "/data/local/tmp/rustdroid-test/images/input.img"
        
        echo "Pushing boot image to device..."
        adb push "$input_img" /data/local/tmp/rustdroid-test/images/input.img

        echo "Executing on-device offline patcher..."
        adb shell "/data/local/tmp/rustdroid-test/bin/rustdroid-core-cli patch /data/local/tmp/rustdroid-test/images/input.img --payload /data/local/tmp/rustdroid-test/ --output /data/local/tmp/rustdroid-test/images/rustdroid_patched.img"

        mkdir -p out/adb-validation
        echo "Pulling patched image back to host..."
        adb pull /data/local/tmp/rustdroid-test/images/rustdroid_patched.img out/adb-validation/rustdroid_patched.img
        ;;
    "verify-patched-image")
        echo "Performing verification of pulled patched image..."
        if [ ! -f "out/adb-validation/rustdroid_patched.img" ]; then
            echo "Error: Patched output image not found locally. Run patch-image-file first!" >&2
            exit 1
        fi
        
        # Run host CLI verify
        mkdir -p out/adb-validation
        ./rust/target/debug/rustdroid-core-cli verify out/adb-validation/rustdroid_patched.img > out/adb-validation/verification_report.json
        cat out/adb-validation/verification_report.json
        ;;
    "collect-logs")
        check_adb_connected
        mkdir -p out/adb-validation
        
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking logs collection."
            exit 0
        fi

        echo "Collecting device log files..."
        if adb shell "[ -f /data/local/tmp/rustdroid-test/logs/daemon_dry_run.log ]" &>/dev/null; then
            adb pull /data/local/tmp/rustdroid-test/logs/daemon_dry_run.log out/adb-validation/
        fi
        
        # Device info collection
        local model
        local os_release
        local abi
        model=$(adb shell getprop ro.product.model)
        os_release=$(adb shell getprop ro.build.version.release)
        abi=$(adb shell getprop ro.product.cpu.abi)
        cat <<EOF > out/adb-validation/device_info.txt
Model: $model
OS version: $os_release
ABI: $abi
SELinux: $(adb shell getenforce)
EOF
        echo "Log files stored under: out/adb-validation/"
        ;;
    "clean-test-files")
        check_adb_connected
        confirm_action "Are you sure you want to delete all RustDroid test files from the connected device?"
        
        if [ "$RUSTDROID_MOCK_ADB" = "1" ]; then
            echo "Mocking cleanup."
            exit 0
        fi

        echo "Cleaning device files..."
        adb shell rm -rf /data/local/tmp/rustdroid-test/
        echo "Cleanup completed successfully."
        ;;
    "test-path-safety")
        validate_path_safety "$2"
        echo "Path is safe"
        ;;
    *)
        echo "RustDroid Userspace Validation Tool (v0.8)"
        echo "Usage: scripts/adb-validate.sh <action> [options]"
        echo ""
        echo "Actions:"
        echo "  check-device"
        echo "  push-payload"
        echo "  run-daemon-dry-run"
        echo "  run-su-dry-run"
        echo "  run-su-execute-test-if-rooted [--allow-rooted-execution-test]"
        echo "  patch-image-file <local_boot_image>"
        echo "  verify-patched-image"
        echo "  collect-logs"
        echo "  clean-test-files"
        echo ""
        echo "Safety Warning:"
        echo "  This tool ONLY validates userspace binaries and offline boot image patching."
        echo "  It NEVER flashes partitions or modifications onto system/boot sectors."
        ;;
esac

echo ""
echo "RustDroid v0.8 validates Android userspace binaries and offline boot image patching only."
echo "It does not flash any device."
echo "It does not reboot any device."
echo "It does not modify boot partitions."
echo "It does not bypass Android security."
echo "It does not hide root."
echo "Real boot validation requires a separate unlocked test device or controlled emulator environment."
