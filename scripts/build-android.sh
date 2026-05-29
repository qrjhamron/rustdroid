#!/bin/bash
# RustDroid Android Manager Compile Script
# Triggers Gradle build command to produce the final management app package.

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$DIR/.."

cd "$ROOT_DIR/manager/android"

echo "=== RustDroid: Compiling Android Manager UI ==="

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo "Initializing Gradle wrapper..."
    gradle wrapper || echo "Please install gradle or place gradlew wrapper in manager/android"
fi

if [ -f "./gradlew" ]; then
    chmod +x gradlew
    # Run Assemble Release build
    ./gradlew assembleRelease
    echo "=== Android Manager APK compiled successfully! ==="
else
    echo "Error: gradlew wrapper not found. Please compile inside Android Studio."
    exit 1
fi
