#!/bin/bash
# RustDroid C Glue Layer Build Script
# Compiles low-level process and mount abstractions.

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$DIR/.."

cd "$ROOT_DIR/c"

echo "=== RustDroid: Compiling C Glue Layer ==="

# Define build directory
BUILD_DIR="build"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Generate build configuration
if [ -n "$ANDROID_NDK_HOME" ]; then
    echo "Using Android NDK located at: $ANDROID_NDK_HOME"
    # Cross-compile for Android target using NDK CMake toolchain
    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="arm64-v8a" \
        -DANDROID_PLATFORM=android-26
else
    echo "Warning: ANDROID_NDK_HOME not set. Compiling for Host system..."
    # Local host compilation for testing
    cmake ..
fi

# Build static library
make -j$(nproc 2>/dev/null || echo 4)

echo "=== C Glue compilation succeeded! Output: $ROOT_DIR/c/build/librustdroid_c.a ==="
