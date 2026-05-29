#!/bin/bash
# RustDroid Rust Build Automation Script
# Sets up compiling for Android targets (aarch64, armv7, x86_64) using Android NDK.

set -e

# Path to the script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$DIR/.."

cd "$ROOT_DIR/rust"

# Default Target (Use host for quick audits/tests unless specified)
TARGET_RAW=${1:-"host"}
if [ "$TARGET_RAW" = "--target" ]; then
    TARGET_RAW=${2:-"host"}
fi

# Normalize target name
case "$TARGET_RAW" in
    "android-arm64" | "aarch64" | "aarch64-linux-android")
        TARGET="aarch64"
        ;;
    "android-arm" | "armv7" | "armv7-linux-androideabi")
        TARGET="armv7"
        ;;
    "android-x86_64" | "x86_64" | "x86_64-linux-android")
        TARGET="x86_64"
        ;;
    "host")
        TARGET="host"
        ;;
    *)
        echo "Error: Unknown target architecture '$TARGET_RAW'."
        echo "Supported architectures: android-arm64, aarch64, host"
        exit 1
        ;;
esac

echo "=== RustDroid: Compiling Rust Core [Target: $TARGET] ==="

if [ "$TARGET" = "host" ]; then
    # Compile for local host platform (ideal for testing modules/parsers)
    cargo build --workspace
    cargo test --workspace
    echo "=== Host build and test suite succeeded! ==="
else
    # Compile for specific Android target architecture
    # Default ANDROID_NDK_HOME if missing but available in /opt/android-ndk
    if [ -z "$ANDROID_NDK_HOME" ] && [ -d "/opt/android-ndk" ]; then
        export ANDROID_NDK_HOME="/opt/android-ndk"
    fi

    if [ -z "$ANDROID_NDK_HOME" ]; then
        echo "Error: ANDROID_NDK_HOME environment variable is not set."
        exit 1
    fi

    # Compile the low-level C glue layer first
    echo "Compiling low-level C glue layer..."
    "$ROOT_DIR/scripts/build-c.sh"

    case "$TARGET" in
        "aarch64")
            RUST_TARGET="aarch64-linux-android"
            OUT_ARCH_DIR="arm64"
            ;;
        "armv7")
            RUST_TARGET="armv7-linux-androideabi"
            OUT_ARCH_DIR="arm"
            ;;
        "x86_64")
            RUST_TARGET="x86_64-linux-android"
            OUT_ARCH_DIR="x86_64"
            ;;
    esac

    echo "Building Cargo Workspace for $RUST_TARGET using NDK..."
    # Ensure rustup target is installed
    rustup target add "$RUST_TARGET" || true

    # Export Linker and search path/libs variables for Cargo
    export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
    export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi26-clang"
    export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android26-clang"
    export RUSTFLAGS="-L native=$ROOT_DIR/c/build -l static=rustdroid_c"

    # Run cargo cross-compilation
    cargo build --target "$RUST_TARGET" --release

    # Output binaries into: target/rustdroid-android/$OUT_ARCH_DIR/
    OUT_DIR="target/rustdroid-android/$OUT_ARCH_DIR"
    mkdir -p "$OUT_DIR"
    
    cp "target/$RUST_TARGET/release/rustdroidd" "$OUT_DIR/"
    cp "target/$RUST_TARGET/release/su" "$OUT_DIR/"
    if [ -f "target/$RUST_TARGET/release/rustdroid-core-cli" ]; then
        cp "target/$RUST_TARGET/release/rustdroid-core-cli" "$OUT_DIR/"
    fi

    echo "=== Android ($TARGET) workspace compile completed! ==="
    echo "Output directory: rust/$OUT_DIR"
fi
